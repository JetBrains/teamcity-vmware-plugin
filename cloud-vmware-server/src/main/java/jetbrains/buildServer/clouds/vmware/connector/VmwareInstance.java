

package jetbrains.buildServer.clouds.vmware.connector;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.*;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.vmware.VmwareSourceState;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/25/2014
 *         Time: 6:45 PM
 */
public class VmwareInstance extends AbstractInstance implements VmwareManagedEntity, Comparable<VmwareInstance> {
  private static final Logger LOG = Logger.getInstance(VmwareInstance.class.getName());

  private final String myId;
  @NotNull private final OptionValue[] myExtraConfig;
  @NotNull private final VirtualMachinePowerState myPowerState;
  private final boolean myIsTemplate;
  @NotNull private final String myChangeVersion;
  @Nullable private final Calendar myBootTime;
  @Nullable private final String myIpAddress;
  @Nullable private final ManagedObjectReference myParent;
  @NotNull private final String myDatacenterId;
  private final Map<String, String> myProperties;
  private final String myName;

  @Used("Tests")
  public VmwareInstance(@NotNull final VirtualMachine vm, String datacenterId){
    this(vm.getName(),
         vm.getMOR().getVal(),
         vm.getConfig() == null ? new OptionValue[0] : vm.getConfig().getExtraConfig(),
         vm.getRuntime().getPowerState(),
         vm.getConfig() != null && vm.getConfig().isTemplate(),
         vm.getConfig() == null ? "" : vm.getConfig().getChangeVersion(),
         vm.getRuntime().getBootTime(),
         vm.getGuest() == null ? null : vm.getGuest().getIpAddress(),
         vm.getParent().getMOR(),
         datacenterId);
  }

  public VmwareInstance(@NotNull final String name,
                        @NotNull final String id,
                        @NotNull final OptionValue[] extraConfig,
                        @NotNull final VirtualMachinePowerState powerState,
                        final boolean isTemplate,
                        @NotNull final String changeVersion,
                        @Nullable final Calendar bootTime,
                        @Nullable final String ipAddress,
                        @Nullable final ManagedObjectReference parent,
                        @NotNull final String datacenterId
                        ) {
    myName = name;
    myProperties = extractProperties(extraConfig);
    myId = id;
    myExtraConfig = extraConfig;
    myPowerState = powerState;
    myIsTemplate = isTemplate;
    myChangeVersion = changeVersion;
    myBootTime = bootTime;
    myIpAddress = ipAddress;
    myParent = parent;
    myDatacenterId = datacenterId;
  }


  @Nullable
  private  Map<String, String> extractProperties(@NotNull final OptionValue[] configInfo) {
    try {
      Map<String, String> retval = new HashMap<String, String>();
      for (OptionValue optionValue : configInfo) {
        retval.put(optionValue.getKey(), String.valueOf(optionValue.getValue()));
      }
      return retval;
    } catch (Exception ex){
      LOG.info("Unable to retrieve instance properties for " + myName  + ": " + ex.toString());
      return null;
    }
  }

  public boolean isPoweredOn() {
    return myPowerState == VirtualMachinePowerState.poweredOn;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getPath() {
    // no need to have full path for VirtualMachines: their names are unique across the whole datacenter
    return getName();
  }

  @Override
  public boolean isInitialized() {
    return myProperties != null;
  }

  @Nullable
  public String getProperty(@NotNull final String propertyName) {
    return myProperties == null ? null : myProperties.get(propertyName);
  }

  @Nullable
  public Date getStartDate() {
    if (myPowerState != VirtualMachinePowerState.poweredOn) {
      return null;
    }
    return myBootTime == null ? null : myBootTime.getTime();
  }

  @Nullable
  public String getIpAddress() {
    return myIpAddress;
  }

  @Override
  public InstanceStatus getInstanceStatus() {
    if (myPowerState == VirtualMachinePowerState.poweredOff) {
      return InstanceStatus.STOPPED;
    }
    if (myPowerState == VirtualMachinePowerState.poweredOn) {
      return InstanceStatus.RUNNING;
    }
    return InstanceStatus.UNKNOWN;
  }

  public boolean isReadonly() {
    return myIsTemplate;
  }

  @NotNull
  public String getChangeVersion() {
    return myChangeVersion;
  }

  @Nullable
  public VmwareSourceState getVmSourceState(){
    return VmwareSourceState.from(
      StringUtil.nullIfEmpty(getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT)),
      StringUtil.nullIfEmpty(getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_ID))
    );
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getDatacenterId() {
    return myDatacenterId;
  }

  @NotNull
  @Override
  public ManagedObjectReference getMOR() {
    throw new UnsupportedOperationException("VmwareInstance.getMOR");

    //return null;
  }

  @Nullable
  @Override
  public ManagedObjectReference getParentMOR() {
    throw new UnsupportedOperationException("VmwareInstance.getParentMOR");

    //return null;
  }

  public String getImageName(){
    final String nickname = getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_ID);

    // for backward compatibility
    return nickname == null ? getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME) : nickname;
  }

  public Map<String, String> getProperties() {
    return Collections.unmodifiableMap(myProperties);
  }

  @Nullable
  public String getServerUUID(){
    return getProperty(VMWareApiConnector.TEAMCITY_VMWARE_SERVER_UUID);
  }

  public String getProfileId(){
    return getProperty(VMWareApiConnector.TEAMCITY_VMWARE_PROFILE_ID);
  }

  public boolean isClone(){
    return "true".equals(getProperty(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
  }

  @Override
  public int compareTo(@NotNull final VmwareInstance o) {
    return StringUtil.compare(StringUtil.toLowerCase(myName), StringUtil.toLowerCase(o.myName));
  }

  @Override
  public String toString() {
    return "VmwareInstance{" +
           "myId='" + myId + '\'' +
           ", myPowerState=" + myPowerState +
           ", myChangeVersion='" + myChangeVersion + '\'' +
           '}';
  }
}