package jetbrains.buildServer.clouds.vmware;

import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:58 PM
 */
public class VMWareCloudImage implements CloudImage {

  private final String myImageName;
  private final Map<String, VMWareCloudInstance> myInstances;
  private final VMWareImageType myImageType;
  private final VMWareImageStartType myStartType;
  @Nullable private final String mySnapshotName;
  private CloudErrorInfo myErrorInfo;

  public VMWareCloudImage(@NotNull final String imageName, @NotNull final VMWareImageType imageType, @Nullable String snapshotName,
                          @Nullable final InstanceStatus imageInstanceStatus, final VMWareImageStartType startType) {
    myImageName = imageName;
    myImageType = imageType;
    mySnapshotName = snapshotName;
    if (myImageType == VMWareImageType.TEMPLATE && startType == VMWareImageStartType.START) {
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
      if (currentInstances.get(name) == null){
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

  public static interface ProcessImageInstancesTask{
    void processInstance(@NotNull final VMWareCloudInstance instance);
  }
}
