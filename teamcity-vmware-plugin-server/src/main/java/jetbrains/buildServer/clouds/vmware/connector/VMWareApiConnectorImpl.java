package jetbrains.buildServer.clouds.vmware.connector;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Pattern;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.*;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.*;

/**
 * @author Sergey.Pak
 *         Date: 4/17/2014
 *         Time: 11:32 AM
 */
public class VMWareApiConnectorImpl implements VMWareApiConnector {

  private static final Logger LOG = Logger.getInstance(VMWareApiConnectorImpl.class.getName());

  private static final long SHUTDOWN_TIMEOUT = 60 * 1000;

  private final URL myInstanceURL;
  private final String myUsername;
  private final String myPassword;
  private ServiceInstance myServiceInstance;


  public VMWareApiConnectorImpl(final URL instanceURL, final String username, final String password) throws MalformedURLException, RemoteException {
    myInstanceURL = instanceURL;
    myUsername = username;
    myPassword = password;

  }

  private synchronized Folder getRootFolder() throws RemoteException {
    try {
      if (myServiceInstance != null) {
        final SessionManager sessionManager = myServiceInstance.getSessionManager();
        if (sessionManager == null || sessionManager.getCurrentSession() == null) {
          myServiceInstance = null;
        }
      }
    } catch (Exception ex){
      ex.printStackTrace();
      myServiceInstance = null;
    }

    if (myServiceInstance == null){
      try {
        myServiceInstance = new ServiceInstance(myInstanceURL, myUsername, myPassword, true);
      } catch (MalformedURLException e) {
        throw new RemoteException("Unable to create ServiceInstance", e);
      }
    }
    return myServiceInstance.getRootFolder();
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
  public Map<String, VirtualMachine> getVirtualMachines(boolean filterClones) throws RemoteException {
    final Map<String, VirtualMachine> allVms = findAllEntitiesAsMap(VirtualMachine.class);
    final Map<String, VirtualMachine> filteredVms = new HashMap<String, VirtualMachine>();
    for (String vmName : allVms.keySet()) {
      final VirtualMachine vm = allVms.get(vmName);
      final VirtualMachineConfigInfo config = vm.getConfig();
      if (config == null) {
        if (!filterClones) {
          filteredVms.put(vmName, vm);
        }
        continue;
      }
      final OptionValue[] extraConfig = config.getExtraConfig();
      boolean isClone = false;
      for (OptionValue optionValue : extraConfig) {
        if (TEAMCITY_VMWARE_CLONED_INSTANCE.equals(optionValue.getKey()) && Boolean.valueOf(String.valueOf(optionValue.getValue()))) {
          isClone = true;
          break;
        }
      }
      if (!isClone || !filterClones) {
        filteredVms.put(vmName, vm);
      }
    }
    return filteredVms;
  }

  public Map<String, VirtualMachine> getClones(@NotNull final String imageName) throws RemoteException {
    final Map<String, VirtualMachine> allVms = findAllEntitiesAsMap(VirtualMachine.class);
    final Map<String, VirtualMachine> filteredVms = new HashMap<String, VirtualMachine>();
    for (Map.Entry<String, VirtualMachine> entry : allVms.entrySet()) {
      final VirtualMachine vm = entry.getValue();
      final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();

      for (OptionValue optionValue : extraConfig) {
        if (TEAMCITY_VMWARE_IMAGE_NAME.equals(optionValue.getKey()) &&
            imageName.equals(optionValue.getValue())) {
          filteredVms.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return filteredVms;
  }

  public Map<String, String> getVMParams(@NotNull final String vmName) throws RemoteException {
    final Map<String, String> map = new HashMap<String, String>();
    VirtualMachine vm = findEntityByName(vmName, VirtualMachine.class);
    final VirtualMachineConfigInfo config = vm.getConfig();
    if (config != null) {
      for (OptionValue val : config.getExtraConfig()) {
        map.put(val.getKey(), String.valueOf(val.getValue()));
      }
    }

    return map;
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
    if (vm == null || vm.getSnapshot() == null) {
      return Collections.emptyMap();
    }
    final VirtualMachineSnapshotTree[] rootSnapshotList = vm.getSnapshot().getRootSnapshotList();
    return snapshotNames(rootSnapshotList);
  }

  @Nullable
  public String getLatestSnapshot(@NotNull final String vmName, @NotNull final String snapshotNameMask) throws RemoteException {
    final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vmName);
    if (!snapshotNameMask.contains("*") && !snapshotNameMask.contains("?")) {
      return snapshotList.containsKey(snapshotNameMask) ? snapshotNameMask : null;
    }
    Date latestTime = new Date(0);
    String latestSnapshotName = null;
    for (Map.Entry<String, VirtualMachineSnapshotTree> entry : snapshotList.entrySet()) {
      final String snapshotNameMaskRegex = StringUtil.convertWildcardToRegexp(snapshotNameMask);
      final Pattern pattern = Pattern.compile(snapshotNameMaskRegex);
      if (pattern.matcher(entry.getKey()).matches()) {
        final Date snapshotTime = entry.getValue().getCreateTime().getTime();
        if (latestTime.before(snapshotTime)) {
          latestTime = snapshotTime;
          latestSnapshotName = entry.getKey();
        }
      }
    }
    return latestSnapshotName;
  }

  @Nullable
  public Task startInstance(@NotNull final VMWareCloudInstance instance, @NotNull final String agentName, @NotNull final CloudInstanceUserData userData)
    throws RemoteException, InterruptedException {
    final VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
    if (vm != null) {
      return vm.powerOnVM_Task(null);
    } else {
      instance.setErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
    }
    return null;
  }

  public Task reconfigureInstance(@NotNull final VMWareCloudInstance instance, @NotNull final String agentName, @NotNull final CloudInstanceUserData userData)
    throws RemoteException {
    final VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
    final VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
    spec.setExtraConfig(new OptionValue[]{
      createOptionValue(AGENT_NAME, agentName),
      createOptionValue(INSTANCE_NAME, instance.getInstanceId()),
      createOptionValue(AUTH_TOKEN, userData.getAuthToken()),
      createOptionValue(SERVER_URL, userData.getServerAddress()),
      createOptionValue(IMAGE_NAME, instance.getImageId()),
      createOptionValue(USER_DATA, userData.serialize())
    });
    return vm.reconfigVM_Task(spec);
  }

  @Nullable
  public Task cloneVm(@NotNull final VMWareCloudInstance instance, @NotNull String resourcePool,@NotNull String folder) throws RemoteException {
    final String imageName = instance.getImage().getName();
    LOG.info(String.format("Attempting to clone VM %s into %s", imageName, instance.getName()));
    VirtualMachine vm = findEntityByName(imageName, VirtualMachine.class);
    if (vm == null) {
      final String errorText = "Unable to find vm " + instance.getName();
      throw new RemoteException(errorText);
    }
    final VirtualMachineConfigSpec config = new VirtualMachineConfigSpec();
    final VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
    final VirtualMachineRelocateSpec location = new VirtualMachineRelocateSpec();

    cloneSpec.setLocation(location);
    cloneSpec.setConfig(config);
    location.setPool(findEntityByName(resourcePool, ResourcePool.class).getMOR());
    final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vm.getName());
    if (StringUtil.isNotEmpty(instance.getSnapshotName())) {

      final VirtualMachineSnapshotTree obj = snapshotList.get(instance.getSnapshotName());
      final ManagedObjectReference snapshot = obj == null ? null : obj.getSnapshot();
      cloneSpec.setSnapshot(snapshot);
      if (snapshot != null) {
        LOG.info("Using linked clone. Snapshot name: " + instance.getSnapshotName());
        location.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name());
      } else {
        final String errorText = "Unable to find snapshot " + instance.getSnapshotName();
        throw new RemoteException(errorText);
      }
    } else {
      LOG.info("Snapshot name is not specified. Will clone latest VM state");
    }


    config.setExtraConfig(new OptionValue[]{
      createOptionValue(TEAMCITY_VMWARE_CLONED_INSTANCE, "true"),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_NAME, imageName),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SNAPSHOT, instance.getSnapshotName()),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION, vm.getConfig().getChangeVersion())
    });

    return vm.cloneVM_Task(findEntityByName(folder, Folder.class), instance.getName(), cloneSpec);
  }

  public boolean isStartedByTeamcity(String instanceName) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instanceName, VirtualMachine.class);
    return getOptionValue(vm, SERVER_URL) != null;
  }

  public boolean isInstanceStopped(String instanceName) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instanceName, VirtualMachine.class);
    if (vm.getRuntime() != null) {
      return vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff;
    }
    return false;
  }


  protected static Map<String, VirtualMachineSnapshotTree> snapshotNames(@Nullable final VirtualMachineSnapshotTree[] trees) {
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

  private OptionValue createOptionValue(@NotNull final String key, @Nullable final String value) {
    final OptionValue optionValue = new OptionValue();
    optionValue.setKey(key);
    optionValue.setValue(value == null ? "" : value);
    return optionValue;
  }

  public void stopInstance(@NotNull final VMWareCloudInstance instance) {
    instance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);
    try {
      VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
      if (vm != null) {
        doShutdown(instance, vm);
      } else {
        instance.setStatus(InstanceStatus.ERROR);
      }
    } catch (Exception ex) {
      instance.setErrorType(VMWareCloudErrorType.INSTANCE_CANNOT_STOP);
      throw new RuntimeException(ex);
    }
  }

  private void doShutdown(@NotNull final VMWareCloudInstance instance, @NotNull VirtualMachine vm) throws RemoteException, InterruptedException {
    try {
      instance.setStatus(InstanceStatus.STOPPING);
      vm.shutdownGuest();
      long shutdownStartTime = System.currentTimeMillis();
      while (getInstanceStatus(vm) != InstanceStatus.STOPPED && (System.currentTimeMillis() - shutdownStartTime) < SHUTDOWN_TIMEOUT) {
        Thread.sleep(5000);
        vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
      }
      if (getInstanceStatus(vm) != InstanceStatus.STOPPED) {
        throw new RuntimeException("Stop timeout(" + SHUTDOWN_TIMEOUT + ") elapsed.");
      }

    } catch (Exception ex) {
      LOG.warn("Unable to stop using Guest Shutdown: " + ex.toString());
      final Task task = vm.powerOffVM_Task();
      final String powerOffResult = task.waitForTask();
      if (!Task.SUCCESS.equals(powerOffResult)) {
        instance.setErrorType(VMWareCloudErrorType.INSTANCE_CANNOT_STOP);
      }
    }
    instance.setStatus(InstanceStatus.STOPPED);
  }

  public Task deleteVirtualMachine(final VirtualMachine vm) throws RemoteException, InterruptedException {
    return vm.destroy_Task();
  }

  public void restartInstance(VMWareCloudInstance instance) throws RemoteException {
    final VirtualMachine vm = findEntityByName(instance.getInstanceId(), VirtualMachine.class);
    if (vm != null) {
      vm.rebootGuest();
    } else {
      instance.setStatus(InstanceStatus.ERROR);
    }
  }


  public boolean checkCloneFolderExists(@NotNull final String cloneFolderName) {
    try {
      return findEntityByName(cloneFolderName, Folder.class) != null;
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean checkResourcePoolExists(@NotNull final String resourcePool) {
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
  public String getImageName(@NotNull final VirtualMachine vm) {
    return getOptionValue(vm, TEAMCITY_VMWARE_IMAGE_NAME);
  }

  @Nullable
  private String getOptionValue(@NotNull final VirtualMachine vm, @NotNull final String optionName) {
    final VirtualMachineConfigInfo config = vm.getConfig();
    if (config == null)
      return null;
    final OptionValue[] extraConfig = config.getExtraConfig();
    for (OptionValue option : extraConfig) {
      if (optionName.equals(option.getKey())) {
        return String.valueOf(option.getValue());
      }
    }
    return null;
  }

  public InstanceStatus getInstanceStatus(@NotNull final VirtualMachine vm) {
    if (vm.getRuntime() == null || vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff) {
      return InstanceStatus.STOPPED;
    }
    if (vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn) {
      return InstanceStatus.RUNNING;
    }
    return InstanceStatus.UNKNOWN;
  }

  @NotNull
  public Map<String, String> getTeamcityParams(@NotNull final VirtualMachine vm) {
    final Map<String, String> params = new HashMap<String, String>();
    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    for (OptionValue optionValue : extraConfig) {
      if (optionValue.getKey().startsWith(TEAMCITY_VMWARE_PREFIX)) {
        params.put(optionValue.getKey(), String.valueOf(optionValue.getValue()));
      }
    }
    return params;
  }

  public void dispose(){
    try {
      if (myServiceInstance != null) {
        final ServerConnection serverConnection = myServiceInstance.getServerConnection();
        if (serverConnection != null)
          serverConnection.logout();
      }
    } catch (Exception ex){}
  }
}
