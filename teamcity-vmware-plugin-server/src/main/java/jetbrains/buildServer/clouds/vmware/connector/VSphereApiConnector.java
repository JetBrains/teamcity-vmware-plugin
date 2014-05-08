package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.*;

/**
 * @author Sergey.Pak
 *         Date: 4/17/2014
 *         Time: 11:32 AM
 */
public class VSphereApiConnector {

  private static final long SHUTDOWN_TIMEOUT = 60*1000;

  private final URL myInstanceURL;
  private final String myUsername;
  private final String myPassword;


  public VSphereApiConnector(final URL instanceURL, final String username, final String password) throws MalformedURLException, RemoteException {
    myInstanceURL = instanceURL;
    myUsername = username;
    myPassword = password;
  }

  private ServiceInstance createServiceInstance() throws RemoteException {
    try {
      return new ServiceInstance(myInstanceURL, myUsername, myPassword, true);
    } catch (MalformedURLException e) {
      throw new RemoteException("Unable to create ServiceInstance", e);
    }
  }

  public Map<String, VirtualMachine> getInstances() throws RemoteException {
    final Map<String, VirtualMachine> instances = new HashMap<String, VirtualMachine>();
    Folder rootFolder = createServiceInstance().getRootFolder();
    ManagedEntity[] vms = new InventoryNavigator(rootFolder).searchManagedEntities(
      new String[][]{{VMWareCloudConstants.VM_TYPE, "name"},}, true);
    for (int i = 0; i < vms.length; i++) {
      instances.put(vms[i].getName(), (VirtualMachine)vms[i]);
    }
    return instances;
  }

  @Nullable
  public VirtualMachine startInstance(VMWareCloudInstance instance, String agentName, String authToken, String serverURL)
    throws RemoteException, InterruptedException {
    instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
    final VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
    if (vm != null) {
      final Task task = vm.powerOnVM_Task(null);
      instance.setStatus(InstanceStatus.STARTING);
      // adding special properties:
      final VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
      spec.setExtraConfig(new OptionValue[]{
        createOptionValue(AGENT_NAME, agentName),
        createOptionValue(INSTANCE_NAME, instance.getInstanceId()),
        createOptionValue(AUTH_TOKEN, authToken),
        createOptionValue(SERVER_URL, serverURL),
        createOptionValue(IMAGE_NAME, instance.getImageId())
      });
      vm.reconfigVM_Task(spec);
      instance.setStatus(InstanceStatus.RUNNING);
      return vm;
    } else {
      instance.setStatus(InstanceStatus.ERROR);
    }
    return null;
  }

