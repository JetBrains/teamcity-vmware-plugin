package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.Task;
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
  Task startInstance(VMWareCloudInstance instance, String agentName, CloudInstanceUserData userData)
    throws RemoteException, InterruptedException;

  Task reconfigureInstance(@NotNull final VMWareCloudInstance instance,
                           @NotNull final String agentName,
                           @NotNull final CloudInstanceUserData userData) throws RemoteException;

  Task cloneVm(@NotNull final String baseVmName,
               @NotNull String resourcePool,
               @NotNull String folder,
               @NotNull final String newVmName,
               @Nullable final String snapshotName,
               final boolean isLinkedClone) throws RemoteException;

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
