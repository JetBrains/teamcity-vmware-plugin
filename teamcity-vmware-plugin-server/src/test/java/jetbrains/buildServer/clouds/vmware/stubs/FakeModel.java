package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineRelocateDiskMoveOptions;
import com.vmware.vim25.mo.Folder;
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

  private final Map<String, Folder> myFolders = new HashMap<String, Folder>();
  private final Map<String, ResourcePool> myResourcePools = new HashMap<String, ResourcePool>();
  private final Map<String, FakeVirtualMachine> myVms = new HashMap<String, FakeVirtualMachine>();

  public Map<String, Folder> getFolders() {
    return myFolders;
  }

  public Map<String, ResourcePool> getResourcePools() {
    return myResourcePools;
  }

  public Map<String, FakeVirtualMachine> getVms() {
    return myVms;
  }

  public Folder getFolder(String name){
    return myFolders.get(name);
  }

  public ResourcePool getResourcePool(String name){
    return myResourcePools.get(name);
  }

  public FakeVirtualMachine getVirtualMachine(String name){
    return myVms.get(name);
  }

  public void addFolder(String name){
    putFolder(name, new Folder(null, null));
  }

  public void putFolder(String name, Folder folder){
    myFolders.put(name, folder);
  }

  public void removeFolder(String name){
    myFolders.remove(name);
  }

  public void addResourcePool(String name){
    putResourcePool(name, new ResourcePool(null, null));
  }

  public void putResourcePool(String name, ResourcePool pool){
    myResourcePools.put(name, pool);
  }

  public void removeResourcePool(String name){
    myResourcePools.remove(name);
  }

  public void addVM(String name){
    addVM(name, false);
  }

  public VirtualMachine addVM(String name, boolean isRunning, VirtualMachineCloneSpec spec){
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
  public VirtualMachine addVM(String name, boolean isRunning){
    return addVM(name, isRunning, null);
  }

  public VirtualMachine putVM(String name, FakeVirtualMachine vm){
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

  public void clear(){
    myFolders.clear();
    myVms.clear();
    myResourcePools.clear();
  }
}
