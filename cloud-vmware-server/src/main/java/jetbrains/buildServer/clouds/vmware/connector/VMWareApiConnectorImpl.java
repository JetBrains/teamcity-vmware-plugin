/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.vmware.connector;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import com.vmware.vim25.mo.util.MorUtil;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImageDetails;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.VmwareConstants;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.*;
import static jetbrains.buildServer.clouds.vmware.connector.VmwareUtils.isSpecial;

/**
 * @author Sergey.Pak
 *         Date: 4/17/2014
 *         Time: 11:32 AM
 */
public class VMWareApiConnectorImpl implements VMWareApiConnector {

  private static final Logger LOG = Logger.getInstance(VMWareApiConnectorImpl.class.getName());
  private static final String VM_TYPE = VirtualMachine.class.getSimpleName();
  private static final String LINUX_GUEST_FAMILY = "linuxGuest";
  private static final Pattern FQDN_PATTERN = Pattern.compile("[^\\.]+\\.(.+)");
  private static final Pattern RESPOOL_PATTERN = Pattern.compile("resgroup-\\d+");
  private static final Pattern FOLDER_PATTERN = Pattern.compile("group-v\\d+");

  private static final long SHUTDOWN_TIMEOUT = 60 * 1000;

  private final URL myInstanceURL;
  private final String myUsername;
  private final String myPassword;
  private ServiceInstance myServiceInstance;
  private final String myDomain;

  @Nullable private final String myServerUUID;
  // it can be null, when we create a temporary api connector for a short-term use (for example, when we prepopulate information on create/edit cloud profile page
  @Nullable private final String myProfileId;
  // we also create a separate connector for controller and which doesn't need this field
  @Nullable private final CloudInstancesProvider myInstancesProvider;


  public VMWareApiConnectorImpl(@NotNull final URL instanceURL,
                                @NotNull final String username,
                                @NotNull final String password,
                                @Nullable final String serverUUID,
                                @Nullable final String profileId,
                                @Nullable final CloudInstancesProvider instancesProvider){
    myInstanceURL = instanceURL;
    myUsername = username;
    myPassword = password;
    myServerUUID = serverUUID;
    myProfileId = profileId;
    myInstancesProvider = instancesProvider;
    myDomain = getTCServerDomain();
    if (myDomain == null){
      LOG.info("Unable to determine server domain. Linux guest hostname customization is disabled");
    } else {
      LOG.info("Domain is " + myDomain + ". Will use the Linux guest hostname customization");
    }
  }