  public String cloneVmIfNecessary(VMWareCloudImage image, VMWareImageStartType startType, String cloneFolderName, String resourcePoolName) throws RemoteException, InterruptedException {
    VirtualMachine vm = findEntityByName(image.getId(), VirtualMachine.class);
    if (vm != null) {
      String cloneName;
      final VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
      final VirtualMachineRelocateSpec location = new VirtualMachineRelocateSpec();
      cloneSpec.setLocation(location);
      if (resourcePoolName != null) {
        location.setPool(findEntityByName(resourcePoolName, ResourcePool.class).getMOR());
      }
      cloneSpec.setTemplate(false);
      if (image.getImageType() == VMWareImageType.INSTANCE) {
        if (startType == VMWareImageStartType.START) {
          return image.getName();
        }

        final ManagedObjectReference snapshot;
        if (image.getSnapshotName() != null) {
          snapshot = findSnapshot(vm.getSnapshot().getRootSnapshotList(), image.getSnapshotName());
        } else {
          snapshot = null;
        }

        if (snapshot != null) { // linked clones only makes sense for snapshots
          if (startType == VMWareImageStartType.LINKED_CLONE) {
            location.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name());
          }
          cloneSpec.setSnapshot(snapshot);
        }
      }
      SimpleDateFormat sdf = new SimpleDateFormat("Md-hhmmss");
      cloneName = String.format("%s-clone-%s", image.getId(), sdf.format(new Date()));
      final Task task = vm.cloneVM_Task(this.findEntityByName(cloneFolderName, Folder.class), cloneName, cloneSpec);
      final String status = task.waitForTask();
      if (Task.SUCCESS.equals(status)) {
        image.setErrorInfo(null);
        return cloneName;
      } else {
        image.setErrorInfo(new CloudErrorInfo("Unable to clone VM: " + status));
        return null;
      }
    }
    return null;
  }

  public boolean isStartedByTeamcity(String instanceName) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instanceName, VirtualMachine.class);
    return getOptionValue(vm, SERVER_URL) != null;
  }

  public boolean isInstanceStopped(String instanceName) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instanceName, VirtualMachine.class);
    if (vm.getRuntime() != null){
      return vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff;
    }
    return false;
  }

  @Nullable
  private static ManagedObjectReference findSnapshot(@Nullable final VirtualMachineSnapshotTree[] trees, @NotNull final String snapshotName) {
    if (trees == null) {
      return null;
    }
    for (final VirtualMachineSnapshotTree tree : trees) {
      if (snapshotName.equals(tree.getName())) {
        return tree.getSnapshot();
      }
      final ManagedObjectReference snapshot = findSnapshot(tree.getChildSnapshotList(), snapshotName);
      if (snapshot != null) {
        return snapshot;
      }
    }
    return null;
  }

  public boolean ensureSnapshotExists(String instanceName, String snapshotName) throws RemoteException {
    VirtualMachine vm = findEntityByName(instanceName, VirtualMachine.class);
    final ManagedObjectReference snapshot = findSnapshot(vm.getSnapshot().getRootSnapshotList(), snapshotName);
    return snapshot != null;
  }

  private OptionValue createOptionValue(String key, String value) {
    final OptionValue optionValue = new OptionValue();
    optionValue.setKey(key);
    optionValue.setValue(value);
    return optionValue;
  }

  public void stopInstance(VMWareCloudInstance instance) {
    instance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);
    try {
      VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
      if (vm != null) {
        try {
          instance.setStatus(InstanceStatus.STOPPING);
          vm.shutdownGuest();
          long shutdownStartTime = System.currentTimeMillis();
          while (getInstanceStatus(vm) != InstanceStatus.STOPPED && (System.currentTimeMillis() - shutdownStartTime) < SHUTDOWN_TIMEOUT){
            vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
            Thread.sleep(5000);
          }
          if (getInstanceStatus(vm) != InstanceStatus.STOPPED){
            throw new RuntimeException("Stop timeout("+SHUTDOWN_TIMEOUT+") elapsed.");
          }

        } catch (Exception ex) {
          instance.setErrorInfo(new CloudErrorInfo("Unable to stop using Guest Shutdown", ex.toString(), ex));
          final Task task = vm.powerOffVM_Task();
          final String powerOffResult = task.waitForTask();
          if (!Task.SUCCESS.equals(powerOffResult)){
            instance.setErrorInfo(new CloudErrorInfo("Unable to powerOff VM. Task status: " + powerOffResult, ex.toString(), ex));
          }
        }
        instance.setStatus(InstanceStatus.STOPPED);
        if (instance.isDeleteAfterStop() && instance.getErrorInfo() == null){ // we only destroy proper instances.
          final Task destroyTask = vm.destroy_Task();
          final String destroyResult = destroyTask.waitForTask();
          if (!Task.SUCCESS.equals(destroyResult)){
            instance.setErrorInfo(new CloudErrorInfo("Unable to destroy VM. Task status: " + destroyResult));
          }
        }
      } else {
        instance.setStatus(InstanceStatus.ERROR);
      }
    } catch (Exception ex) {
      instance.setStatus(InstanceStatus.ERROR);
      instance.setErrorInfo(new CloudErrorInfo("Unable to terminate instance", ex.toString(), ex));
      throw new RuntimeException(ex);
    }
  }

  public void restartInstance(VMWareCloudInstance instance) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
    if (vm != null) {
      vm.rebootGuest();
    } else {
      instance.setStatus(InstanceStatus.ERROR);
    }
  }

  private <T extends ManagedEntity> T findEntityByName(String name, Class<T> instanceType) throws RemoteException {
    ServiceInstance serviceInstance = createServiceInstance();
    Folder rootFolder = serviceInstance.getRootFolder();
    ManagedEntity entity = new InventoryNavigator(rootFolder).searchManagedEntity(instanceType.getSimpleName(), name);
    if (entity == null) {
      return null;
    }
    return (T)entity;
  }

  public boolean checkCloneFolder(@NotNull final String cloneFolderName){
    try {
      return findEntityByName(cloneFolderName, Folder.class) != null;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean checkResourcePool(@NotNull final String resourcePool){
    try {
      return findEntityByName(resourcePool, ResourcePool.class) != null;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return false;
  }

  public String getVMIpAddress(String instanceName) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instanceName, VirtualMachine.class);
    if (vm != null) {
      final GuestInfo guest = vm.getGuest();
      return guest != null ? guest.getIpAddress() : null;
    }
    return null;
  }

  public VirtualMachine getInstanceDetails(String instanceName) throws RemoteException {
    return findEntityByName(instanceName, VirtualMachine.class);
  }

  @Nullable
  public String getImageName(VirtualMachine vm){
    return getOptionValue(vm, IMAGE_NAME);
  }

  @Nullable
  private String getOptionValue(@NotNull final VirtualMachine vm, @NotNull final String optionName){
    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    for (OptionValue option : extraConfig) {
      if (optionName.equals(option.getKey())){
        return String.valueOf(option.getValue());
      }
    }
    return null;
  }

  public InstanceStatus getInstanceStatus(VirtualMachine vm){
    if (vm.getRuntime() == null || vm.getRuntime().getPowerState() ==VirtualMachinePowerState.poweredOff){
      return InstanceStatus.STOPPED;
    }
    if (vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn){
      return InstanceStatus.RUNNING;
    }
    return InstanceStatus.UNKNOWN;
  }
}
