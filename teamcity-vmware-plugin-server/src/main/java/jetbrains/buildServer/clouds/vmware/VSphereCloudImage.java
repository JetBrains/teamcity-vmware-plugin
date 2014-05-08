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
public class VSphereCloudImage implements CloudImage {

  private final String myImageName;
  private final Map<String, VSphereCloudInstance> myInstances;
  private final VSphereImageType myImageType;
  private final VSphereImageStartType myStartType;
  @Nullable private final String mySnapshotName;
  private CloudErrorInfo myErrorInfo;

  public VSphereCloudImage(@NotNull final String imageName, @NotNull final VSphereImageType imageType, @Nullable String snapshotName,
                           @Nullable final InstanceStatus imageInstanceStatus, final VSphereImageStartType startType) {
    myImageName = imageName;
    myImageType = imageType;
    mySnapshotName = snapshotName;
    myStartType = startType;
    if (startType == VSphereImageStartType.START){
      final VSphereCloudInstance imageInstance = new VSphereCloudInstance(this, imageName);
      imageInstance.setStatus(imageInstanceStatus);
      myInstances = Collections.singletonMap(imageName, imageInstance);
    } else {
      myInstances = new HashMap<String, VSphereCloudInstance>();
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

  public VSphereImageType getImageType() {
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

  public void instanceStarted(@NotNull final VSphereCloudInstance instance){
    // special handling for imageInstance
    if (myStartType == VSphereImageStartType.START){
      myInstances.get(myImageName).setStatus(InstanceStatus.RUNNING);
    } else {
      myInstances.put(instance.getName(), instance);
    }
  }

  public void updateRunningInstances(final ProcessImageInstancesTask task){
    for (VSphereCloudInstance instance : myInstances.values()) {
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
      final VSphereCloudInstance instance = new VSphereCloudInstance(this, name);
      instance.setStatus(currentInstances.get(name));
      instanceStarted(instance);
    }
  }

  public void instanceStopped(@NotNull final String instanceName){
    // special handling for imageInstance
    if (myStartType == VSphereImageStartType.START){
      myInstances.get(myImageName).setStatus(InstanceStatus.STOPPED);
    } else {
      myInstances.remove(instanceName);
    }
  }

  public VSphereImageStartType getStartType() {
    return myStartType;
  }

  public static interface ProcessImageInstancesTask{
    void processInstance(@NotNull final VSphereCloudInstance instance);
  }
}
