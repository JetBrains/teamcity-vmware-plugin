package jetbrains.buildServer.clouds.vmware.connector;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
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
public class VMWareApiConnectorImpl implements VMWareApiConnector {

  private static final Logger LOG = Logger.getInstance(VMWareCloudClient.class.getName());

  private static final long SHUTDOWN_TIMEOUT = 60*1000;

  private final URL myInstanceURL;
  private final String myUsername;
  private final String myPassword;


  public VMWareApiConnectorImpl(final URL instanceURL, final String username, final String password) throws MalformedURLException, RemoteException {
    myInstanceURL = instanceURL;
    myUsername = username;
    myPassword = password;
  }

  private Folder getRootFolder() throws RemoteException {
    try {
      final ServiceInstance serviceInstance = new ServiceInstance(myInstanceURL, myUsername, myPassword, true);
      return serviceInstance.getRootFolder();
    } catch (MalformedURLException e) {
      throw new RemoteException("Unable to create ServiceInstance", e);
    }
  }

  protected <T extends ManagedEntity> T findEntityByName(String name, Class<T> instanceType) throws RemoteException {
    ManagedEntity entity = new InventoryNavigator(getRootFolder()).searchManagedEntity(instanceType.getSimpleName(), name);
    if (entity == null) {
      return null;
    }
    return (T)entity;
  }

  protected <T extends ManagedEntity> Collection<T> findAllEntities(Class<T> instanceType) throws RemoteException {
    final ManagedEntity[] managedEntities = new InventoryNavigator(getRootFolder())
      .searchManagedEntities(new String[][]{{instanceType.getSimpleName(), "name"},}, true);
    List<T> retval = new ArrayList<T>();
    for (ManagedEntity managedEntity : managedEntities) {
      retval.add((T)managedEntity);
    }
    return retval;
  }

  protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMap(Class<T> instanceType) throws RemoteException {
    final ManagedEntity[] managedEntities = new InventoryNavigator(getRootFolder())
      .searchManagedEntities(new String[][]{{instanceType.getSimpleName(), "name"},}, true);
    Map<String, T> retval = new HashMap<String, T>();
    for (ManagedEntity managedEntity : managedEntities) {
      retval.put(managedEntity.getName(), (T)managedEntity);
    }
    return retval;
  }

  @NotNull
  public Map<String, VirtualMachine> getVirtualMachines() throws RemoteException {
    return findAllEntitiesAsMap(VirtualMachine.class);
  }

  @NotNull
  public Map<String, Folder> getFolders() throws RemoteException {
    return findAllEntitiesAsMap(Folder.class);
  }

  @NotNull
  public Map<String, ResourcePool> getResourcePools() throws RemoteException {
    return findAllEntitiesAsMap(ResourcePool.class);
  }

  @NotNull
  public Map<String, VirtualMachineSnapshotTree> getSnapshotList(final String vmName) throws RemoteException {
    final VirtualMachine vm = findEntityByName(vmName, VirtualMachine.class);
    if (vm.getSnapshot() == null){
      return Collections.emptyMap();
    }
    final VirtualMachineSnapshotTree[] rootSnapshotList = vm.getSnapshot().getRootSnapshotList();
    return snapshotNames(rootSnapshotList);
  }

  @Nullable
  public VirtualMachine startInstance(@NotNull final VMWareCloudInstance instance, @NotNull final String agentName, @NotNull final CloudInstanceUserData userData)
    throws RemoteException, InterruptedException {
    instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
    final VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
    if (vm != null) {
      final Task task = vm.powerOnVM_Task(null);
      instance.setStatus(InstanceStatus.STARTING);
      final String result = task.waitForTask();
      if (result != Task.SUCCESS) {
        final String errorMsg = String.format("Unable to start %s. Result: %s", instance.getName(), result);
        LOG.warn(errorMsg);
        instance.setErrorInfo(new CloudErrorInfo(errorMsg));
      }
      // adding special properties:
      final VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
      spec.setExtraConfig(new OptionValue[]{
        createOptionValue(AGENT_NAME, agentName),
        createOptionValue(INSTANCE_NAME, instance.getInstanceId()),
        createOptionValue(AUTH_TOKEN, userData.getAuthToken()),
        createOptionValue(SERVER_URL, userData.getServerAddress()),
        createOptionValue(IMAGE_NAME, instance.getImageId()),
        createOptionValue(USER_DATA, userData.serialize())
      });
      if (vm.reconfigVM_Task(spec).waitForTask() == Task.SUCCESS) {
        instance.setStatus(InstanceStatus.RUNNING);
      }
      return vm;
    } else {
      instance.setStatus(InstanceStatus.ERROR);
      instance.setErrorInfo(new CloudErrorInfo("VM " + instance.getInstanceId() + " not found!"));
    }
    return null;
  }