  private synchronized Folder getRootFolder() throws VmwareCheckedCloudException {
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
        myServiceInstance = new ServiceInstance(myInstanceURL, myUsername, myPassword, true, 10*1000, 30*1000);
      } catch (MalformedURLException e) {
        throw new VmwareCheckedCloudException("Invalid server URL", e);
      } catch (RemoteException e) {
        throw new VmwareCheckedCloudException(e);
      }
    }
    return myServiceInstance.getRootFolder();
  }

  private boolean isId(String idName, Class instanceType){
    if (instanceType == ResourcePool.class) {
      return RESPOOL_PATTERN.matcher(idName).matches();
    } else if (instanceType == Folder.class) {
      return FOLDER_PATTERN.matcher(idName).matches();
    } else {
      return false;
    }
  }

  @Nullable
  protected <T extends ManagedEntity> T findEntityByIdNameNullable(@NotNull final String idName,
                                                                   @NotNull final Class<T> instanceType,
                                                                   @Nullable final Datacenter dc) throws VmwareCheckedCloudException {
    try {
      if (isId(idName, instanceType)) {
        ManagedObjectReference mor = new ManagedObjectReference();
        mor.setType(instanceType.getSimpleName());
        mor.setVal(idName);
        return (T)MorUtil.createExactManagedEntity(myServiceInstance.getServerConnection(), mor);
      } else {
        if (dc == null) {
          return (T)new InventoryNavigator(getRootFolder()).searchManagedEntity(instanceType.getSimpleName(), idName);
        } else {
          return (T)new InventoryNavigator(dc).searchManagedEntity(instanceType.getSimpleName(), idName);
        }
      }
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
  }

  @NotNull
  protected <T extends ManagedEntity> T findEntityByIdName(String idName, Class<T> instanceType) throws VmwareCheckedCloudException  {
    final T entity = findEntityByIdNameNullable(idName, instanceType, null);
    if (entity == null) {
      throw new VmwareCheckedCloudException(String.format("Unable to find %s '%s'", instanceType.getSimpleName(), idName));
    }
    return entity;
  }

  protected <T extends ManagedEntity> Collection<T> findAllEntities(Class<T> instanceType) throws VmwareCheckedCloudException  {
    final ManagedEntity[] managedEntities;
    try {
      managedEntities = new InventoryNavigator(getRootFolder())
        .searchManagedEntities(new String[][]{{instanceType.getSimpleName(), "name"},}, true);
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
    List<T> retval = new ArrayList<T>();
    for (ManagedEntity managedEntity : managedEntities) {
      retval.add((T)managedEntity);
    }
    return retval;
  }

  protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMap(Class<T> instanceType) throws VmwareCheckedCloudException  {
    final ManagedEntity[] managedEntities;
    try {
      managedEntities = new InventoryNavigator(getRootFolder())
        .searchManagedEntities(new String[][]{{instanceType.getSimpleName(), "name"},}, true);
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
    Map<String, T> retval = new HashMap<String, T>();
    for (ManagedEntity managedEntity : managedEntities) {
      try {
        retval.put(managedEntity.getName(), (T)managedEntity);
      } catch (Exception ex){}
    }
    return retval;
  }

  @NotNull
  public Map<String, VmwareInstance> getVirtualMachines(boolean filterClones) throws VmwareCheckedCloudException {
    final Map<String, VirtualMachine> allVms = findAllEntitiesAsMap(VirtualMachine.class);
    final Map<String, VmwareInstance> filteredVms = new HashMap<String, VmwareInstance>();
    for (String vmName : allVms.keySet()) {
      final VirtualMachine vm = allVms.get(vmName);
      final VmwareInstance vmInstance = new VmwareInstance(vm);
      if (!vmInstance.isInitialized()) {
        if (!filterClones) {
          filteredVms.put(vmInstance.getName(), vmInstance);
        }
        continue;
      }

      if (!vmInstance.isClone() || !filterClones) {
        filteredVms.put(vmName, vmInstance);
      }
    }
    return filteredVms;
  }

  @Override
  @NotNull
  public <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final VmwareCloudImage image) throws VmwareCheckedCloudException {
    Map<VmwareCloudImage, Map<String, R>> imageMap = fetchInstances(Collections.singleton(image));
    Map<String, R> res = imageMap.get(image);
    return res == null ? Collections.emptyMap() : res;
  }

  @Override
  @NotNull
  public <R extends AbstractInstance> Map<VmwareCloudImage, Map<String, R>> fetchInstances(@NotNull final Collection<VmwareCloudImage> images) throws VmwareCheckedCloudException {
    Map<VmwareCloudImage, Map<String, R>> result = new HashMap<>();
    List<VmwareCloudImage> unprocessed = new ArrayList<>();
    for (VmwareCloudImage image: images) {
      final VmwareCloudImageDetails imageDetails = image.getImageDetails();
      if(imageDetails.getBehaviour().isUseOriginal()){
        final VirtualMachine vmEntity = findEntityByIdName(imageDetails.getSourceVmName(), VirtualMachine.class);
        final VmwareInstance vmInstance = new VmwareInstance(vmEntity);
        result.put(image, Collections.singletonMap(image.getName(), (R)vmInstance));
      } else {
        unprocessed.add(image);
      }
    }

    if (unprocessed.isEmpty()) return result;

    Map<String, VmwareCloudImage> imageNameMap = new HashMap<>();
    for (VmwareCloudImage image: unprocessed) {
      imageNameMap.put(image.getName(), image);
    }

    final Collection<VirtualMachine> virtualMachines = findAllEntities(VirtualMachine.class);
    for (VirtualMachine vm : virtualMachines) {
      try {
        final VmwareInstance vmInstance = new VmwareInstance(vm);
        final String instanceImage = vmInstance.getImageName();
        VmwareCloudImage image = imageNameMap.get(instanceImage);
        if (image != null) {
          Map<String, R> imageInstancesMap = result.get(image);
          if (imageInstancesMap == null) {
            imageInstancesMap = new HashMap<>();
            final String serverUUID = vmInstance.getServerUUID();
            if (StringUtil.isNotEmpty(serverUUID) && !serverUUID.equals(myServerUUID)) {
              LOG.debug(String.format("Instance '%s' belongs to server with another UUID('%s'). Our UUID is '%s'", vmInstance.getName(), serverUUID, myServerUUID));
              continue;
            }
            final String profileId = vmInstance.getProfileId();
            if (StringUtil.isNotEmpty(serverUUID) && !profileId.equals(myProfileId)) {
              LOG.debug(String.format("Instance '%s' belongs to another cloud profile id('%s'). Our cloud profile id is '%s'", vmInstance.getName(), profileId, myProfileId));
              continue;
            }
            result.put(image, imageInstancesMap);
          }
          imageInstancesMap.put(vmInstance.getName(), (R)vmInstance);
        }
      } catch (Exception ex) {
        final ManagedObjectReference mor = vm.getMOR();
        if (mor != null) {
          LOG.debug("Unable to process " + mor.getType() + " " + mor.getVal());
        } else {
          LOG.debug("Null MOR passed");
        }
      }
    }

    return result;
  }

  @NotNull
  @Override
  public Map<String, String> getCustomizationSpecs() {
    final Map<String,String> retval = new HashMap<>();
    final CustomizationSpecManager specManager = myServiceInstance.getCustomizationSpecManager();
    final CustomizationSpecInfo[] specs = specManager.getInfo();
    if (specs != null) {
      for (CustomizationSpecInfo spec : specs) {
        retval.put(spec.getName(), spec.getType());
      }
    }
    return retval;
  }

  @Override
  public CustomizationSpec getCustomizationSpec(final String name) throws VmwareCheckedCloudException {
    final CustomizationSpecManager specManager = myServiceInstance.getCustomizationSpecManager();
    try {
      return specManager.getCustomizationSpec(name).getSpec();
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException("Unable to get Customization Spec: '" + name + "'" , e);
    }
  }

  public Map<String, String> getVMParams(@NotNull final String vmName) throws VmwareCheckedCloudException {
    final Map<String, String> map = new HashMap<String, String>();
    VirtualMachine vm = findEntityByIdName(vmName, VirtualMachine.class);
    final VirtualMachineConfigInfo config = vm.getConfig();
    if (config != null) {
      for (OptionValue val : config.getExtraConfig()) {
        map.put(val.getKey(), String.valueOf(val.getValue()));
      }
    }

    return map;
  }

  @NotNull
  public Map<String, VmwareManagedEntity> getFolders() throws VmwareCheckedCloudException {
    final Collection<Folder> allFolders = CollectionsUtil.filterCollection(findAllEntities(Folder.class), new Filter<Folder>() {
      public boolean accept(@NotNull final Folder folder) {
        return canContainVMs(folder);
      }
    });

    final boolean containsDuplicates = containsDuplicates(allFolders);
    final Map<String, VmwareManagedEntity> filteredMap = new HashMap<String, VmwareManagedEntity>();

    for (Folder folder : allFolders) {
      String folderName = VmwareUtils.getEntityDisplayName(folder);
      if (containsDuplicates) {
        folderName = getFullFolderPath(folder);
      }
      filteredMap.put(folderName, new VmwareManagedEntityImpl(folder));
    }
    return filteredMap;
  }

  @NotNull
  public Map<String, VmwareManagedEntity> getResourcePools() throws VmwareCheckedCloudException {
    final Collection<ResourcePool> allPools = CollectionsUtil.filterCollection(findAllEntities(ResourcePool.class), new Filter<ResourcePool>() {
      public boolean accept(@NotNull final ResourcePool resourcePool) {
        return !isSpecial(resourcePool);
      }
    });
    boolean containsDuplicates = containsDuplicates(allPools);
    Map<String, VmwareManagedEntity> retval = new HashMap<String, VmwareManagedEntity>();
    for (ResourcePool pool : allPools) {

      String poolName = pool.getName();

      if (containsDuplicates) {
        poolName = getFullResourcePoolPath(pool);
      }
      retval.put(poolName, new VmwareManagedEntityImpl(pool));
    }
    return retval;
  }


  private boolean canContainVMs(final Folder folder) {
    final String[] childTypes = folder.getChildType();
    for (String childType : childTypes) {
      if (VM_TYPE.equals(childType)) {
        return true;
      }
    }
    return false;
  }

  private String getFullFolderPath(final Folder folder){
    final String folderMORType = folder.getMOR().getType();
    final StringBuilder pathBuilder = new StringBuilder(VmwareUtils.getEntityDisplayName(folder));
    ManagedEntity entity = folder.getParent();
    if (isSpecial(folder)){ // we'll skip the first parent
      entity = entity.getParent();
    }
    while (entity != null){
      boolean skip = false;
      if (entity.getMOR().getType().equals(folderMORType)){
        skip = true;
        if (entity.getParent() != null && !entity.getName().equals("vm") && folderMORType.equals(entity.getParent().getMOR().getType())) {
          final String[] childTypes = ((Folder)entity).getChildType();
          for (String childType : childTypes) {
            if (VirtualMachine.class.getSimpleName().equals(childType)) {
              skip = false;
              break;
            }
          }
        }
      }
      if (!skip)
        pathBuilder.insert(0, String.format("%s/", entity.getName()));
      entity = entity.getParent();
    }
    return pathBuilder.toString();
  }

  private String getFullResourcePoolPath(final ResourcePool pool){
    final String resPool = pool.getMOR().getType();
    StringBuilder pathBuilder = new StringBuilder(pool.getName());
    ManagedEntity entity = pool.getParent();
    while (entity != null){
      boolean skip = false;
      if ("Resources".equals(entity.getName()) && entity.getParent() != null && !resPool.equals(entity.getParent().getMOR())) {
        skip = true;
      }
      if (entity.getMOR().getType().equals(Folder.class.getSimpleName())){
        skip = true;
      }

      if (!skip) {
        pathBuilder.insert(0, String.format("%s/", entity.getName()));
      }

      entity = entity.getParent();
    }
    return pathBuilder.toString();
  }


  @NotNull
  private Map<String, VirtualMachineSnapshotTree> getSnapshotList(final VirtualMachine vm) {
    if (vm.getSnapshot() == null) {
      return Collections.emptyMap();
    }
    final VirtualMachineSnapshotTree[] rootSnapshotList = vm.getSnapshot().getRootSnapshotList();
    return snapshotNames(rootSnapshotList);
  }

  public Map<String, VirtualMachineSnapshotTree> getSnapshotList(final String vmName) throws VmwareCheckedCloudException {
    return getSnapshotList(findEntityByIdName(vmName, VirtualMachine.class));
  }

  @Nullable
  public String getLatestSnapshot(@NotNull final String vmName, @NotNull final String snapshotNameMask) throws VmwareCheckedCloudException {
    if (VmwareConstants.CURRENT_STATE.equals(snapshotNameMask)){
      return VmwareConstants.CURRENT_STATE;
    }
    final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vmName);
    return getLatestSnapshot(snapshotNameMask, snapshotList);
  }

  private boolean containsDuplicates(Collection<? extends ManagedEntity> entities){
    final Set<String> names = new HashSet<String>();
    for (ManagedEntity entity : entities) {
      if (!names.add(entity.getName())){
        return true;
      }
    }
    return false;
  }


  private String getLatestSnapshot(final String snapshotNameMask, final Map<String, VirtualMachineSnapshotTree> snapshotList) {
    if (snapshotNameMask == null)
      return null;
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
  public Task startInstance(@NotNull final VmwareCloudInstance instance, @NotNull final String agentName, @NotNull final CloudInstanceUserData userData)
    throws VmwareCheckedCloudException, InterruptedException {
    final VirtualMachine vm = findEntityByIdName(instance.getInstanceId(), VirtualMachine.class);
    if (vm != null) {
      try {
        return vm.powerOnVM_Task(null);
      } catch (RemoteException e) {
        throw new VmwareCheckedCloudException(e);
      }
    } else {
      instance.updateErrors(new TypedCloudErrorInfo(String.format("Instance %s doesn't exist", instance.getInstanceId())));
    }
    return null;
  }

  public Task reconfigureInstance(@NotNull final VmwareCloudInstance instance, @NotNull final String agentName, @NotNull final CloudInstanceUserData userData)
    throws VmwareCheckedCloudException {
    final VirtualMachine vm = findEntityByIdName(instance.getInstanceId(), VirtualMachine.class);
    final VirtualMachineConfigSpec spec = new VirtualMachineConfigSpec();
    spec.setExtraConfig(new OptionValue[]{
      createOptionValue(AGENT_NAME, agentName),
      createOptionValue(INSTANCE_NAME, instance.getInstanceId()),
      createOptionValue(AUTH_TOKEN, userData.getAuthToken()),
      createOptionValue(SERVER_URL, userData.getServerAddress()),
      createOptionValue(IMAGE_NAME, instance.getImageId()),
      createOptionValue(USER_DATA, userData.serialize())
    });
    try {
      return vm.reconfigVM_Task(spec);
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
  }

  @Nullable
  @Override
  public Task cloneAndStartVm(@NotNull final VmwareCloudInstance instance) throws VmwareCheckedCloudException {
    final VmwareCloudImageDetails imageDetails = instance.getImage().getImageDetails();
    LOG.info(String.format("Attempting to clone VM %s into %s", imageDetails.getSourceVmName(), instance.getName()));
    final VirtualMachine vm = findEntityByIdName(imageDetails.getSourceVmName(), VirtualMachine.class);
    final Datacenter datacenter = getParentOfType(vm, Datacenter.class);

    final VirtualMachineConfigSpec config = new VirtualMachineConfigSpec();
    final VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
    final VirtualMachineRelocateSpec location = new VirtualMachineRelocateSpec();

    cloneSpec.setPowerOn(true);
    cloneSpec.setLocation(location);
    cloneSpec.setConfig(config);
    final boolean disableOsCustomization = TeamCityProperties.getBoolean(VmwareConstants.DISABLE_OS_CUSTOMIZATION);
    if (!VmwareConstants.DEFAULT_RESOURCE_POOL.equals(imageDetails.getResourcePoolId())) {
      final ResourcePool pool = findEntityByIdNameNullable(imageDetails.getResourcePoolId(), ResourcePool.class, datacenter);
      if (pool != null) {
        location.setPool(pool.getMOR());
      } else {
        LOG.warn(String.format("Unable to find resource pool %s at datacenter %s. Will clone at the image resource pool instead"
          , imageDetails.getResourcePoolId()
          , datacenter == null? "<not provided>":  datacenter.getName()));
      }
    }
    final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vm);
    final String snapshotName = instance.getSnapshotName();
    if (imageDetails.useCurrentVersion() || StringUtil.isEmpty(snapshotName)) {
      LOG.info("Snapshot name is not specified. Will clone latest VM state");
    } else {
      final VirtualMachineSnapshotTree obj = snapshotList.get(snapshotName);
      final ManagedObjectReference snapshot = obj == null ? null : obj.getSnapshot();
      cloneSpec.setSnapshot(snapshot);
      if (snapshot != null) {
        if (TeamCityProperties.getBooleanOrTrue(VmwareConstants.USE_LINKED_CLONE)) {
          LOG.info("Using linked clone. Snapshot name: " + snapshotName);
          location.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name());
        } else {
          LOG.info("Using full clone. Snapshot name: " + snapshotName);
        }
      } else {
        final String errorText = "Unable to find snapshot " + snapshotName;
        throw new VmwareCheckedCloudException(errorText);
      }
    }

    final VirtualMachineConfigInfo vmConfig = vm.getConfig();
    config.setExtraConfig(new OptionValue[]{
      createOptionValue(TEAMCITY_VMWARE_CLONED_INSTANCE, "true"),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME, imageDetails.getSourceVmName()),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SOURCE_ID, imageDetails.getSourceId()),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SNAPSHOT, snapshotName),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION, vmConfig.getChangeVersion()),
      createOptionValue(TEAMCITY_VMWARE_PROFILE_ID, StringUtil.emptyIfNull(myProfileId)),
      createOptionValue(TEAMCITY_VMWARE_SERVER_UUID, StringUtil.emptyIfNull(myServerUUID))
    });

    final GuestInfo guest = vm.getGuest();
    String guestFamily = guest != null ? guest.getGuestFamily() : null;
    if (guestFamily == null){
      final String guestFullName = vmConfig.getGuestFullName();
      if (guestFullName != null && guestFullName.contains("Linux")){
        guestFamily = LINUX_GUEST_FAMILY;
      }
    }

    if (StringUtil.isNotEmpty(imageDetails.getCustomizationSpec())){
      LOG.info(String.format("Will use Customization Spec '%s' to clone %s into %s"
        , imageDetails.getCustomizationSpec(), imageDetails.getSourceVmName(), instance.getName()));
      cloneSpec.setCustomization(getCustomizationSpec(imageDetails.getCustomizationSpec()));
    } else if (!disableOsCustomization && myDomain != null && LINUX_GUEST_FAMILY.equals(guestFamily)){
      LOG.info("Will use basic Linux customization (will customize hostname)");
      // TODO: remove later, after all profiles are updated to use customization spec
      final CustomizationSpec customization = new CustomizationSpec();
      final CustomizationLinuxPrep linuxPrep = new CustomizationLinuxPrep();
      final CustomizationLinuxOptions linuxOptions = new CustomizationLinuxOptions();

      linuxPrep.setHostName(new CustomizationVirtualMachineName());
      linuxPrep.setDomain(myDomain);
      customization.setIdentity(linuxPrep);
      customization.setOptions(linuxOptions);

      customization.setGlobalIPSettings(new CustomizationGlobalIPSettings());
      final CustomizationAdapterMapping mapping = new CustomizationAdapterMapping();
      final CustomizationIPSettings ipSettings = new CustomizationIPSettings();
      mapping.setAdapter(ipSettings);
      ipSettings.setIp(new CustomizationDhcpIpGenerator());
      customization.setNicSettingMap(new CustomizationAdapterMapping[]{mapping});

      cloneSpec.setCustomization(customization);
    }

    try {
      final Folder folder = findEntityByIdNameNullable(imageDetails.getFolderId(), Folder.class, datacenter);
      if (folder != null) {
        return vm.cloneVM_Task(folder, instance.getName(), cloneSpec);
      } else {
        String dcName = datacenter == null ? "root" : datacenter.getName();
        throw new VmwareCheckedCloudException(
          String.format("Unable to find folder %s in datacenter %s", imageDetails.getFolderId(), dcName)
        );
      }
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
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

  private OptionValue createOptionValue(@NotNull final String key, @Nullable final String value) {
    final OptionValue optionValue = new OptionValue();
    optionValue.setKey(key);
    optionValue.setValue(value == null ? "" : value);
    return optionValue;
  }

  public Task stopInstance(@NotNull final VmwareCloudInstance instance) {
    instance.setStatus(InstanceStatus.STOPPING);
    try {
      VirtualMachine vm = findEntityByIdName(instance.getInstanceId(), VirtualMachine.class);
      if (getInstanceStatus(vm) == InstanceStatus.STOPPED) {
        return successTask();
      }
      return doShutdown(instance, vm);
    } catch (Exception ex) {
      instance.updateErrors(TypedCloudErrorInfo.fromException(ex));
      throw new CloudException(ex.getMessage(),ex);
    }
  }

  private Task doShutdown(@NotNull final VmwareCloudInstance instance, @NotNull final VirtualMachine vm) throws VmwareCheckedCloudException {
    try {
      guestShutdown(instance, vm);
      final long shutdownStartTime = System.currentTimeMillis();
      return new Task(null, null){
        private final TaskInfo myInfo = new TaskInfo();

        {myInfo.setState(TaskInfoState.running);}

        @Override
        public String waitForTask() throws RemoteException, InterruptedException {
          if (waitForStatus(shutdownStartTime, 5000) != InstanceStatus.STOPPED) {
            myInfo.setState(TaskInfoState.error);
          } else {
            myInfo.setState(TaskInfoState.success);
          }
          return myInfo.getState().name();
        }

        @Override
        public String waitForTask(final int runningDelayInMillSecond, final int queuedDelayInMillSecond) throws RemoteException, InterruptedException {
          if (runningDelayInMillSecond >= (System.currentTimeMillis() -  shutdownStartTime)){
            return waitForTask();
          } else {
            final InstanceStatus instanceStatus = waitForStatus(runningDelayInMillSecond, 5000);
            if (instanceStatus == InstanceStatus.STOPPED){
              myInfo.setState(TaskInfoState.success);
            }
          }
          return myInfo.getState().name();
        }

        @Override
        public TaskInfo getTaskInfo() throws RemoteException {
          try {
            final InstanceStatus instanceStatus = waitForStatus(0, 5000);
            if (instanceStatus == InstanceStatus.STOPPED){
              myInfo.setState(TaskInfoState.success);
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          return myInfo;
        }

        public InstanceStatus waitForStatus(long maxWaitTime, long delay) throws RemoteException, InterruptedException {
          try {
            VirtualMachine vmCopy = findEntityByIdName(instance.getInstanceId(), VirtualMachine.class);
            final long startHere = System.currentTimeMillis();
            while (getInstanceStatus(vmCopy) != InstanceStatus.STOPPED && (System.currentTimeMillis() - shutdownStartTime) < SHUTDOWN_TIMEOUT) {
              if ((System.currentTimeMillis() - startHere) >= maxWaitTime) {
                break;
              }
              Thread.sleep(delay);
              vmCopy = findEntityByIdName(instance.getInstanceId(), VirtualMachine.class);
            }
            return getInstanceStatus(vmCopy);
          } catch (VmwareCheckedCloudException e) {
            throw new RemoteException(e.getMessage(), e);
          }
        }

        @Override
        public void cancelTask() throws RemoteException {
          // do nothing;
        }
      };
    } catch (RemoteException e) {
      LOG.info("Will attempt to force shutdown due to error: " + e.toString());
      try {
        return forceShutdown(vm);
      } catch (RemoteException e1) {
        throw new VmwareCheckedCloudException(e1);
      }
    }
  }

  private void guestShutdown(final VmwareCloudInstance instance, final VirtualMachine vm) throws RemoteException {
    try {
      vm.shutdownGuest();
    } catch (ToolsUnavailable e) {
      LOG.warn(String.format("Guest tools not installed or unavailable for '%s'", instance.getName()));
      throw e;
    } catch (InvalidState e) {
      final VirtualMachineRuntimeInfo runtime = vm.getRuntime();
      final String powerStateInfo = runtime==null ? "no runtime info" : runtime.getPowerState().name();
      LOG.warn(String.format("Invalid power state for '%s': %s", instance.getName(), powerStateInfo));
      throw e;
    } catch (TaskInProgress e) {
      LOG.warn(String.format("Already task in progress for '%s': '%s'", instance.getName(), e.getTask().getType()));
      throw e;
    } catch (RuntimeFault runtimeFault) {
      LOG.warn(String.format("Runtime fault in guest shutdown for '%s': '%s'", instance.getName(), runtimeFault.toString()));
      throw runtimeFault;
    }
  }

  private Task forceShutdown(@NotNull final VirtualMachine vm) throws RemoteException {
    return vm.powerOffVM_Task();
  }

  public void restartInstance(VmwareCloudInstance instance) throws VmwareCheckedCloudException {
    final VirtualMachine vm = findEntityByIdName(instance.getInstanceId(), VirtualMachine.class);
    try {
      vm.rebootGuest();
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
  }


  public boolean checkVirtualMachineExists(@NotNull final String vmName) {
    try {
      return findEntityByIdNameNullable(vmName, VirtualMachine.class, null) != null;
    } catch (VmwareCheckedCloudException e) {
      return false;
    }
  }

  public void processImageInstances(@NotNull final VmwareCloudImage image, @NotNull final VmwareInstanceProcessor processor) {
    try {
      final Map<String, VmwareInstance> instances = fetchInstances(image);
      for (VmwareInstance instance : instances.values()) {
        processor.process(instance);
      }
    } catch (VmwareCheckedCloudException e) {
      LOG.warnAndDebugDetails("Unable to process image instances", e);
    }
  }

  @NotNull
  public VmwareInstance getInstanceDetails(String instanceName) throws VmwareCheckedCloudException {
    final VirtualMachine entity = findEntityByIdName(instanceName, VirtualMachine.class);
    return new VmwareInstance(entity);
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

  @NotNull
  public InstanceStatus getInstanceStatus(@NotNull final VirtualMachine vm) {
    if (vm.getRuntime() == null || vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff) {
      return InstanceStatus.STOPPED;
    }
    if (vm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn) {
      return InstanceStatus.RUNNING;
    }
    return InstanceStatus.UNKNOWN;
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

  public void test() throws VmwareCheckedCloudException {
    getRootFolder();
  }

  @Nullable
  @Override
  public InstanceStatus getInstanceStatusIfExists(@NotNull final String instanceName) {
    try {
      final VirtualMachine vm = findEntityByIdNameNullable(instanceName, VirtualMachine.class, null);
      if (vm == null) {
        return null;
      }
      return getInstanceStatus(vm);
    } catch (VmwareCheckedCloudException e) {
      LOG.debug(e.toString());
      return InstanceStatus.ERROR;
    }
  }

  @NotNull
  public TypedCloudErrorInfo[] checkImage(@NotNull final VmwareCloudImage image) {
    final VmwareCloudImageDetails imageDetails = image.getImageDetails();
    final String vmName = imageDetails.getSourceVmName();
    try {
      final VirtualMachine vm = findEntityByIdNameNullable(vmName, VirtualMachine.class, null);
      if (vm == null){
        return new TypedCloudErrorInfo[]{new TypedCloudErrorInfo("NoVM", "No such VM: " + vmName)};
      }
      if (!imageDetails.getBehaviour().isUseOriginal() && !imageDetails.useCurrentVersion()) {
        final String snapshotName = imageDetails.getSnapshotName();
        final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vm);
        final String latestSnapshot = getLatestSnapshot(snapshotName, snapshotList);
        if (StringUtil.isNotEmpty(snapshotName) && latestSnapshot == null) {
          return new TypedCloudErrorInfo[]{new TypedCloudErrorInfo("NoSnapshot", "No such snapshot: " + snapshotName)};
        }
        image.updateActualSnapshotName(latestSnapshot);
        if (myInstancesProvider != null) {
          final Collection<VmwareCloudInstance> instances = image.getInstances();
          for (VmwareCloudInstance instance : instances) {
            if (!StringUtil.areEqual(instance.getSnapshotName(), latestSnapshot)) {
              myInstancesProvider.markInstanceExpired(instance);
            }
          }
        } else {
          LOG.debug("CloudInstancesProvider is null");
        }
      }
    } catch (VmwareCheckedCloudException e) {
      return new TypedCloudErrorInfo[]{TypedCloudErrorInfo.fromException(e)};
    }
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  public TypedCloudErrorInfo[] checkInstance(@NotNull final VmwareCloudInstance instance) {
    return new TypedCloudErrorInfo[0];
  }

  private static <T extends ManagedEntity> T getParentOfType(ManagedEntity entity, Class<T> parentType){
    while(entity != null){
      if (parentType.isAssignableFrom(entity.getClass())){
        return (T)entity;
      }
      entity = entity.getParent();
    }
    return null;
  }

  @Nullable
  private static String getTCServerDomain(){
    try {
      final String fqdn = InetAddress.getLocalHost().getCanonicalHostName();
      final Matcher matcher = FQDN_PATTERN.matcher(fqdn);
      if (matcher.matches()) {
        return matcher.group(1);
      } else {
        return null;
      }
    } catch (UnknownHostException ex){
      LOG.info("Unable to resolve FQDN. Linux hostname customization will be disabled: " + ex.toString());
      return null;
    }

  }

  private static Task successTask(){
    return new Task(null, null) {
      @Override
      public TaskInfo getTaskInfo() throws RemoteException {
        final TaskInfo taskInfo = new TaskInfo();
        taskInfo.setState(TaskInfoState.success);
        return taskInfo;
      }

      @Override
      public String waitForTask() throws RemoteException, InterruptedException {
        return Task.SUCCESS;
      }
    };
  }
}
