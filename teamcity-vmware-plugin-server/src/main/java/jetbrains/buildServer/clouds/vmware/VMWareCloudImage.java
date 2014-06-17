package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.mo.Task;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.UpdateTaskStatusTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:58 PM
 */
public class VMWareCloudImage implements CloudImage {

  private static final Logger LOG = Logger.getInstance(VMWareCloudImage.class.getName());

  private final String myImageName;
  private final Map<String, VMWareCloudInstance> myInstances;
  private final VMWareImageType myImageType;
  private final VMWareImageStartType myStartType;
  @Nullable private final String mySnapshotName;
  private CloudErrorInfo myErrorInfo;
  private final String myResourcePool;
  private final String myFolder;
  private final VMWareApiConnector myApiConnector;
  @NotNull private final UpdateTaskStatusTask myStatusTask;

  public VMWareCloudImage(@NotNull final VMWareApiConnector apiConnector,
                          @NotNull final String imageName,
                          @NotNull final VMWareImageType imageType,
                          @NotNull final String folder,
                          @NotNull final String resourcePool,
                          @Nullable final String snapshotName,
                          @Nullable final InstanceStatus imageInstanceStatus,
                          @NotNull final UpdateTaskStatusTask statusTask,
                          final VMWareImageStartType startType) {

    myImageName = imageName;
    myImageType = imageType;
    myFolder = folder;
    myResourcePool = resourcePool;
    mySnapshotName = snapshotName;
    myApiConnector = apiConnector;
    this.myStatusTask = statusTask;
    if (myImageType == VMWareImageType.TEMPLATE && startType == VMWareImageStartType.START) {
      myStartType = VMWareImageStartType.CLONE;
    } else if (startType == VMWareImageStartType.LINKED_CLONE && snapshotName == null) {
      myStartType = VMWareImageStartType.CLONE;
    } else {
      myStartType = startType;
    }
    if (myStartType == VMWareImageStartType.START){
      final VMWareCloudInstance imageInstance = new VMWareCloudInstance(this, imageName);
      imageInstance.setStatus(imageInstanceStatus);
      myInstances = Collections.singletonMap(imageName, imageInstance);
    } else {
      myInstances = new HashMap<String, VMWareCloudInstance>();
    }
  }

  @NotNull
  public String getId() {
    return myImageName;
  }

  @NotNull
  public String getName() {
    return myImageName;
  }

  public VMWareImageType getImageType() {
    return myImageType;
  }

  @Nullable
  public String getSnapshotName() {
    return mySnapshotName;
  }

  @NotNull
  public Collection<? extends CloudInstance> getInstances() {
    return myInstances.values();
  }

