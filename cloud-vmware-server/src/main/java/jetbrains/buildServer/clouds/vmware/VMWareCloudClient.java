

package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jetbrains.buildServer.clouds.CanStartNewInstanceResult;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:23 PM
 */
public class VMWareCloudClient extends AbstractCloudClient<VmwareCloudInstance, VmwareCloudImage, VmwareCloudImageDetails>{

  private static final Logger LOG = Logger.getInstance(VMWareCloudClient.class.getName());
  @NotNull private final VmwareUpdateTaskManager myTaskManager;
  @NotNull private final File myIdxStorage;
  @NotNull private final CloudProfile myProfile;
  private final Integer myProfileInstancesLimit;
  private final List<DisposeHandler> myDisposeHandlers = new ArrayList<>();
  private volatile boolean myInitialized = false;


  public VMWareCloudClient(@NotNull final CloudProfile profile,
                           @NotNull final VMWareApiConnector apiConnector,
                           @NotNull final VmwareUpdateTaskManager taskManager,
                           @NotNull final File idxStorage) {
    super(profile.getParameters(), apiConnector);
    myTaskManager = taskManager;
    myIdxStorage = idxStorage;
    myProfile = profile;
    final String limitStr = profile.getProfileProperties().get(VMWareWebConstants.PROFILE_INSTANCE_LIMIT);
    myProfileInstancesLimit = StringUtil.isEmpty(limitStr) ? null : Integer.valueOf(limitStr);
  }

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  @Nullable
  public VmwareCloudInstance findInstanceByAgent(@NotNull AgentDescription agentDescription) {
    final String imageName = agentDescription.getAvailableParameterValue(VMWarePropertiesNames.IMAGE_NAME);
    String instanceName = agentDescription.getAvailableParameterValue(VMWarePropertiesNames.INSTANCE_NAME);
    if (imageName != null && instanceName != null) {
      final VmwareCloudImage cloudImage = findImageById(imageName);
      if (cloudImage != null) {
        return cloudImage.findInstanceById(instanceName);
      }
    }
    return null;
  }

  @Override
  protected VmwareCloudImage checkAndCreateImage(@NotNull final VmwareCloudImageDetails imageDetails) {
    final VMWareApiConnector apiConnector = (VMWareApiConnector)myApiConnector;
    return new VmwareCloudImage(apiConnector, imageDetails, myAsyncTaskExecutor, myIdxStorage, myProfile);
  }

  @NotNull
  @Override
  public CanStartNewInstanceResult canStartNewInstanceWithDetails(@NotNull final CloudImage baseImage) {
    if (myProfileInstancesLimit != null) {
      final AtomicLong count = new AtomicLong(0);
      myImageMap.forEach((s, img) -> {
        count.addAndGet(img.getInstances().stream().filter(i -> i.getStatus().isCanTerminate()).count());
      });
      if (count.get() >= myProfileInstancesLimit){
        return CanStartNewInstanceResult.no("Profile instance limit exceeded.");
      }
    }
    return super.canStartNewInstanceWithDetails(baseImage);
  }

  @NotNull
  @Override
  protected UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> createUpdateInstancesTask() {
    return myTaskManager.createUpdateTask((VMWareApiConnector)myApiConnector, this);
  }

  @Nullable
  public String generateAgentName(@NotNull AgentDescription agentDescription) {
    return agentDescription.getAvailableParameterValue(VMWarePropertiesNames.INSTANCE_NAME);
  }

  public void addDisposeHandler(@NotNull DisposeHandler disposeHandler){
    myDisposeHandlers.add(disposeHandler);
  }

  @Override
  public void dispose() {
    myDisposeHandlers.forEach(d->{
      try {
        d.clientDisposing(this);
      } catch (Exception e) {
        LOG.warn("An exception occurred while disposing client", e);
      }
    });
    myImageMap.forEach((k, v)->{
      v.storeIdx();
    });
    super.dispose();
  }

  public void setInitializedIfNecessary() {
    if (!myInitialized)
      myInitialized = true;
  }

  public static interface DisposeHandler{
    public void clientDisposing(@NotNull final  VMWareCloudClient client);
  }
}