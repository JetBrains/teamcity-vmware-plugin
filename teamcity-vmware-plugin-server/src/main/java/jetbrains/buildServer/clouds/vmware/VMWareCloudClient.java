package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
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
public class VMWareCloudClient implements CloudClientEx {

  private static final Logger LOG = Logger.getInstance(VMWareCloudClient.class.getName());

  private final VMWareApiConnector myApiConnector;

  private final Map<String, VMWareCloudImage> myImageMap = new HashMap<String, VMWareCloudImage>();
  private ScheduledExecutorService myExecutor;
  private CloudErrorInfo myErrorInfo;
  private VMWareImageStartType myStartType;
  private final String myCloneFolderName;
  private final String myResourcePoolName;

  public VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters) throws MalformedURLException, RemoteException {
    this(cloudClientParameters, new VMWareApiConnectorImpl(
      new URL(cloudClientParameters.getParameter("serverUrl")),
      cloudClientParameters.getParameter("username"),
      cloudClientParameters.getParameter("password")));
  }

  VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters, @NotNull final VMWareApiConnector apiConnector) throws MalformedURLException, RemoteException {
    myApiConnector = apiConnector;
    final String cloneFolder = cloudClientParameters.getParameter("cloneFolder");
    if (cloneFolder != null) {
      if (myApiConnector.checkCloneFolder(cloneFolder)) {
        myCloneFolderName = cloneFolder;
      } else {
        myErrorInfo = VMWareCloudErrorInfoFactory.noSuchFolder(cloneFolder);
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
        myErrorInfo = VMWareCloudErrorInfoFactory.noSuchResourcePool(resourcePool);
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
    myStartType = VMWareImageStartType.START;
    try {
      myStartType = VMWareImageStartType.valueOf(cloudClientParameters.getParameter("cloneBehaviour"));
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
        final VMWareImageType imageType = virtualMachine.getConfig().isTemplate() ? VMWareImageType.TEMPLATE : VMWareImageType.INSTANCE;
        myImageMap.put(imageName, new VMWareCloudImage(imageName, imageType, snapshotName, myApiConnector.getInstanceStatus(virtualMachine), myStartType));
        if (snapshotName != null  && !myApiConnector.ensureSnapshotExists(imageName, snapshotName)){
          myErrorInfo = VMWareCloudErrorInfoFactory.noSuchSnapshot(snapshotName, imageName);
        }
      }
    }
    if (missingInstances.size() == 0){
      myExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("VSphere"));
      myExecutor.scheduleWithFixedDelay(new UpdateInstancesTask(myApiConnector, this), 20, 10, TimeUnit.SECONDS);
    } else {
      myErrorInfo = VMWareCloudErrorInfoFactory.noSuchImages(missingInstances);
    }
  }



    @NotNull
  public CloudInstance startNewInstance(@NotNull CloudImage cloudImage, @NotNull CloudInstanceUserData cloudInstanceUserData) throws QuotaException {
      LOG.info("Attempting to start new Instance for cloud " + cloudImage.getName());
    try {
      final String instanceName = myApiConnector.cloneVmIfNecessary((VMWareCloudImage)cloudImage, myStartType, myCloneFolderName, myResourcePoolName);
      LOG.info("Instance name is " + instanceName);
      final VMWareCloudInstance instance = new VMWareCloudInstance((VMWareCloudImage)cloudImage, instanceName);
      if (myStartType != VMWareImageStartType.START || ((VMWareCloudImage)cloudImage).getImageType() != VMWareImageType.INSTANCE){
        LOG.info("Delete after stop:" + true);
        instance.setDeleteAfterStop(true);
      }
      myApiConnector.startInstance(instance,
                                   instanceName,
                                   cloudInstanceUserData);
      ((VMWareCloudImage)cloudImage).instanceStarted(instance);
      LOG.info("Instance started successfully");
      return instance;
    } catch (Exception e) {
      throw new RuntimeException("Unable to start new instance: " + e.toString(), e);
    }
  }

  public void restartInstance(@NotNull CloudInstance cloudInstance) {
    try {
      myApiConnector.restartInstance((VMWareCloudInstance)cloudInstance);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  public void terminateInstance(@NotNull CloudInstance cloudInstance) {
    LOG.info("Terminating instance " + cloudInstance.getName());
    myApiConnector.stopInstance((VMWareCloudInstance)cloudInstance);
    ((VMWareCloudInstance)cloudInstance).setStatus(InstanceStatus.STOPPED);
    ((VMWareCloudImage)cloudInstance.getImage()).instanceStopped(cloudInstance.getName());
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
    VMWareCloudImage vCloudImage = (VMWareCloudImage)cloudImage;
    try {
      if (vCloudImage.getImageType() == VMWareImageType.INSTANCE) {
        if (vCloudImage.getSnapshotName() != null) {
          if (!myApiConnector.ensureSnapshotExists(vCloudImage.getId(), vCloudImage.getSnapshotName())) {
            myErrorInfo = new CloudErrorInfo("Unable to find snapshot: " + vCloudImage.getSnapshotName());
          }
        } else if (myStartType == VMWareImageStartType.START) {
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
