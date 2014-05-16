package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
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
public class VMWareCloudInstance implements CloudInstance {

  private static final Logger LOG = Logger.getInstance(VMWareCloudInstance.class.getName());

  private final String myInstanceName;
  private final VMWareCloudImage myImage;
  private InstanceStatus myStatus = InstanceStatus.UNKNOWN;
  private VirtualMachine myVM = null;
  private CloudErrorInfo myErrorInfo;
  private Date myStartDate;
  private String myIpAddress;
  private boolean myDeleteAfterStop;

  public VMWareCloudInstance(@NotNull final VMWareCloudImage image, @NotNull final String instanceName) {
    myImage = image;
    myInstanceName = instanceName;
    myStartDate = new Date();
    myDeleteAfterStop = false;
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
  public CloudImage getImage() {
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

  public void setStatus(final InstanceStatus status) {
    myStatus = status;
  }

  public void updateVMInfo(VirtualMachine vm){
    myVM = vm;
    myStartDate = myVM.getRuntime() == null ? null : myVM.getRuntime().getBootTime().getTime();
    myIpAddress = myVM.getGuest() == null ? null : myVM.getGuest().getIpAddress();
    if (vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff && myStatus != InstanceStatus.STOPPED){
      myStatus = InstanceStatus.STOPPED;
    } else if (vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn && myStatus != InstanceStatus.RUNNING){
      myStatus = InstanceStatus.RUNNING;
    }
  }

  public void setErrorInfo(@Nullable final CloudErrorInfo errorInfo) {
    if (errorInfo == null) {
      LOG.info(String.format("Cleared error info for " + getInstanceId()));
    } else {
      LOG.warn(String.format("Setting error info for %s: %s(%s).",
                             getInstanceId(), errorInfo.getMessage(), errorInfo.getDetailedMessage()));
    }
    myErrorInfo = errorInfo;
  }

  public boolean isDeleteAfterStop() {
    return myDeleteAfterStop;
  }

  public void setDeleteAfterStop(final boolean deleteAfterStop) {
    myDeleteAfterStop = deleteAfterStop;
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public boolean containsAgent(@NotNull AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return getInstanceId().equals(configParams.get(INSTANCE_NAME));
  }
}
