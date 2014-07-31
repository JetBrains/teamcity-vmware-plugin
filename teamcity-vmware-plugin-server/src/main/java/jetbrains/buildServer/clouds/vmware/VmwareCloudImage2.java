package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.vmware.vim25.mo.Task;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.connector.TaskCallbackHandler;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.connector.VmwareTaskWrapper;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCloudErrorInfo2;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:58 PM
 */
public class VmwareCloudImage2 extends AbstractCloudImage implements VmInfo {

  private static final Logger LOG = Logger.getInstance(VmwareCloudImage2.class.getName());

  private final String myImageName;
  private final Map<String, VmwareCloudInstance2> myInstances;
  private final VMWareImageType myImageType;
  private final VMWareImageStartType myStartType;
  @Nullable private final String mySnapshotName;
  private final VmwareCloudErrorInfo2 myErrorInfo;
  private final String myResourcePool;
  private final String myFolder;
  private final VMWareApiConnector myApiConnector;
  private final int myMaxInstances;
  @NotNull private final CloudAsyncTaskExecutor myAsyncClient;

  public VmwareCloudImage2(@NotNull final VMWareApiConnector apiConnector,
                           @NotNull final String imageName,
                           @NotNull final VMWareImageType imageType,
                           @Nullable final String folder,
                           @Nullable final String resourcePool,
                           @Nullable final String snapshotName,
                           @Nullable final InstanceStatus imageInstanceStatus,
                           @NotNull final CloudAsyncTaskExecutor asyncClient,
                           @NotNull final VMWareImageStartType startType,
                           final int maxInstances) {
    myErrorInfo = new VmwareCloudErrorInfo2(this);
    myImageName = imageName;
    myImageType = imageType;
    myFolder = folder;
    myResourcePool = resourcePool;
    mySnapshotName = StringUtil.isEmpty(snapshotName) ? null : snapshotName;
    myApiConnector = apiConnector;
    myAsyncClient = asyncClient;
    myMaxInstances = maxInstances;
    if (myImageType == VMWareImageType.TEMPLATE && startType == VMWareImageStartType.START) {
      myStartType = VMWareImageStartType.CLONE;
    } else {
      myStartType = startType;
    }
    if (myStartType == VMWareImageStartType.START) {
      final VmwareCloudInstance2 imageInstance = new VmwareCloudInstance2(this, imageName, snapshotName);
      imageInstance.setStatus(imageInstanceStatus);
      myInstances = Collections.singletonMap(imageName, imageInstance);
    } else {
      myInstances = new HashMap<String, VmwareCloudInstance2>();
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
  public Collection<VmwareCloudInstance2> getInstances() {
    return Collections.unmodifiableCollection(myInstances.values());
  }

  @Nullable
  public VmwareCloudInstance2 findInstanceById(@NotNull String instanceId) {
    return myInstances.get(instanceId);
  }

  @Override
  public void createInstance(@NotNull final String instanceName) {

  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo.getErrorInfo();
  }

  private synchronized VmwareCloudInstance2 getOrCreateInstance() throws RemoteException, InterruptedException {
    if (myStartType.isUseOriginal()) {
      LOG.info("Won't create a new instance - using original");
      return myInstances.get(myImageName);
    }

    String latestSnapshotName = null;
    if (mySnapshotName != null) { ////means latest clone of snapshot that fits a mask
      latestSnapshotName = myApiConnector.getLatestSnapshot(myImageName, mySnapshotName);
      if (latestSnapshotName == null) {
        setErrorType(VMWareCloudErrorType.IMAGE_SNAPSHOT_NOT_EXISTS);
        throw new IllegalArgumentException("Unable to find snapshot: " + mySnapshotName);
      }
    }

    clearErrorType(VMWareCloudErrorType.IMAGE_SNAPSHOT_NOT_EXISTS);


    if (!myStartType.isDeleteAfterStop()) {
      // on demand clone
      final Map<String, VmwareInstance> vmClones = myApiConnector.getClones(myImageName);

      // start an existsing one.
      final VmwareInstance imageVm = myApiConnector.getInstanceDetails(myImageName);
      if (imageVm == null) {
        throw new IllegalArgumentException("Unable to get VM details: " + mySnapshotName);
      }
      for (VmwareInstance vmInstance : vmClones.values()) {
        if (vmInstance.getInstanceStatus() == InstanceStatus.STOPPED) {

          final String vmName = vmInstance.getName();
          final VmwareCloudInstance2 instance = myInstances.get(vmName);

          // checking if this instance is already starting.
          if (instance != null && instance.getStatus() != InstanceStatus.STOPPED)
            continue;

          if (latestSnapshotName == null) {
            if (imageVm.getChangeVersion() == null || !imageVm.getChangeVersion().equals(vmInstance.getChangeVersion())) {
              LOG.info(String.format("Change version for %s is outdated: '%s' vs '%s'", vmName, vmInstance.getChangeVersion(), imageVm.getChangeVersion()));
              deleteInstance(instance);
              continue;
            }
          } else {
            final String snapshotName = vmInstance.getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT);
            if (!latestSnapshotName.equals(snapshotName)) {
              LOG.info(String.format("VM %s Snapshot is not the latest one: '%s' vs '%s'", vmName, snapshotName, latestSnapshotName));
              deleteInstance(instance);
              continue;
            }
          }
          LOG.info("Will use existing VM with name " + vmName);
          return instance == null ? new VmwareCloudInstance2(this, vmName, latestSnapshotName) : instance;
        }
      }
    }
    // wasn't able to find an existing candidate, so will clone into a new VM
    final String newVmName = generateNewVmName();
    LOG.info("Will create a new VM with name " + newVmName);
    return new VmwareCloudInstance2(this, newVmName, latestSnapshotName);
  }

  public synchronized VmwareCloudInstance2 startInstance(@NotNull final CloudInstanceUserData cloudInstanceUserData) {
    try {
      final VmwareCloudInstance2 instance = getOrCreateInstance();
      boolean willClone = !myApiConnector.checkVirtualMachineExists(instance.getName());
      LOG.info("Will clone for " + instance.getName() + ": " + willClone);
      instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
      if (!myInstances.containsKey(instance.getName())) {
        addInstance(instance);
      }
      if (willClone) {
        myAsyncClient.executeAsync(
          new VmwareTaskWrapper(new Callable<Task>() {
          public Task call() throws Exception {
            return myApiConnector.cloneVm(instance, myResourcePool, myFolder);
          }}
          ),
          new ImageStatusTaskWrapper(instance) {
          @Override
          public void onSuccess() {
            cloneVmSuccessHandler(instance, cloudInstanceUserData);
          }

          @Override
          public void onError(final Throwable th) {
            super.onError(th);
            removeInstance(instance.getName());
          }
        });
      } else {
        cloneVmSuccessHandler(instance, cloudInstanceUserData);
      }
      return instance;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private synchronized void cloneVmSuccessHandler(@NotNull final VmwareCloudInstance2 instance, @NotNull final CloudInstanceUserData cloudInstanceUserData) {
    instance.setStatus(InstanceStatus.STARTING);
    myAsyncClient.executeAsync(new VmwareTaskWrapper(new Callable<Task>() {
      public Task call() throws Exception {
        return myApiConnector.startInstance(instance, instance.getName(), cloudInstanceUserData);
      }
    }), new ImageStatusTaskWrapper(instance) {
      @Override
      public void onSuccess() {
        reconfigureVmTask(instance, cloudInstanceUserData);
      }
    });
  }

  private synchronized void reconfigureVmTask(@NotNull final VmwareCloudInstance2 instance, @NotNull final CloudInstanceUserData cloudInstanceUserData) {
    final Task task;
    myAsyncClient.executeAsync(new VmwareTaskWrapper(new Callable<Task>() {
      public Task call() throws Exception {
        return myApiConnector.reconfigureInstance(instance, instance.getName(), cloudInstanceUserData);
      }
    }), new ImageStatusTaskWrapper(instance) {
      @Override
      public void onSuccess() {
        instance.setStatus(InstanceStatus.RUNNING);
        instance.clearAllErrors();
        LOG.info("Instance started successfully");
      }
    });
  }

  public void updateRunningInstances(final ProcessImageInstancesTask task) {
    for (VmwareCloudInstance2 instance : myInstances.values()) {
      task.processInstance(instance);
    }
  }

  public void populateInstances(final Map<String, VmwareInstance> currentInstances) {
    final List<String> instances2add = new ArrayList<String>();
    final List<String> instances2remove = new ArrayList<String>();

    for (String name : myInstances.keySet()) {
      if (currentInstances.get(name) == null && myInstances.get(name).isInPermanentStatus()) {
        instances2remove.add(name);
      }
    }

    for (String name : currentInstances.keySet()) {
      if (myInstances.get(name) == null) {
        instances2add.add(name);
      }
    }
    for (String name : instances2remove) {
      removeInstance(name);
    }
    for (String name : instances2add) {
      final VmwareInstance vmInstance = currentInstances.get(name);
      final String snapshotName = vmInstance.getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT);

      final VmwareCloudInstance2 instance = new VmwareCloudInstance2(this, name, StringUtil.isEmpty(snapshotName) ? null : snapshotName);
      instance.setStatus(vmInstance.getInstanceStatus());
      addInstance(instance);
    }
  }


  public void stopInstance(@NotNull final VmwareCloudInstance2 instance) throws RemoteException, InterruptedException {
    LOG.info("Stopping instance " + instance.getName());
    myApiConnector.stopInstance(instance);
    instance.setStatus(InstanceStatus.STOPPED);
    if (myStartType.isDeleteAfterStop()) { // we only destroy proper instances.
      deleteInstance(instance);
    }
  }

  private void deleteInstance(@NotNull final VmwareCloudInstance2 instance) throws RemoteException, InterruptedException {
    if (instance.getErrorInfo() == null) {
      LOG.info("Will delete instance " + instance.getName());
      final VmwareInstance vmInstance = myApiConnector.getInstanceDetails(instance.getName());
      if (vmInstance != null){
        myAsyncClient.executeAsync(vmInstance.deleteInstance(), new TaskCallbackHandler(){
          @Override
          public void onSuccess() {
            super.onSuccess();
            removeInstance(instance.getName());
          }
        });
      }
    } else {
      LOG.warn(String.format("Won't delete instance %s with error: %s (%s)",
                             instance.getName(), instance.getErrorInfo().getMessage(), instance.getErrorInfo().getDetailedMessage()));
    }
  }

  public boolean canStartNewInstance() throws RemoteException {
    if (getImageType() == VMWareImageType.INSTANCE && myStartType == VMWareImageStartType.START) {
      return myApiConnector.isInstanceStopped(getId());
    }
    final List<String> runningInstancesNames = new ArrayList<String>();
    for (Map.Entry<String, VmwareCloudInstance2> entry : myInstances.entrySet()) {
      if (entry.getValue().getStatus() != InstanceStatus.STOPPED)
        runningInstancesNames.add(entry.getKey());
    }
    final boolean canStartMore = myErrorInfo.getErrorInfo() == null && (myMaxInstances == 0 || runningInstancesNames.size() < myMaxInstances);
    LOG.info(String.format("Running count: %d %s, can start more: %s",
                           runningInstancesNames.size(), Arrays.toString(runningInstancesNames.toArray()), String.valueOf(canStartMore)));
    return canStartMore;
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

  private String generateNewVmName() {
    Random r = new Random();
    SimpleDateFormat sdf = new SimpleDateFormat("MMdd-hhmmss");
    return String.format("%s-clone-%s%s", getId(), sdf.format(new Date()), Integer.toHexString(r.nextInt(256)));
  }

  public void addInstance(@NotNull final VmwareCloudInstance2 instance){
    LOG.info(String.format("Image %s, put instance %s", myImageName, instance.getName()));
    myInstances.put(instance.getName(), instance);
  }

  public void removeInstance(@NotNull final String name){
    LOG.info(String.format("Image %s, remove instance %s", myImageName, name));
    myInstances.remove(name);
  }

  public void setErrorType(@NotNull final VMWareCloudErrorType errorType) {
    setErrorType(errorType, null);
  }

  public void setErrorType(@NotNull final VMWareCloudErrorType errorType, @Nullable final String errorMessage) {
    myErrorInfo.setErrorType(errorType, errorMessage);
  }

  public void clearErrorType(@NotNull final VMWareCloudErrorType errorType) {
    myErrorInfo.clearErrorType(errorType);
  }

  public static interface ProcessImageInstancesTask{
    void processInstance(@NotNull final VmwareCloudInstance2 instance);
  }

  private static class ImageStatusTaskWrapper extends TaskCallbackHandler {

    @NotNull protected final VmwareCloudInstance2 myInstance;

    public ImageStatusTaskWrapper(@NotNull final VmwareCloudInstance2 instance) {
      myInstance = instance;
    }

    @Override
    public void onError(final Throwable th) {
      if (th != null) {
        myInstance.setErrorType(VMWareCloudErrorType.CUSTOM, th.getMessage());
        LOG.info("An error occured: " + th.getLocalizedMessage() + " during processing " + myInstance.getName());
      } else {
        myInstance.setErrorType(VMWareCloudErrorType.CUSTOM, "Unknown error during processing instance " + myInstance.getName());
        LOG.info("Unknown error during processing " + myInstance.getName());
      }
    }
  }
}