  @Nullable
  public CloudInstance findInstanceById(@NotNull String instanceId) {
    return myInstances.get(instanceId);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public void setErrorInfo(final CloudErrorInfo errorInfo) {
    myErrorInfo = errorInfo;
  }

  public VMWareCloudInstance startInstance(@NotNull final CloudInstanceUserData cloudInstanceUserData){
    final String vmName = generateNewVmName();
    final VMWareCloudInstance instance = new VMWareCloudInstance(this, vmName);
    instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
    try {
      final Task task = myApiConnector.cloneVm(
        getName(),
        getResourcePool(),
        getFolder(),
        vmName,
        getSnapshotName(),
        getStartType() == VMWareImageStartType.LINKED_CLONE);
      myStatusTask.submit(task, new ImageStatusTaskWrapper(instance){
        @Override
        public void onSuccess() {
          cloneVmSuccessHandler(instance, cloudInstanceUserData);
        }
      });
      myInstances.put(vmName, instance);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return instance;
  }

  private void cloneVmSuccessHandler(@NotNull final VMWareCloudInstance instance, @NotNull final CloudInstanceUserData cloudInstanceUserData){
    instance.setStatus(InstanceStatus.STARTING);
    if (getStartType() != VMWareImageStartType.START || getImageType() != VMWareImageType.INSTANCE){
      LOG.info("Delete after stop:" + true);
      instance.setDeleteAfterStop(true);
    }
    try {
      final Task startInstanceTask = myApiConnector.startInstance(instance,
                                                                  instance.getName(),
                                                                  cloudInstanceUserData);

      myStatusTask.submit(startInstanceTask, new ImageStatusTaskWrapper(instance){
        @Override
        public void onSuccess() {
          reconfigureVmTask(instance, cloudInstanceUserData);
        }
      });
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void reconfigureVmTask(@NotNull final VMWareCloudInstance instance, @NotNull final CloudInstanceUserData cloudInstanceUserData){
    final Task task;
    try {
      task = myApiConnector.reconfigureInstance(instance, instance.getName(), cloudInstanceUserData);
      myStatusTask.submit(task, new ImageStatusTaskWrapper(instance){
        @Override
        public void onSuccess() {
          instance.setStatus(InstanceStatus.RUNNING);
          instanceStarted(instance);
          LOG.info("Instance started successfully");
        }
      });
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  public void instanceStarted(@NotNull final VMWareCloudInstance instance){
    // special handling for imageInstance
    if (myStartType == VMWareImageStartType.START){
      myInstances.get(myImageName).setStatus(InstanceStatus.RUNNING);
    } else {
      myInstances.put(instance.getName(), instance);
    }
  }

  public void updateRunningInstances(final ProcessImageInstancesTask task){
    for (VMWareCloudInstance instance : myInstances.values()) {
      task.processInstance(instance);
    }
  }

  public void populateRunningInstances(final Map<String, InstanceStatus> currentInstances){
    final List<String> instances2add = new ArrayList<String>();
    final List<String> instances2remove = new ArrayList<String>();

    for (String name : myInstances.keySet()) {
      if (currentInstances.get(name) == null && myInstances.get(name).getStatus()==InstanceStatus.RUNNING){
        instances2remove.add(name);
      }
    }
    for (String name : currentInstances.keySet()) {
      if (myInstances.get(name) == null){
        instances2add.add(name);
      }
    }

    for (String name: instances2remove) {
      instanceStopped(name);
    }
    for (String name : instances2add) {
      final VMWareCloudInstance instance = new VMWareCloudInstance(this, name);
      instance.setStatus(currentInstances.get(name));
      instanceStarted(instance);
    }
  }

  public void instanceStopped(@NotNull final String instanceName){
    // special handling for imageInstance
    if (myStartType == VMWareImageStartType.START){
      myInstances.get(myImageName).setStatus(InstanceStatus.STOPPED);
    } else {
      myInstances.remove(instanceName);
    }
  }

  public VMWareImageStartType getStartType() {
    return myStartType;
  }

  public String getResourcePool() {
    return myResourcePool;
  }

  public String getFolder() {
    return myFolder;
  }

  private String generateNewVmName(){
    SimpleDateFormat sdf = new SimpleDateFormat("MMdd-hhmmss");
    return String.format("%s-clone-%s", getId(), sdf.format(new Date()));
  }

  public static interface ProcessImageInstancesTask{
    void processInstance(@NotNull final VMWareCloudInstance instance);
  }

  private static class ImageStatusTaskWrapper extends UpdateTaskStatusTask.TaskCallbackAdapter{


    @NotNull private final VMWareCloudInstance myInstance;

    public ImageStatusTaskWrapper(@NotNull final VMWareCloudInstance instance) {
      myInstance = instance;
    }

    @Override
    public void onError(final LocalizedMethodFault fault) {
      myInstance.setStatus(InstanceStatus.ERROR);
      final CloudErrorInfo errorInfo;
      if (fault.getFault() != null && fault.getFault().getCause() != null) {
        errorInfo = new CloudErrorInfo(fault.getLocalizedMessage(), fault.getLocalizedMessage(), fault.getFault().getCause());
      } else {
        errorInfo = new CloudErrorInfo(fault.getLocalizedMessage(), fault.getLocalizedMessage());
      }
      myInstance.setErrorInfo(errorInfo);
      LOG.info("Unknown error occured: " + fault.getLocalizedMessage());
    }
  }
}
