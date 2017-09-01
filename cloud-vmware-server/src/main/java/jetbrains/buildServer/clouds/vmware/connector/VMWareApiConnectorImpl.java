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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import com.vmware.vim25.mo.util.MorUtil;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.vmware.*;
import jetbrains.buildServer.clouds.vmware.connector.beans.FolderBean;
import jetbrains.buildServer.clouds.vmware.connector.beans.ResourcePoolBean;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.util.StringUtil;
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
  private static final Pattern VM_PATTERN = Pattern.compile("vm-\\d+");

  private static final String FOLDER_TYPE = Folder.class.getSimpleName();
  private static final String RESPOOL_TYPE = ResourcePool.class.getSimpleName();
  private static final String SPEC_FOLDER = "vm";
  private static final String SPEC_RESPOOL = "Resources";


  private static final long SHUTDOWN_TIMEOUT = 60 * 1000;

  private final URL myInstanceURL;
  private final String myUsername;
  private final String myPassword;
  private ServiceInstance myServiceInstance;
  private final String myDomain;

  // short living cache
  private static final Cache<Pair<String, String>, String> MANAGED_ENTITIES_NAMES_CACHE = CacheBuilder.newBuilder()
                                                                                        .expireAfterWrite(20, TimeUnit.SECONDS)
                                                                                        .build();

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
    } else if (instanceType == VirtualMachine.class) {
      return VM_PATTERN.matcher(idName).matches();
    } else {
      return false;
    }
  }

  @Nullable
  protected <T extends ManagedEntity> T findEntityByIdNameNullableOld(@NotNull final String idName,
                                                                   @NotNull final Class<T> instanceType,
                                                                   @Nullable final Datacenter dc) throws VmwareCheckedCloudException {
    try {
      if (isId(idName, instanceType)) {
        ManagedObjectReference mor = new ManagedObjectReference();
        mor.setType(instanceType.getSimpleName());
        mor.setVal(idName);
        return createExactManagedEntity(mor);
      } else {
        return searchManagedEntity(idName, instanceType, dc);
      }
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
  }

  protected  <T extends ManagedEntity> T createExactManagedEntity(final ManagedObjectReference mor) {
    return (T)MorUtil.createExactManagedEntity(myServiceInstance.getServerConnection(), mor);
  }

  protected <T extends ManagedEntity> T searchManagedEntity(final @NotNull String idName,
                                                            final @NotNull Class<T> instanceType,
                                                            final @Nullable Datacenter dc)
    throws RemoteException, VmwareCheckedCloudException {
    if (dc == null) {
      return (T)new InventoryNavigator(getRootFolder()).searchManagedEntity(instanceType.getSimpleName(), idName);
    } else {
      return (T)new InventoryNavigator(dc).searchManagedEntity(instanceType.getSimpleName(), idName);
    }
  }

  @NotNull
  protected <T extends ManagedEntity> Pair<T,Datacenter> findEntityByIdNameOld(String idName, Class<T> instanceType) throws VmwareCheckedCloudException  {
    final AtomicReference<VmwareCheckedCloudException> exceptionRef = new AtomicReference<>();
    final Optional<Pair<T, Datacenter>> any = findAllEntitiesOld(Datacenter.class)
      .stream()
      .map(
        dc -> {
          try {
            final T e = findEntityByIdNameNullableOld(idName, instanceType, dc);
            return (e != null) ? Pair.create(e, dc) : null;
          } catch (VmwareCheckedCloudException e) {
            LOG.warnAndDebugDetails("An exception while searching", e);
            exceptionRef.set(e);
            return null;
          }
        })
      .filter(Objects::nonNull)
      .findAny();
    if (exceptionRef.get() != null) {
      throw exceptionRef.get();
    }

    if (!any.isPresent() ) {
      throw new VmwareCheckedCloudException(String.format("Unable to find %s '%s'", instanceType.getSimpleName(), idName));
    }
    return any.get();
  }

  protected Collection<VmwareInstance> findAllVirtualMachines() throws VmwareCheckedCloudException {
    final AtomicReference<VmwareCheckedCloudException> exceptionRef = new AtomicReference<>();
    final Collection<VmwareInstance> result = findWithDatacenter(dc -> {
      final String datacenterId = dc.getMOR().getVal();
      try {
        final ObjectContent[] ocs = getObjectContents(dc, new String[][]{{
          "VirtualMachine", "name", "config.extraConfig", "config.template" , "config.changeVersion"
          , "runtime.powerState", "runtime.bootTime",
          "guest.ipAddress", "parent"
        },});
        if (ocs == null){
          return Stream.empty();
        }
        return Arrays.stream(ocs)
                     .map(oc->{
            final Map<String, Object> mappedProperties = Arrays.stream(oc.getPropSet()).collect(Collectors.toMap(
              DynamicProperty::getName, DynamicProperty::getVal
            ));

          final String vmName = String.valueOf(mappedProperties.get("name"));
          try {
            return new VmwareInstance(
              vmName,
              oc.getObj().getVal(),
              ((ArrayOfOptionValue)mappedProperties.get("config.extraConfig")).getOptionValue(),
              (VirtualMachinePowerState)mappedProperties.get("runtime.powerState"),
              (Boolean)mappedProperties.get("config.template"),
              String.valueOf(mappedProperties.get("config.changeVersion")),
              (Calendar)mappedProperties.get("runtime.bootTime"),
              (String)mappedProperties.get("guest.ipAddress"),
              (ManagedObjectReference)mappedProperties.get("parent"),
              datacenterId
            );
          } catch (Exception ex) {
            LOG.debug("Unable to process VM with name '" + vmName + "'. Not all properties are available");
            return null;
          }}).filter(Objects::nonNull);
      } catch (RemoteException e) {
        LOG.warnAndDebugDetails("An error occurred while searching for all folders", e);
        exceptionRef.set(new VmwareCheckedCloudException(e));
        return Stream.empty();
      }
    });
    if (exceptionRef.get() != null){
      throw exceptionRef.get();
    }
    LOG.debug(
      String.format("[%s]. All instances: [%s]"
        , myProfileId, String.join(",", result
          .stream()
          .map(VmwareInstance::getName).collect(Collectors.toList())
        )
      )
    );
    return result;
  }

  protected Map<String, VmwareInstance> findAllVirtualMachinesAsMap() throws VmwareCheckedCloudException{
    return findAllVirtualMachines()
      .stream()
      .collect(Collectors.toMap(VmwareInstance::getName, Function.identity(), (k, v) -> k));
  }

  @NotNull
  protected VmwareInstance findVirtualMachineOrThrowException(String vmName) throws VmwareCheckedCloudException {
    final VmwareInstance vmwareInstance = findAllVirtualMachinesAsMap().get(vmName);
    if (vmwareInstance == null) {
      throw new VmwareCheckedCloudException(String.format("Unable to find VirtualMachine by name '%s'", vmName));
    }
    return vmwareInstance;
  }

  protected Collection<FolderBean> findAllFolders() throws VmwareCheckedCloudException {
    final AtomicReference<VmwareCheckedCloudException> exceptionRef = new AtomicReference<>();
    final Collection<FolderBean> result = findWithDatacenter(dc -> {
      try {
        final ObjectContent[] ocs = getObjectContents(dc, new String[][]{{
          "Folder", "name", "childType", "parent"
        },});
        if (ocs == null){
          return Stream.empty();
        }
        final String datacenterId = dc.getMOR().getVal();
        return Arrays.stream(ocs).map(oc -> {
          try {
            final Map<String, Object> mappedProperties = Arrays.stream(oc.getPropSet()).collect(Collectors.toMap(
              DynamicProperty::getName, DynamicProperty::getVal
            ));

            final String simpleName = String.valueOf(mappedProperties.get("name"));
            final ManagedObjectReference parent = (ManagedObjectReference)mappedProperties.get("parent");

            LOG.debug("Found folder with name '" + simpleName + "'. Parent: " + (parent == null ? "null" : parent.toString()));

            final String[] childTypes = ((ArrayOfString)mappedProperties.get("childType")).getString();
            boolean skip = true;
            for (String childType : childTypes) {
              if (VM_TYPE.equals(childType)) {
                skip = false;
                break;
              }
            }
            if (skip) {
              LOG.debug("The folder cannot contain VMs. Skipping it...");
              return null;
            }

            final String fullFolderPath = getFullPath(simpleName, oc.obj, parent, dc);
            LOG.debug("Calculated path: " + fullFolderPath);

            return new FolderBean(oc.obj,
                                  simpleName,
                                  fullFolderPath,
                                  childTypes,
                                  parent,
                                  datacenterId
            );
          } catch (Exception ex) {
            LOG.warnAndDebugDetails("Error getting folder details", ex);
            return null;
          }
        });
      } catch (RemoteException e) {
        LOG.warnAndDebugDetails("An error occurred while searching for all folders", e);
        exceptionRef.set(new VmwareCheckedCloudException(e));
        return Stream.empty();
      }
    });

    return result;
  }

  protected Collection<ResourcePoolBean> findAllResourcePools() throws VmwareCheckedCloudException {
    final AtomicReference<VmwareCheckedCloudException> exceptionRef = new AtomicReference<>();
    final Collection<ResourcePoolBean> result = findWithDatacenter(dc -> {
      try {
        final String datacenterId = dc.getMOR().getVal();
        final ObjectContent[] ocs = getObjectContents(dc, new String[][]{{"ResourcePool", "name", "parent"},});
        if (ocs == null){
          return Stream.empty();
        }
        return Arrays.stream(ocs).map(oc -> {
          final Map<String, Object> mappedProperties = Arrays.stream(oc.getPropSet()).collect(Collectors.toMap(
            DynamicProperty::getName, DynamicProperty::getVal
          ));
          final String simpleName = String.valueOf(mappedProperties.get("name"));
          final ManagedObjectReference parent = (ManagedObjectReference)mappedProperties.get("parent");
          LOG.debug("Found respool with name '" + simpleName + "'. Parent: " + (parent == null ? "null" : parent.toString()));

          final ResourcePool pool =  (ResourcePool)createExactManagedEntity(oc.getObj());

          final String path = getFullPath(simpleName, oc.obj, parent, dc);
          LOG.debug("Calculated path: " + path);

          return new ResourcePoolBean(oc.obj,
                                      simpleName,
                                      path,
                                      parent,
                                      datacenterId
          );
        });
      } catch (RemoteException e) {
        LOG.warnAndDebugDetails("An error occurred while searching for all resource pools", e);
        exceptionRef.set(new VmwareCheckedCloudException(e));
        return Stream.empty();
      }
    });
    if (exceptionRef.get() != null){
      throw exceptionRef.get();
    }
    return result;
  }

  //protected 4 tests
  protected ObjectContent[] getObjectContents(final Datacenter dc, final String[][] typeinfo) throws RemoteException {
    return new InventoryNavigator(dc).retrieveObjectContents(typeinfo, true);
  }

  private <T extends VmwareManagedEntity> Collection<T> findWithDatacenter(
    Function<Datacenter, Stream<T>> mapper) throws VmwareCheckedCloudException {
    return findAllEntitiesOld(Datacenter.class).stream().flatMap(mapper).filter(Objects::nonNull).collect(Collectors.toList());
  }

  protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(Class<T> instanceType) throws VmwareCheckedCloudException  {
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

  protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(Class<T> instanceType) throws VmwareCheckedCloudException  {
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
  public List<VmwareInstance> getVirtualMachines(boolean filterClones) throws VmwareCheckedCloudException {
    final Collection<VmwareInstance> allVms = findAllVirtualMachines();
    return allVms.stream()
                 .filter(vm -> vm.isInitialized() && (!filterClones || !vm.isClone()))
                 .sorted()
                 .collect(Collectors.toList());
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

    final Map<String, VmwareInstance> allVmsAsMap = findAllVirtualMachinesAsMap();

    for (VmwareCloudImage image: images) {
      final VmwareCloudImageDetails imageDetails = image.getImageDetails();
      if(imageDetails.getBehaviour().isUseOriginal()){
        final VmwareInstance vmInstance = allVmsAsMap.get(imageDetails.getSourceVmName());
        if (vmInstance == null){
          throw new VmwareCheckedCloudException(String.format("Unable to find VirtualMachine '%s'", imageDetails.getSourceVmName()));
        }
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

    for (VmwareInstance vmInstance : allVmsAsMap.values()) {
      try {
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
            result.put(image, imageInstancesMap);
          }
          imageInstancesMap.put(vmInstance.getName(), (R)vmInstance);
        }
      } catch (Exception ex) {
        LOG.debug("Unable to process VirtualMachine" + vmInstance.getId());
      }
    }

    return result;
  }

  @NotNull
  @Override
  public Map<String, String> getCustomizationSpecs() {
    final Map<String,String> retval = new HashMap<>();
    try {
      final CustomizationSpecManager specManager = myServiceInstance.getCustomizationSpecManager();
      if (specManager == null)
        return retval;
      final CustomizationSpecInfo[] specs = specManager.getInfo();
      if (specs != null) {
        for (CustomizationSpecInfo spec : specs) {
          retval.put(spec.getName(), spec.getType());
        }
      }
    } catch (Exception ex){
      LOG.warnAndDebugDetails("Can't get customization specs", ex);
    }
    return retval;
  }

  @Override
  public CustomizationSpec getCustomizationSpec(final String name) throws VmwareCheckedCloudException {
    final CustomizationSpecManager specManager = myServiceInstance.getCustomizationSpecManager();
    if (specManager == null){
      throw new VmwareCheckedCloudException("Customization Spec in not available: '" + name + "'");
    }
    try {
      return specManager.getCustomizationSpec(name).getSpec();
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException("Unable to get Customization Spec: '" + name + "'" , e);
    }
  }

  @Used("Tests")
  public Map<String, String> getVMParams(@NotNull final String vmName) throws VmwareCheckedCloudException {
    return findVirtualMachineOrThrowException(vmName).getProperties();
  }

  @NotNull
  public List<FolderBean> getFolders() throws VmwareCheckedCloudException {
    final Collection<FolderBean> allFolders = findAllFolders();

    return allFolders.stream().filter(this::canContainVMs).collect(Collectors.toList());
  }

  @NotNull
  public List<ResourcePoolBean> getResourcePools() throws VmwareCheckedCloudException {
    final Collection<ResourcePoolBean> pools = findAllResourcePools();

    return pools.stream()
                .sorted()
                .collect(Collectors.toList());
  }


  private boolean canContainVMs(final FolderBean folder) {
    final String[] childTypes = folder.getChildType();
    for (String childType : childTypes) {
      if (VM_TYPE.equals(childType)) {
        return true;
      }
    }
    return false;
  }

  private String getFullPath(@NotNull final String entityName,
                             @NotNull final ManagedObjectReference mor,
                             @Nullable final ManagedObjectReference firstParent,
                             @Nullable final Datacenter dc){
    final String uniqueName = String.format("%s (%s)", entityName, mor.getVal());
    if (firstParent == null) {
      return uniqueName;
    }
    try {
      final String morPath = getFullMORPath(createExactManagedEntity(firstParent), dc);
      if (StringUtil.isEmpty(morPath)) {
        return uniqueName;
      } else if (("Resources".equals(entityName) || "vm".equals(entityName)) && !mor.getType().equals(firstParent.getType())) {
        LOG.debug("The pool is a special pool. Skipping it...");
        return morPath;
      } else {
        return morPath + "/" + entityName;
      }
    } catch (Exception ex){
      LOG.warnAndDebugDetails("Can't calculate full path for " + uniqueName, ex);
      return uniqueName;
    }
  }

  @Nullable
  private String getFullFolderPath(final ManagedObjectReference mor, final Datacenter dc) {
    ManagedEntity entity;
    try {
      entity = findEntityByIdNameNullableOld(mor.getVal(), Folder.class, dc);
      if (entity != null) {
        return getFullMORPath(entity, dc);
      } else {
        return null;
      }
    } catch (VmwareCheckedCloudException e) {
      return mor.getVal();
    }
  }

  @Nullable
  private String getResourcePoolPath(final ManagedObjectReference mor, final Datacenter dc) {
    ManagedEntity entity;
    try {
      entity = findEntityByIdNameNullableOld(mor.getVal(), ResourcePool.class, dc);
      if (entity != null) {
        return getFullMORPath(entity, dc);
      } else {
        return null;
      }
    } catch (VmwareCheckedCloudException e) {
      return mor.getVal();
    }
  }

  private String getFullMORPath(@NotNull final ManagedEntity entity, @Nullable final Datacenter dc) {
    final ManagedObjectReference mor = entity.getMOR();
    final Pair<String, String> morPair = Pair.create(mor.getType(), mor.getVal());
    final String existingPath = MANAGED_ENTITIES_NAMES_CACHE.getIfPresent(morPair);
    if (existingPath != null)
      return existingPath;
    final ManagedEntity parent = entity.getParent();
    final String entityName = entity.getName();
    boolean skipName =
      (mor.getType().equals(FOLDER_TYPE) && (entityName.equals(SPEC_FOLDER) || !FOLDER_PATTERN.matcher(morPair.getSecond()).matches() )) ||
      (mor.getType().equals(RESPOOL_TYPE) && entityName.equals(SPEC_RESPOOL));

    if (parent == null){
      final String name = skipName ? "" : entityName;
      MANAGED_ENTITIES_NAMES_CACHE.put(morPair, name);
      return name;
    } else {
      final String fullMORPath = getFullMORPath(parent, dc);
      final String delimiter = fullMORPath.isEmpty() ? "" : "/";
      final String name = skipName ? fullMORPath : fullMORPath + delimiter + entityName;
      MANAGED_ENTITIES_NAMES_CACHE.put(morPair, name);
      return name;
    }
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
    return getSnapshotList(findEntityByIdNameOld(vmName, VirtualMachine.class).getFirst());
  }

  @Nullable
  public String getLatestSnapshot(@NotNull final String vmName, @NotNull final String snapshotNameMask) throws VmwareCheckedCloudException {
    if (VmwareConstants.CURRENT_STATE.equals(snapshotNameMask)){
      return VmwareConstants.CURRENT_STATE;
    }
    final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vmName);
    return getLatestSnapshot(snapshotNameMask, snapshotList);
  }

  private boolean containsDuplicates(Collection<? extends VmwareManagedEntity> entities){
    final Set<String> names = new HashSet<String>();
    for (VmwareManagedEntity entity : entities) {
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
    final VirtualMachine vm = findEntityByIdNameOld(instance.getInstanceId(), VirtualMachine.class).getFirst();
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
    final VirtualMachine vm = findEntityByIdNameOld(instance.getInstanceId(), VirtualMachine.class).getFirst();
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

    final Pair<VirtualMachine, Datacenter> pair = findEntityByIdNameOld(imageDetails.getSourceVmName(), VirtualMachine.class);
    final VirtualMachine vm = pair.getFirst();
    final Datacenter datacenter = pair.getSecond();

    final VirtualMachineConfigSpec config = new VirtualMachineConfigSpec();
    final VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
    final VirtualMachineRelocateSpec location = new VirtualMachineRelocateSpec();

    cloneSpec.setPowerOn(true);
    cloneSpec.setLocation(location);
    cloneSpec.setConfig(config);
    final boolean disableOsCustomization = TeamCityProperties.getBoolean(VmwareConstants.DISABLE_OS_CUSTOMIZATION);
    if (!VmwareConstants.DEFAULT_RESOURCE_POOL.equals(imageDetails.getResourcePoolId())) {
      final ResourcePool pool = findEntityByIdNameNullableOld(imageDetails.getResourcePoolId(), ResourcePool.class, datacenter);
      if (pool != null) {
          location.setPool(pool.getMOR());
      } else {
        LOG.warn(String.format("Unable to find resource pool %s at datacenter %s. Will clone at the image resource pool instead"
          , imageDetails.getResourcePoolId()
          , datacenter == null? "<not provided>":  datacenter.getName()));
      }
    }
    final Map<String, VirtualMachineSnapshotTree> snapshotList = getSnapshotList(vm);
    final VmwareSourceState sourceState = instance.getSourceState();
    if (imageDetails.useCurrentVersion() || StringUtil.isEmptyOrSpaces(sourceState.getSnapshotName())) {
      LOG.info("Snapshot name is not specified. Will clone latest VM state");
    } else {
      final VirtualMachineSnapshotTree obj = snapshotList.get(sourceState.getSnapshotName());
      final ManagedObjectReference snapshot = obj == null ? null : obj.getSnapshot();
      cloneSpec.setSnapshot(snapshot);
      if (snapshot != null) {
        if (TeamCityProperties.getBooleanOrTrue(VmwareConstants.USE_LINKED_CLONE)) {
          LOG.info("Using linked clone. Snapshot name: " + sourceState.getSnapshotName());
          location.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name());
        } else {
          LOG.info("Using full clone. Snapshot name: " + sourceState.getSnapshotName());
        }
      } else {
        final String errorText = "Unable to find snapshot " + sourceState.getSnapshotName();
        throw new VmwareCheckedCloudException(errorText);
      }
    }

    final VirtualMachineConfigInfo vmConfig = vm.getConfig();
    config.setExtraConfig(new OptionValue[]{
      createOptionValue(TEAMCITY_VMWARE_CLONED_INSTANCE, "true"),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME, imageDetails.getSourceVmName()),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SOURCE_ID, imageDetails.getSourceId()),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SOURCE_VM_ID, vm.getMOR().getVal()),
      createOptionValue(TEAMCITY_VMWARE_IMAGE_SNAPSHOT, sourceState.getSnapshotName()),
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
      final Folder folder = findEntityByIdNameNullableOld(imageDetails.getFolderId(), Folder.class, datacenter);
      if (folder != null) {
        return vm.cloneVM_Task(folder, instance.getName(), cloneSpec);
      } else {
        String dcName = datacenter == null ? "root" : datacenter.getName();
        throw new VmwareCheckedCloudException(
          String.format("Unable to find folder %s in datacenter %s", imageDetails.getFolderId(), dcName)
        );
      }
    } catch (RemoteException e) {
      instance.setStatus(InstanceStatus.ERROR);
      throw new VmwareCheckedCloudException(e);
    }
  }

  /**
   * checks whether user has a certain privilege on a certain resource.
   * @param pool
   * @param instanceType
   * @param permission
   * @return whether user has a certain privilege on a certain resource. The result is false positive, i.e. can return true, when the permission existence cannot be checked.
   */
  public <T extends ManagedEntity> boolean hasPrivilegeOnResource(@NotNull final String entityId,
                                                                  @NotNull final Class<T> instanceType,
                                                                  @NotNull final String permission) throws VmwareCheckedCloudException {
    final Pair<T, Datacenter> pair = findEntityByIdNameOld(entityId, instanceType);
    if (pair.getFirst() == null){
      return true;
    }

    final Set<Integer> rolesSet = new HashSet<>();
    final int[] role = pair.getFirst().getEffectiveRole();
    if (role == null) {
      return true; // don't perform the check
    }

    for(int roleId : role){
      rolesSet.add(roleId);
    }

    final AuthorizationManager authorizationManager = myServiceInstance.getAuthorizationManager();
    if (authorizationManager == null)
      return true; // don't perform the check

    final AuthorizationRole[] roleList = authorizationManager.getRoleList();
    if (roleList == null)
      return true; // don't perform the check

    for (AuthorizationRole authRole : roleList) {
      if (!rolesSet.contains(authRole.getRoleId())) {
        continue;
      }
      for (String p : authRole.getPrivilege()) {
        if (p.equalsIgnoreCase(permission)) {
          return true;
        }
      }
    }

    // can't add
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

  private OptionValue createOptionValue(@NotNull final String key, @Nullable final String value) {
    final OptionValue optionValue = new OptionValue();
    optionValue.setKey(key);
    optionValue.setValue(value == null ? "" : value);
    return optionValue;
  }

  public Task stopInstance(@NotNull final VmwareCloudInstance instance) {
    instance.setStatus(InstanceStatus.STOPPING);
    try {
      final VirtualMachine vm = findEntityByIdNameNullableOld(instance.getInstanceId(), VirtualMachine.class, null);
      if (vm == null){
        // VM no longer exists TW-47486
        instance.getImage().removeInstance(instance.getInstanceId());
        return emptyTask();
      }
      if (getInstanceStatus(vm) == InstanceStatus.STOPPED) {
        return emptyTask();
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
          //TODO rework
          try {
            VirtualMachine vmCopy = findEntityByIdNameOld(instance.getInstanceId(), VirtualMachine.class).getFirst();
            final long startHere = System.currentTimeMillis();
            while (getInstanceStatus(vmCopy) != InstanceStatus.STOPPED && (System.currentTimeMillis() - shutdownStartTime) < SHUTDOWN_TIMEOUT) {
              if ((System.currentTimeMillis() - startHere) >= maxWaitTime) {
                break;
              }
              Thread.sleep(delay);
              vmCopy = findEntityByIdNameOld(instance.getInstanceId(), VirtualMachine.class).getFirst();
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

  @Override
  public Task deleteInstance(final VmwareCloudInstance instance) {
    LOG.info("Will delete instance " + instance.getName());
    try {
      final VirtualMachine vm = findEntityByIdNameOld(instance.getInstanceId(), VirtualMachine.class).getFirst();
      return vm.destroy_Task();
    } catch (Exception e) {
      // stacktrace goes to SDK details, so no value of dumping it to log here
      LOG.warnAndDebugDetails("An error occured during deleting instance " + instance.getName(), e);
      instance.updateErrors(TypedCloudErrorInfo.fromException(e));
    }
    return emptyTask();
  }

  public void restartInstance(VmwareCloudInstance instance) throws VmwareCheckedCloudException {
    final VirtualMachine vm = findEntityByIdNameOld(instance.getInstanceId(), VirtualMachine.class).getFirst();
    try {
      vm.rebootGuest();
    } catch (RemoteException e) {
      throw new VmwareCheckedCloudException(e);
    }
  }


  public boolean checkVirtualMachineExists(@NotNull final String vmName) {
    try {
      return findEntityByIdNameNullableOld(vmName, VirtualMachine.class, null) != null;
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
    return findVirtualMachineOrThrowException(instanceName);
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

  @NotNull
  @Override
  public String getKey() {
    return getKey(myInstanceURL, myUsername, myPassword);
  }

  @NotNull
  @Override
  public Map<String, InstanceStatus> getInstanceStatusesIfExists(@NotNull Set<String> instanceNames) {
  try {
    return findAllVirtualMachines()
      .stream()
      .filter(vm->instanceNames.contains(vm.getName()))
      .collect(Collectors.toMap(VmwareInstance::getName, VmwareInstance::getInstanceStatus, (k,v)->k));
    } catch (VmwareCheckedCloudException e) {
      LOG.debug(e.toString());
      return instanceNames.stream().collect(Collectors.toMap(Function.identity(), in-> InstanceStatus.ERROR));
    }
  }

  @NotNull
  public TypedCloudErrorInfo[] checkImage(@NotNull final VmwareCloudImage image) {
    final VmwareCloudImageDetails imageDetails = image.getImageDetails();
    final String vmName = imageDetails.getSourceVmName();
    try {
      final VirtualMachine vm = findEntityByIdNameNullableOld(vmName, VirtualMachine.class, null);
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
        final VmwareSourceState actualState = VmwareSourceState.from(latestSnapshot, vm.getMOR().getVal());
        image.updateActualSourceState(actualState);
        if (myInstancesProvider != null) {
          final Collection<VmwareCloudInstance> instances = image.getInstances();
          for (VmwareCloudInstance instance : instances) {
            final VmwareSourceState vmSourceState = instance.getSourceState();
            if (!actualState.equals(vmSourceState)) {
              LOG.info("marking instance expired: " + actualState.getDiffMessage(vmSourceState));
              myInstancesProvider.markInstanceExpired(image.getProfile(), instance);
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

  private static Task emptyTask(){
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


  public static String getKey(@NotNull final URL serverUrl, @NotNull final String username, @NotNull final String pwd){
    return String.format("%s_%s%s", serverUrl.toString().toLowerCase(), username.toLowerCase(), EncryptUtil.scramble(pwd));
  }
}
