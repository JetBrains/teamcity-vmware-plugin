package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.VMWareCloudImage;
import jetbrains.buildServer.clouds.vmware.VMWareCloudInstance;
import jetbrains.buildServer.clouds.vmware.VMWareImageStartType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:06 PM
 */
public interface VMWareApiConnector {

  @NotNull
  Map<String, VirtualMachine> getVirtualMachines() throws RemoteException;

  @NotNull
  Map<String, Folder> getFolders() throws RemoteException;

  @NotNull
  Map<String, ResourcePool> getResourcePools() throws RemoteException;

  @NotNull
  Map<String, VirtualMachineSnapshotTree> getSnapshotList(String vmName) throws RemoteException;

  @Nullable
  VirtualMachine startInstance(VMWareCloudInstance instance, String agentName, CloudInstanceUserData userData)
    throws RemoteException, InterruptedException;

  String cloneVmIfNecessary(VMWareCloudImage image) throws RemoteException, InterruptedException;

  boolean isStartedByTeamcity(String instanceName) throws RemoteException;

  boolean isInstanceStopped(String instanceName) throws RemoteException;

  boolean ensureSnapshotExists(String instanceName, String snapshotName) throws RemoteException;

  void stopInstance(VMWareCloudInstance instance);

  void restartInstance(VMWareCloudInstance instance) throws RemoteException;

  boolean checkCloneFolderExists(@NotNull String cloneFolderName);

  boolean checkResourcePoolExists(@NotNull String resourcePool);

  boolean checkVirtualMachineExists(@NotNull String vmName);

  @Nullable
  VirtualMachine getInstanceDetails(String instanceName) throws RemoteException;

  @Nullable
  String getImageName(VirtualMachine vm);

  @Nullable
  InstanceStatus getInstanceStatus(VirtualMachine vm);
}
