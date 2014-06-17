package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.tasks.TaskStatusUpdater;
import jetbrains.buildServer.clouds.vmware.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
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
  private ScheduledExecutorService myScheduledExecutor;
  private TaskStatusUpdater myStatusTask;
  private CloudErrorInfo myErrorInfo;

  public VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters) throws MalformedURLException, RemoteException {
    this(cloudClientParameters, new VMWareApiConnectorImpl(
      new URL(cloudClientParameters.getParameter(VMWareWebConstants.SERVER_URL)),
      cloudClientParameters.getParameter(VMWareWebConstants.USERNAME),
      cloudClientParameters.getParameter(VMWareWebConstants.SECURE_PASSWORD)));

    //neverov tfs;;vm;Koshkin;START;X;:TC vlad-ubuntu;;vm;Koshkin;START;X;:Lubuntu-sergey;Before installing mc;vm;Koshkin;START;X;:
  }

  VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters, @NotNull final VMWareApiConnector apiConnector) throws MalformedURLException, RemoteException {
    myApiConnector = apiConnector;
    myStatusTask = new TaskStatusUpdater();

    final String imagesListData = cloudClientParameters.getParameter(VMWareWebConstants.IMAGES_DATA);
    if (imagesListData == null) {
      return;
    }
    final List<String> errorList = new ArrayList<String>();
    try {
      final String[] imageDataArray = imagesListData.split(";X;:");
      for (String imageDataStr : imageDataArray) {
        final String[] split = imageDataStr.split(";");
        String vmName = split[0];
        String snapshotName = split[1];
        String cloneFolder = split[2];
        String resourcePool = split[3];
        String behaviourStr = split[4];
        String maxInstancesStr = split[5];

        int maxInstances = 0;
        try {
          maxInstances = Integer.parseInt(maxInstancesStr);
        } catch (Exception ex) {
        }

        if (!myApiConnector.checkCloneFolderExists(cloneFolder)) {
          errorList.add(VMWareCloudErrorInfoFactory.noSuchFolder(cloneFolder).getMessage());
          break;
        }

        if (!myApiConnector.checkResourcePoolExists(resourcePool)) {
          errorList.add(VMWareCloudErrorInfoFactory.noSuchResourcePool(resourcePool).getMessage());
          break;
        }

        final VirtualMachine vm = myApiConnector.getInstanceDetails(vmName);

        if (vm == null) {
          errorList.add(VMWareCloudErrorInfoFactory.noSuchVM(vmName).getMessage());
          break;
        }

        final VMWareImageStartType startType = VMWareImageStartType.valueOf(behaviourStr);
        final VMWareImageType imageType = vm.getConfig().isTemplate() ? VMWareImageType.TEMPLATE : VMWareImageType.INSTANCE;

        final VMWareCloudImage cloudImage = new VMWareCloudImage(
          myApiConnector, vmName, imageType, cloneFolder, resourcePool, snapshotName,
          myApiConnector.getInstanceStatus(vm), myStatusTask, startType, maxInstances);
        myImageMap.put(vmName, cloudImage);
      }

    } finally {

      if (errorList.size() == 0) {
        myScheduledExecutor = Executors.newScheduledThreadPool(2, new NamedThreadFactory("VSphere"));
        myScheduledExecutor.scheduleWithFixedDelay(new UpdateInstancesTask(myApiConnector, this), 20, 10, TimeUnit.SECONDS);
        myScheduledExecutor.scheduleWithFixedDelay(myStatusTask, 20, 5, TimeUnit.SECONDS);
      } else {
        myErrorInfo = new CloudErrorInfo(Arrays.toString(errorList.toArray()));
      }
    }
  }


  @NotNull
  public CloudInstance startNewInstance(@NotNull final CloudImage cloudImage, @NotNull final CloudInstanceUserData cloudInstanceUserData) throws QuotaException {
    LOG.info("Attempting to start new Instance for cloud " + cloudImage.getName());
    final VMWareCloudImage vCloudImage = (VMWareCloudImage)cloudImage;
    return vCloudImage.startInstance(cloudInstanceUserData);
  }

  public void restartInstance(@NotNull CloudInstance cloudInstance) {
    try {
      myApiConnector.restartInstance((VMWareCloudInstance)cloudInstance);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  public void terminateInstance(@NotNull CloudInstance cloudInstance) {
    ((VMWareCloudImage)cloudInstance.getImage()).stopInstance((VMWareCloudInstance)cloudInstance);
  }

  public void dispose() {
    if (myScheduledExecutor != null) {
      myScheduledExecutor.shutdown();
    }
  }

  public boolean isInitialized() {
    return myScheduledExecutor != null && myImageMap.size() > 0;
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
    try {
      VMWareCloudImage vCloudImage = (VMWareCloudImage)cloudImage;
      return vCloudImage.canStartNewInstance();
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
