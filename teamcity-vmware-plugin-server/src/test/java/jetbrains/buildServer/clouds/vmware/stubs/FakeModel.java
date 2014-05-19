package jetbrains.buildServer.clouds.vmware.stubs;

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
  private final Map<String, VirtualMachine> myVms = new HashMap<String, VirtualMachine>();

  public Map<String, Folder> getFolders() {
    return myFolders;
  }

  public Map<String, ResourcePool> getResourcePools() {
    return myResourcePools;
  }

  public Map<String, VirtualMachine> getVms() {
    return myVms;
  }

  public Folder getFolder(String name){
    return myFolders.get(name);
  }

  public ResourcePool getResourcePool(String name){
    return myResourcePools.get(name);
  }

  public VirtualMachine getVirtualMachine(String name){
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
    final VirtualMachine vm = putVM(name, new FakeVirtualMachine(name, name.contains("template"), isRunning));
    if (spec != null && spec.getLocation().getDiskMoveType()
      .equals(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.name())){
      //((FakeVirtualMachine)vm).set
    }
    return vm;
  }
  public VirtualMachine addVM(String name, boolean isRunning){
    return addVM(name, isRunning, null);
  }

  public VirtualMachine putVM(String name, VirtualMachine vm){
    return myVms.put(name, vm);
  }

  public void removeVM(String name){
    myVms.remove(name);
  }

  public void addVMSnapshot(String vmName, String snapshotName){
    final FakeVirtualMachine vm = (FakeVirtualMachine)myVms.get(vmName);
    if (vm == null)
      throw new IllegalArgumentException("Unable to find VM: " + vmName);

    vm.addSnapshot(snapshotName);
  }

  public void removeVmSnaphot(String vmName, String snapshotName){
    final FakeVirtualMachine vm = (FakeVirtualMachine)myVms.get(vmName);
    if (vm == null)
      throw new IllegalArgumentException("Unable to find VM: " + vmName);

    vm.removeSnapshot(snapshotName);
  }

}
