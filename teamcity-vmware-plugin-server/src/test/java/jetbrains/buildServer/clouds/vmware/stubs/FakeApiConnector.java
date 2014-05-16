package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.VMWareCloudImage;
import jetbrains.buildServer.clouds.vmware.VMWareCloudInstance;
import jetbrains.buildServer.clouds.vmware.VMWareImageStartType;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 2:40 PM
 */
public class FakeApiConnector extends VMWareApiConnectorImpl {

  private final Map<String, Folder> myFolders = new HashMap<String, Folder>();
  private final Map<String, ResourcePool> myResourcePools = new HashMap<String, ResourcePool>();
  private final Map<String, VirtualMachine> myVms = new HashMap<String, VirtualMachine>();
  private final Map<String, Map<String, ManagedObjectReference>> myVmSnapshots = new HashMap<String, Map<String, ManagedObjectReference>>();

  public FakeApiConnector() throws MalformedURLException, RemoteException {
    super(null, null, null);
  }

  @Override
  protected <T extends ManagedEntity> T findEntityByName(final String name, final Class<T> instanceType) throws RemoteException {
    if (instanceType == Folder.class){
      return (T)myFolders.get(name);
    } else if (instanceType == ResourcePool.class){
      return (T)myResourcePools.get(name);
    } else if (instanceType == VirtualMachine.class){
      return (T)myVms.get(name);
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  @Override
  protected <T extends ManagedEntity> Collection<T> findAllEntities(final Class<T> instanceType) throws RemoteException {
    if (instanceType == Folder.class){
      return (Collection<T>)myFolders.values();
    } else if (instanceType == ResourcePool.class){
      return (Collection<T>)myResourcePools.values();
    } else if (instanceType == VirtualMachine.class){
      return (Collection<T>)myVms.values();
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  public void stopInstance(final VMWareCloudInstance instance) {

  }

  public void restartInstance(final VMWareCloudInstance instance) throws RemoteException {

  }

  public boolean checkCloneFolder(@NotNull final String cloneFolderName) {
    return myFolders.containsKey(cloneFolderName);
  }

  public boolean checkResourcePool(@NotNull final String resourcePool) {
    return myResourcePools.containsKey(resourcePool);
  }

  public VirtualMachine getInstanceDetails(final String instanceName) throws RemoteException {
    return myVms.get(instanceName);
  }

  @Nullable
  public String getImageName(final VirtualMachine vm) {
    return null;
  }


  public InstanceStatus getInstanceStatus(final VirtualMachine vm) {
    return null;
  }

  /**********************************
   *
   *
   *
   *
   *
   ************************************/

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
  public void addVM(String name, boolean isRunning){
    putVM(name, new FakeVirtualMachine(name, name.contains("template"), isRunning));
  }

  public void putVM(String name, VirtualMachine vm){
    myVms.put(name, vm);
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