  public String cloneVmIfNecessary(VMWareCloudImage image) throws RemoteException, InterruptedException {
    VirtualMachine vm = findEntityByName(image.getId(), VirtualMachine.class);
    if (vm != null) {
      String cloneName;
      final VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
      final VirtualMachineRelocateSpec location = new VirtualMachineRelocateSpec();
      cloneSpec.setLocation(location);
      if (image.getResourcePool() != null) {
        location.setPool(findEntityByName(image.getResourcePool(), ResourcePool.class).getMOR());
      }
      cloneSpec.setTemplate(false);
      if (image.getImageType() == VMWareImageType.INSTANCE) {
        if (image.getStartType() == VMWareImageStartType.START) {
          return image.getName();
        }

        final ManagedObjectReference snapshot;
        if (image.getSnapshotName() != null) {
          final VirtualMachineSnapshotTree obj = getSnapshotList(vm.getName()).get(image.getSnapshotName());
          snapshot = obj==null ? null : obj.getSnapshot();
        } else {
          snapshot = null;
        }

        if (snapshot != null) { // linked clones only makes sense for snapshots
          if (image.getStartType() == VMWareImageStartType.LINKED_CLONE) {
            location.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name());
          }
          cloneSpec.setSnapshot(snapshot);
        }
      }
      SimpleDateFormat sdf = new SimpleDateFormat("Md-hhmmss");
      cloneName = String.format("%s-clone-%s", image.getId(), sdf.format(new Date()));
      final Task task = vm.cloneVM_Task(this.findEntityByName(image.getFolder(), Folder.class), cloneName, cloneSpec);
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



  protected static Map<String, VirtualMachineSnapshotTree> snapshotNames(@Nullable final VirtualMachineSnapshotTree[] trees){
    final Map<String, VirtualMachineSnapshotTree> treeNames = new HashMap<String, VirtualMachineSnapshotTree>();
    if (trees != null) {
      for (final VirtualMachineSnapshotTree tree : trees) {
        treeNames.put(tree.getName(), tree);
        treeNames.putAll(snapshotNames(tree.getChildSnapshotList()));
      }
    }
    return treeNames;
  }

  public boolean ensureSnapshotExists(String instanceName, String snapshotName) throws RemoteException {
    return getSnapshotList(instanceName).get(snapshotName) != null;
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
          LOG.warn("Unable to stop using Guest Shutdown: + " + ex.toString());
          final Task task = vm.powerOffVM_Task();
          final String powerOffResult = task.waitForTask();
          if (!Task.SUCCESS.equals(powerOffResult)){
            instance.setErrorInfo(new CloudErrorInfo("Unable to powerOff VM. Task status: " + powerOffResult, ex.toString(), ex));
          }
        }
        instance.setStatus(InstanceStatus.STOPPED);
        if (instance.isDeleteAfterStop()){ // we only destroy proper instances.
          if (instance.getErrorInfo() == null) {
            final Task destroyTask = vm.destroy_Task();
            final String destroyResult = destroyTask.waitForTask();
            if (!Task.SUCCESS.equals(destroyResult)) {
              instance.setErrorInfo(new CloudErrorInfo("Unable to destroy VM. Task status: " + destroyResult));
            }
          } else {
            LOG.warn(String.format("Won't delete instance %s with error: %s (%s)",
                                   instance.getName(), instance.getErrorInfo().getMessage(), instance.getErrorInfo().getDetailedMessage()));
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


  public boolean checkCloneFolderExists(@NotNull final String cloneFolderName){
    try {
      return findEntityByName(cloneFolderName, Folder.class) != null;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean checkResourcePoolExists(@NotNull final String resourcePool){
    try {
      return findEntityByName(resourcePool, ResourcePool.class) != null;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean checkVirtualMachineExists(@NotNull final String vmName) {
    try {
      return findEntityByName(vmName, VirtualMachine.class) != null;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return false;
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
