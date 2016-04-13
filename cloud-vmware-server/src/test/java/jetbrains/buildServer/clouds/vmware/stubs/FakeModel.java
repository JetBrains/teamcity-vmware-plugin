package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineRelocateDiskMoveOptions;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 5/16/2014
 *         Time: 4:36 PM
 */
public class FakeModel {
  private static final FakeModel myInstance = new FakeModel();
  public static FakeModel instance() {return myInstance;}

  private final Map<String, FakeFolder> myFolders = new HashMap<String, FakeFolder>();
  private final Map<String, ResourcePool> myResourcePools = new HashMap<String, ResourcePool>();
  private final Map<String, FakeVirtualMachine> myVms = new HashMap<String, FakeVirtualMachine>();
  private final Map<String, FakeDatacenter> myDatacenters = new HashMap<String, FakeDatacenter>();
  private final Map<String, CustomizationSpec> myCustomizationSpecs = new HashMap<>();

  public Map<String, FakeFolder> getFolders() {
    return myFolders;
  }

  public Map<String, ResourcePool> getResourcePools() {
    return myResourcePools;
  }

  public Map<String, FakeVirtualMachine> getVms() {
    return myVms;
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

  public FakeVirtualMachine getVirtualMachine(String name){
    return myVms.get(name);
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
  }
}
