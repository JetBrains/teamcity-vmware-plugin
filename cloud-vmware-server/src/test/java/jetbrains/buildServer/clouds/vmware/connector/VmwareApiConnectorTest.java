/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware.connector;

import com.intellij.openapi.util.Pair;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.vmware.connector.beans.ResourcePoolBean;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by sergeypak on 18/04/2017.
 */
@Test
public class VmwareApiConnectorTest extends BaseTestCase {

  private Mockery m;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    m = new Mockery();
  }

  public void allow_same_vm_names() throws Exception {
    final VMWareApiConnector connector = new VMWareApiConnectorImpl(new URL("http://localhost:9999"), "username", "pwd", null, null, null, null){
      @NotNull
      @Override
      public Collection<VmwareInstance> findAllVirtualMachines() {
        final ArrayList<VmwareInstance> retval = new ArrayList<>();
        final ManagedObjectReference parent = new ManagedObjectReference();
        parent.setType("Folder");
        parent.setVal("folder-40");
        retval.add(new VmwareInstance("VM1", "VM1", new OptionValue[0],
                                      VirtualMachinePowerState.poweredOff, false,
                                      "changeVersion", null, null, parent, "datacenter-21"));
        retval.add(new VmwareInstance("VM1", "VM1", new OptionValue[0],
                                      VirtualMachinePowerState.poweredOff, false,
                                      "changeVersion", null, null, parent, "datacenter-21"));
        return retval;
      }
    };


    final List<VmwareInstance> vms = connector.getVirtualMachines(false);
    assertEquals(2, vms.size());
    assertEquals("VM1", vms.get(0).getPath());
    assertEquals("VM1", vms.get(1).getPath());
  }

  public void allow_same_respool_names() throws Exception{
    final VMWareApiConnector connector = new VMWareApiConnectorImpl(new URL("http://localhost:9999"), "username", "pwd", null, null, null, null){


      private final Map<String, ManagedEntity> myEntitiesMap = new HashMap<>();

      private final Datacenter myDc = new Datacenter(null, null){
        @Override
        public ManagedObjectReference getMOR() {
          ManagedObjectReference mor = new ManagedObjectReference();
          mor.setType("Datacenter");
          mor.setVal("datacenter-2");
          return mor;
        }
      };

      {
        myEntitiesMap.put("datacenter-2", myDc);
        final Folder folder = createEntity(Folder.class, null, "group-v1", "MyFolder");
        myEntitiesMap.put("group-v1", folder);
        final ResourcePool respool2 = createEntity(ResourcePool.class, folder, "resgroup-2", "MyRespool");
        myEntitiesMap.put("resgroup-2", respool2);
        final ResourcePool respool3 = createEntity(ResourcePool.class, folder, "resgroup-3", "MyRespool");
        myEntitiesMap.put("resgroup-3", respool3);
      }


      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        if (instanceType == Datacenter.class){
          return Arrays.asList((T)myDc);
        }
        return null;
      }

      @Override
      protected <T extends ManagedEntity> T createExactManagedEntity(final ManagedObjectReference mor) {
        return (T)myEntitiesMap.get(mor.getVal());
      }

      @Override
      protected <T extends ManagedEntity> T searchManagedEntity(@NotNull final String idName,
                                                                @NotNull final Class<T> instanceType,
                                                                @Nullable final Datacenter dc) {
        return (T)myEntitiesMap.get(idName);
      }

      @Override
      protected ObjectContent[] getObjectContents(final Datacenter dc, final String[][] typeinfo) throws RemoteException {
        assert dc == myDc;
        final ObjectContent[] retval = new ObjectContent[2];
        //"ResourcePool", "name", "parent"
        ManagedObjectReference parentMOR = new ManagedObjectReference();
        parentMOR.setType("Folder");
        parentMOR.setVal("group-v1");
        retval[0] = createObjectContent("ResourcePool", "resgroup-2", Pair.create("name", "MyRespool"), Pair.create("parent", parentMOR));
        retval[1] = createObjectContent("ResourcePool", "resgroup-3", Pair.create("name", "MyRespool"), Pair.create("parent", parentMOR));
        return retval;
      }
    };

    final List<ResourcePoolBean> pools = connector.getResourcePools();
    assertEquals(2, pools.size());
    {
      ResourcePoolBean pool1 = pools.get(0);
      assertEquals("resgroup-2", pool1.getId());
      assertEquals("MyFolder/MyRespool", pool1.getPath());
    }

    {
      ResourcePoolBean pool2 = pools.get(1);
      assertEquals("resgroup-3", pool2.getId());
      assertEquals("MyFolder/MyRespool", pool2.getPath());
    }
  }


  private static ObjectContent createObjectContent(final String type, final String value, Pair<String, Object>... props){
    final ObjectContent oc = new ObjectContent();
    final ManagedObjectReference mor = new ManagedObjectReference();
    mor.setType(type);
    mor.setVal(value);
    oc.setObj(mor);
    final List<DynamicProperty> properties = Stream
      .of(props)
      .map(p -> {
        DynamicProperty prop = new DynamicProperty();
        prop.setName(p.getFirst());
        prop.setVal(p.second);
        return prop;
      })
      .collect(Collectors.toList());
    oc.setPropSet(properties.toArray(new DynamicProperty[properties.size()]));
    return oc;
  }

  private <T extends ManagedEntity> T createEntity(Class<T> type, @Nullable ManagedEntity parent, String val, String name){
    final ManagedObjectReference mor = new ManagedObjectReference();
    mor.setType(type.getSimpleName());
    mor.setVal(val);
    if (type==Folder.class){
      return (T)new Folder(null, mor){
        @Override
        public ManagedEntity getParent() {
          return parent;
        }

        @Override
        public String getName() {
          return name;
        }
      };
    }
    if (type==ResourcePool.class){
      return (T)new ResourcePool(null, mor){
        @Override
        public ManagedEntity getParent() {
          return parent;
        }

        @Override
        public String getName() {
          return name;
        }
      };
    }
    throw new IllegalArgumentException("can't create instance of type " + type.getSimpleName());
  }
}
