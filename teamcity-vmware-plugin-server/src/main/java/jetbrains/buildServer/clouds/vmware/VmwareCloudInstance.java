package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorType;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.INSTANCE_NAME;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:57 PM
 */
public class VmwareCloudInstance extends AbstractCloudInstance implements VmInfo {

  private static final Logger LOG = Logger.getInstance(VmwareCloudInstance.class.getName());

  private final String myInstanceName;
  private final VmwareCloudImage myImage;
  private InstanceStatus myStatus = InstanceStatus.UNKNOWN;
  private final VmwareCloudErrorInfo myErrorInfo;
  private Date myStartDate;
  private String myIpAddress;
  private String mySnapshotName;

  public VmwareCloudInstance(@NotNull final VmwareCloudImage image, @NotNull final String instanceName, @Nullable final String snapshotName) {
    super(image);
    myImage = image;
    myInstanceName = instanceName;
    mySnapshotName = snapshotName;
    myStartDate = new Date();
    myErrorInfo = new VmwareCloudErrorInfo(this);
  }

  @NotNull
  public String getInstanceId() {
    return myInstanceName;
  }

  @NotNull
  public String getName() {
    return myInstanceName;
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @NotNull
  public VmwareCloudImage getImage() {
    return myImage;
  }

  @NotNull
  public Date getStartedTime() {
    return myStartDate;
  }

  @Nullable
  public String getNetworkIdentity() {
    return myIpAddress;
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  @Nullable
  public String getSnapshotName() {
    return mySnapshotName;
  }

  public void setStatus(final InstanceStatus status) {
    if (myStatus == status)
      return;
    LOG.warn(String.format("Changing %s(%s) status from %s to %s ", getName(), toString(), myStatus, status));
    myStatus = status;
  }

  public void updateVMInfo(@NotNull final VmwareInstance vm) {
    if (!vm.isInitialized()){
      if (myStatus != InstanceStatus.SCHEDULED_TO_START) {
        setStatus(InstanceStatus.UNKNOWN); // still cloning
      }
      return;
    }
    if (vm.isPoweredOn()) {
      myStartDate = vm.getStartDate();
      if (myStatus == InstanceStatus.STOPPED) {
        setStatus(InstanceStatus.RUNNING);
      }
      myIpAddress = vm.getIpAddress();
    } else {
      if (myStatus != InstanceStatus.SCHEDULED_TO_START && myStatus != InstanceStatus.STOPPED) {
        setStatus(InstanceStatus.STOPPED);
      }
    }
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

  public void clearAllErrors(){
    myErrorInfo.clearAllErrors();
  }

  @Nullable
  public CloudErrorInfo getErrorInfo(){
    return myErrorInfo.getErrorInfo();
  }

  public boolean containsAgent(@NotNull AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return getInstanceId().equals(configParams.get(INSTANCE_NAME));
  }

  public boolean isInPermanentStatus(){
    return myStatus == InstanceStatus.STOPPED || myStatus == InstanceStatus.RUNNING;
  }
}
