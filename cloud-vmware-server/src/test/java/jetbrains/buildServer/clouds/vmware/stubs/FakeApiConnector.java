/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.connector.beans.FolderBean;
import jetbrains.buildServer.clouds.vmware.connector.beans.ResourcePoolBean;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 2:40 PM
 */
public class FakeApiConnector extends VMWareApiConnectorImpl {

  public FakeApiConnector(@Nullable String serverUUID, @Nullable String profileId) throws MalformedURLException {
    this(serverUUID, profileId, null);
  }

  public FakeApiConnector(@Nullable String serverUUID, @Nullable String profileId, @Nullable CloudInstancesProvider instancesProvider) throws MalformedURLException {
    super(new URL("http://localhost:9999"), "", "", serverUUID, profileId, instancesProvider, null);
  }

  @Override
  public void test() throws VmwareCheckedCloudException {

  }

  @Override
  protected <T extends ManagedEntity> T findEntityByIdNameNullableOld(final String name, final Class<T> instanceType, Datacenter dc) throws VmwareCheckedCloudException {
    test();
    final T t;
    if (instanceType == Folder.class){
      t = (T)FakeModel.instance().getFolder(name);
    } else if (instanceType == ResourcePool.class){
      t =  (T)FakeModel.instance().getResourcePool(name);
    } else if (instanceType == VirtualMachine.class){
      t =  (T)FakeModel.instance().getVirtualMachine(name);
    } else if (instanceType == Datacenter.class){
      t =  (T)FakeModel.instance().getDatacenter(name);
    } else {
      throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
    }
    return  (dc == null || dc == getParentDC(t)) ? t : null;
  }

  @Override
  protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
    test();
    if (instanceType == Folder.class){
      return (Collection<T>)FakeModel.instance().getFolders().values();
    } else if (instanceType == ResourcePool.class){
      return (Collection<T>)FakeModel.instance().getResourcePools().values();
    } else if (instanceType == VirtualMachine.class){
      return (Collection<T>)FakeModel.instance().getVms().values();
    } else if (instanceType == Datacenter.class) {
      return (Collection<T>)FakeModel.instance().getDatacenters().values();
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  @Override
  protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
    test();
    if (instanceType == Folder.class){
      return (Map<String, T>)FakeModel.instance().getFolders();
    } else if (instanceType == ResourcePool.class){
      return (Map<String, T>)FakeModel.instance().getResourcePools();
    } else if (instanceType == VirtualMachine.class){
      return (Map<String, T>)FakeModel.instance().getVms();
    } else if (instanceType == Datacenter.class) {
      return (Map<String, T>)FakeModel.instance().getDatacenters();
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  @Override
  protected Map<String, VirtualMachine> searchVMsByNames(@NotNull final Collection<String> names, @Nullable final Datacenter dc) throws VmwareCheckedCloudException {
    return names.stream().map(name -> FakeModel.instance().getVirtualMachine(name)).collect(Collectors.toMap(VirtualMachine::getName,Function.identity()));
  }

  @NotNull
  @Override
  public Collection<VmwareInstance> findAllVirtualMachines() throws VmwareCheckedCloudException {
    return findAllEntitiesAsMapOld(VirtualMachine.class)
      .entrySet().stream()
      .map(e->new VmwareInstance(e.getValue(), "datacenter-10"))
      .collect(Collectors.toList());
  }

  public Map<String, VmwareInstance> getAllVMsMap(boolean filterClones) throws VmwareCheckedCloudException {
    return getVirtualMachines(filterClones).stream().collect(Collectors.toMap(VmwareInstance::getName, Function.identity()));
  }

  @NotNull
  @Override
  public List<FolderBean> getFolders() throws VmwareCheckedCloudException {
    return findAllEntitiesAsMapOld(Folder.class)
      .entrySet().stream()
      .map(e->new FolderBean(e.getValue()))
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<ResourcePoolBean> getResourcePools() throws VmwareCheckedCloudException {
    return findAllEntitiesAsMapOld(ResourcePool.class)
      .entrySet().stream()
      .map(e->new ResourcePoolBean(e.getValue()))
      .collect(Collectors.toList());
  }

  @Override
  public CustomizationSpec getCustomizationSpec(final String name) throws VmwareCheckedCloudException {
    final CustomizationSpec spec = FakeModel.instance().getCustomizationSpec(name);
    if (spec == null){
      throw new VmwareCheckedCloudException("Unable to get Customization Spec '" + name + "'");
    }
    return spec;
  }

  private static FakeDatacenter getParentDC(ManagedEntity me){
    while (!(me ==null || me instanceof Datacenter)){
      me = me.getParent();
    }
    return (FakeDatacenter) me;
  }
}
