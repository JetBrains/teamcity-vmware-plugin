package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorInfo;
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
public class VMWareCloudInstance implements CloudInstance, VmInfo {

  private static final Logger LOG = Logger.getInstance(VMWareCloudInstance.class.getName());

  private final String myInstanceName;
  private final VMWareCloudImage myImage;
  private InstanceStatus myStatus = InstanceStatus.UNKNOWN;
  private VirtualMachine myVM = null;
  private final VMWareCloudErrorInfo myErrorInfo;
  private Date myStartDate;
  private String myIpAddress;
  private String mySnapshotName;

  public VMWareCloudInstance(@NotNull final VMWareCloudImage image, @NotNull final String instanceName, @Nullable final String snapshotName) {
    myImage = image;
    myInstanceName = instanceName;
    mySnapshotName = snapshotName;
    myStartDate = new Date();
    myErrorInfo = new VMWareCloudErrorInfo(this);
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
  public VMWareCloudImage getImage() {
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
    LOG.info(String.format("Changing %s status from %s to %s ", getName(), myStatus, status));
    myStatus = status;
  }

  public void updateVMInfo(@NotNull final VirtualMachine vm) {
    myVM = vm;
    if (vm.getConfig() == null){
      if (myStatus != InstanceStatus.SCHEDULED_TO_START) {
        setStatus(InstanceStatus.UNKNOWN); // still cloning
      }
      return;
    }
    final VirtualMachineRuntimeInfo runtime = vm.getRuntime();
    if (runtime != null && runtime.getPowerState() == VirtualMachinePowerState.poweredOn) {
      if (runtime.getBootTime() != null) {
        myStartDate = runtime.getBootTime().getTime();
      }
      if (myStatus != InstanceStatus.RUNNING) {
        setStatus(InstanceStatus.RUNNING);
      }
      myIpAddress = myVM.getGuest() == null ? null : myVM.getGuest().getIpAddress();
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
