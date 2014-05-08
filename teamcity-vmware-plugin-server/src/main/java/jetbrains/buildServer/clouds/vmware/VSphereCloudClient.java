package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.util.text.StringUtil;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VSphereApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:23 PM
 */
public class VSphereCloudClient implements CloudClientEx {

  private VSphereApiConnector myApiConnector;

  @NotNull
  private final CloudClientParameters myCloudClientParameters;
  private final Map<String, VSphereCloudImage> myImageMap = new HashMap<String, VSphereCloudImage>();
  private ScheduledExecutorService myExecutor;
  private CloudErrorInfo myErrorInfo;
  private VSphereImageStartType myStartType;
  private final String myCloneFolderName;
  private final String myResourcePoolName;



  public VSphereCloudClient(@NotNull CloudClientParameters cloudClientParameters) throws MalformedURLException, RemoteException {
    myCloudClientParameters = cloudClientParameters;
    myApiConnector = new VSphereApiConnector(
      new URL(cloudClientParameters.getParameter("serverUrl")),
      cloudClientParameters.getParameter("username"),
      cloudClientParameters.getParameter("password"));

    final String cloneFolder = cloudClientParameters.getParameter("cloneFolder");
    if (cloneFolder != null) {
      if (myApiConnector.checkCloneFolder(cloneFolder)) {
        myCloneFolderName = cloneFolder;
      } else {
        myErrorInfo = new CloudErrorInfo(String.format("Unable to find the clone folder: %s", cloneFolder));
        myCloneFolderName = null;
      }
    } else {
      myCloneFolderName = null;
    }

    final String resourcePool = cloudClientParameters.getParameter("resourcePool");
    if (resourcePool != null){
      if (myApiConnector.checkResourcePool(resourcePool)){
        myResourcePoolName = resourcePool;
      } else {
        myErrorInfo = new CloudErrorInfo(String.format("Unable to find the resource pool: %s", resourcePool));
        myResourcePoolName = null;
      }
    } else {
      myResourcePoolName = null;
    }

    final String images = cloudClientParameters.getParameter("images");
    if (StringUtil.isEmpty(images))
      return;
    final Map<String, VirtualMachine> instances = myApiConnector.getInstances();
    final String[] split = images.split("\n");
    List<String> missingInstances = new ArrayList<String>();
    myStartType = VSphereImageStartType.START;
    try {
      myStartType = VSphereImageStartType.valueOf(cloudClientParameters.getParameter("cloneBehaviour"));
    } catch (Exception e){
      e.printStackTrace();
    }

    for (String sp : split) {
      String imageName;
      String snapshotName = null;
      if (sp.contains("@")){
        imageName = sp.substring(0, sp.indexOf("@"));
        snapshotName = sp.substring(sp.indexOf("@")+1);
      } else {
        imageName = sp;
      }
      if (!instances.containsKey(imageName)){
        missingInstances.add(imageName);
      } else {
        final VirtualMachine virtualMachine = instances.get(imageName);
        final VSphereImageType imageType = virtualMachine.getConfig().isTemplate() ? VSphereImageType.TEMPLATE : VSphereImageType.INSTANCE;
        myImageMap.put(imageName, new VSphereCloudImage(imageName, imageType, snapshotName, myApiConnector.getInstanceStatus(virtualMachine), myStartType));
      }
    }
    if (missingInstances.size() == 0){
      myExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("VSphere"));
      myExecutor.scheduleWithFixedDelay(new UpdateInstancesTask(myApiConnector, this), 20, 10, TimeUnit.SECONDS);
    } else {
      myErrorInfo = new CloudErrorInfo(String.format("Unable to find the following images: %s", Arrays.toString(missingInstances.toArray())));
    }



  }

  @NotNull
  public CloudInstance startNewInstance(@NotNull CloudImage cloudImage, @NotNull CloudInstanceUserData cloudInstanceUserData) throws QuotaException {
    try {
      final String instanceName = myApiConnector.cloneVmIfNecessary((VSphereCloudImage)cloudImage, myStartType, myCloneFolderName, myResourcePoolName);
      final VSphereCloudInstance instance = new VSphereCloudInstance((VSphereCloudImage)cloudImage, instanceName);
      if (myStartType != VSphereImageStartType.START || ((VSphereCloudImage)cloudImage).getImageType() != VSphereImageType.INSTANCE){
        instance.setDeleteAfterStop(true);
      }
      myApiConnector.startInstance(instance,
                                   instanceName,
                                   cloudInstanceUserData.getAuthToken(),
                                   cloudInstanceUserData.getServerAddress());
      ((VSphereCloudImage)cloudImage).instanceStarted(instance);
      return instance;
    } catch (Exception e) {
      throw new RuntimeException("Unable to start new instance: " + e.toString(), e);
    }
  }

  public void restartInstance(@NotNull CloudInstance cloudInstance) {
    try {
      myApiConnector.restartInstance((VSphereCloudInstance)cloudInstance);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  public void terminateInstance(@NotNull CloudInstance cloudInstance) {
    myApiConnector.stopInstance((VSphereCloudInstance)cloudInstance);
    ((VSphereCloudInstance)cloudInstance).setStatus(InstanceStatus.STOPPED);
    ((VSphereCloudImage)cloudInstance.getImage()).instanceStopped(cloudInstance.getName());
  }

  public void dispose() {
    if (myExecutor != null)
      myExecutor.shutdown();
  }

  public boolean isInitialized() {
    return myExecutor != null && myImageMap.size() > 0;
  }

  @Nullable
  public CloudImage findImageById(@NotNull String s) throws CloudException {
    return myImageMap.get(s);
  }

  @Nullable
  public CloudInstance findInstanceByAgent(@NotNull AgentDescription agentDescription) {
    final String imageName = agentDescription.getAvailableParameters().get(VMWarePropertiesNames.IMAGE_NAME);
    if (imageName != null) {
      final CloudImage image = findImageById(imageName);
      if (image != null) {
        return image.findInstanceById(agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME));
      }
    }
    return null;
  }

  @NotNull
  public Collection<? extends CloudImage> getImages() throws CloudException {
    return myImageMap.values();
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public boolean canStartNewInstance(@NotNull CloudImage cloudImage) {
    VSphereCloudImage vCloudImage = (VSphereCloudImage)cloudImage;
    try {
      if (vCloudImage.getImageType() == VSphereImageType.INSTANCE) {
        if (vCloudImage.getSnapshotName() != null) {
          if (!myApiConnector.ensureSnapshotExists(vCloudImage.getId(), vCloudImage.getSnapshotName())) {
            myErrorInfo = new CloudErrorInfo("Unable to find snapshot: " + vCloudImage.getSnapshotName());
          }
        } else if (myStartType == VSphereImageStartType.START) {
          return myApiConnector.isInstanceStopped(vCloudImage.getId());
        }
      }
    } catch (RemoteException e) {
      if (myErrorInfo == null) {
        myErrorInfo = new CloudErrorInfo("error reading VM information", e.toString(), e);
      }
    }
    return myErrorInfo == null;
  }

  @Nullable
  public String generateAgentName(@NotNull AgentDescription agentDescription) {
    return agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME);
  }
}
