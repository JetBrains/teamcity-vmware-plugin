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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineRelocateDiskMoveOptions;
import com.vmware.vim25.mo.ResourcePool;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

/**
 * @author Sergey.Pak
 *         Date: 5/16/2014
 *         Time: 4:36 PM
 */
public class FakeModel {
  private static final FakeModel myInstance = new FakeModel();
  public static final ThreadFactory FAKE_MODEL_THREAD_FACTORY = r -> {
    Thread th = new Thread(r);
    th.setDaemon(true);
    return th;
  };

  public static FakeModel instance() {return myInstance;}

  private final ConcurrentMap<String, FakeFolder> myFolders = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ResourcePool> myResourcePools = new ConcurrentHashMap<String, ResourcePool>();
  private final ConcurrentMap<String, FakeVirtualMachine> myVms = new ConcurrentHashMap<String, FakeVirtualMachine>();
  private final ConcurrentMap<String, FakeDatacenter> myDatacenters = new ConcurrentHashMap<String, FakeDatacenter>();
  private final ConcurrentMap<String, CustomizationSpec> myCustomizationSpecs = new ConcurrentHashMap<>();
  private final List<Trinity<String, String, Long>> myEvents = new CopyOnWriteArrayList<>();

  public Map<String, FakeFolder> getFolders() {
    return myFolders;
  }

  public Map<String, ResourcePool> getResourcePools() {
    return myResourcePools;
  }

  public Map<String, FakeVirtualMachine> getVms() {
    return Collections.unmodifiableMap(myVms);
  }

  public Map<String, FakeDatacenter> getDatacenters() {
    return myDatacenters;
  }

  public Map<String, CustomizationSpec> getCustomizationSpecs() {
    return myCustomizationSpecs;
  }

  public FakeFolder getFolder(String name){
    return myFolders.get(name);
  }

  public ResourcePool getResourcePool(String name){
    return myResourcePools.get(name);
  }

  public List<Trinity<String, String, Long>> getEvents() {
    return myEvents;
  }

  public FakeVirtualMachine getVirtualMachine(String name){
    final FakeVirtualMachine vm = myVms.get(name);
    if (vm != null) {
      return vm;
    } else {
      return myVms.values().stream().filter(v->name.equals(v.getMOR().getVal())).findAny().orElse(null);
    }
  }

  public FakeDatacenter getDatacenter(String name){
    return myDatacenters.get(name);
  }

  public CustomizationSpec getCustomizationSpec(String name){
    return myCustomizationSpecs.get(name);
  }

  public FakeFolder addFolder(String name){
    final FakeFolder folder = new FakeFolder(name);
    myFolders.put(name, folder);
    return folder;
  }

  public void removeFolder(String name){
    myFolders.remove(name);
  }

  public FakeResourcePool addResourcePool(String name){
    final FakeResourcePool pool = new FakeResourcePool(name);
    myResourcePools.put(name, pool);
    return pool;
  }

  public void removeResourcePool(String name){
    myResourcePools.remove(name);
  }

  public FakeVirtualMachine addVM(String name){
    return addVM(name, false);
  }

  public FakeVirtualMachine addVM(String name, boolean isRunning, VirtualMachineCloneSpec spec){
    final FakeVirtualMachine vm = new FakeVirtualMachine(name, name.contains("template"), isRunning);
    putVM(name, vm);
    if (spec != null && spec.getLocation()!= null
        && VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name().equals(spec.getLocation().getDiskMoveType())){
      //((FakeVirtualMachine)vm).set
    }
    if (spec != null && spec.getConfig() != null) {
      final OptionValue[] extraConfig = spec.getConfig().getExtraConfig();
      if (extraConfig != null) {
        for (OptionValue optionValue : extraConfig) {
          vm.addCustomParam(optionValue.getKey(), String.valueOf(optionValue.getValue()));
        }
      }
    }

    return vm;
  }
  public FakeVirtualMachine addVM(String name, boolean isRunning){
    return addVM(name, isRunning, null);
  }

  public FakeVirtualMachine putVM(String name, FakeVirtualMachine vm){
    System.out.println("added VM " + name);
    myVms.put(name, vm);
    return vm;
  }

  public void removeVM(String name){
    System.out.println("Removed VM " + name);
    myVms.remove(name);
  }

  public void addVMSnapshot(String vmName, String snapshotName){
    final FakeVirtualMachine vm = myVms.get(vmName);
    if (vm == null)
      throw new IllegalArgumentException("Unable to find VM: " + vmName);

    vm.addSnapshot(snapshotName);
  }

  public void removeVmSnaphot(String vmName, String snapshotName){
    final FakeVirtualMachine vm = myVms.get(vmName);
    if (vm == null)
      throw new IllegalArgumentException("Unable to find VM: " + vmName);

    vm.removeSnapshot(snapshotName);
  }

  public FakeDatacenter addDatacenter(String dcName){
    final FakeDatacenter dc = new FakeDatacenter(dcName);
    myDatacenters.put(dcName, dc);
    return dc;
  }

  public void removeDatacenter(String dcName){
    myDatacenters.remove(dcName);
  }

  public void clear(){
    myFolders.clear();
    myVms.clear();
    myResourcePools.clear();
    myEvents.clear();
  }

  public void publishEvent(String entityName, String actionName){
    myEvents.add(Trinity.create(entityName, actionName, System.currentTimeMillis()));
  }
}
