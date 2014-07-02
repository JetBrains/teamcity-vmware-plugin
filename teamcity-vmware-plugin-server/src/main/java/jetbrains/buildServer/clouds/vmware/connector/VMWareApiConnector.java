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
import jetbrains.buildServer.clouds.vmware.VMWareCloudInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:06 PM
 */
public interface VMWareApiConnector {

  String TEAMCITY_VMWARE_PREFIX = "teamcity.vmware.";
  String TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION = TEAMCITY_VMWARE_PREFIX + "image.change.version";
  String TEAMCITY_VMWARE_IMAGE_SNAPSHOT = TEAMCITY_VMWARE_PREFIX + "image.snapshot";
  String TEAMCITY_VMWARE_IMAGE_NAME = TEAMCITY_VMWARE_PREFIX + "image.name";
  String TEAMCITY_VMWARE_CLONED_INSTANCE = TEAMCITY_VMWARE_PREFIX + "cloned.instance";

  @NotNull
  Map<String, VirtualMachine> getVirtualMachines(boolean filterClones) throws RemoteException;

  Map<String, VirtualMachine> getClones(@NotNull final String imageName) throws RemoteException;

  Map<String, String> getVMParams(@NotNull final String vmName) throws RemoteException;

  @NotNull
  Map<String, Folder> getFolders() throws RemoteException;

  @NotNull
  Map<String, ResourcePool> getResourcePools() throws RemoteException;

  @NotNull
  Map<String, VirtualMachineSnapshotTree> getSnapshotList(String vmName) throws RemoteException;

  @Nullable
  String getLatestSnapshot(@NotNull final String vmName,@NotNull final String snapshotNameMask) throws RemoteException;

  @Nullable
  Task startInstance(VMWareCloudInstance instance, String agentName, CloudInstanceUserData userData)
    throws RemoteException, InterruptedException;

  Task reconfigureInstance(@NotNull final VMWareCloudInstance instance,
                           @NotNull final String agentName,
                           @NotNull final CloudInstanceUserData userData) throws RemoteException;

  Task cloneVm(@NotNull final VMWareCloudInstance instance, @NotNull String resourcePool,@NotNull String folder) throws RemoteException;

  boolean isStartedByTeamcity(String instanceName) throws RemoteException;

  boolean isInstanceStopped(String instanceName) throws RemoteException;

  boolean ensureSnapshotExists(String instanceName, String snapshotName) throws RemoteException;

  void stopInstance(VMWareCloudInstance instance);

  Task deleteVirtualMachine(VirtualMachine vm) throws RemoteException, InterruptedException;

  void restartInstance(VMWareCloudInstance instance) throws RemoteException;

  boolean checkCloneFolderExists(@NotNull String cloneFolderName);

  boolean checkResourcePoolExists(@NotNull String resourcePool);

  boolean checkVirtualMachineExists(@NotNull String vmName);

  @Nullable
  VirtualMachine getInstanceDetails(String instanceName) throws RemoteException;

  @Nullable
  String getImageName(@NotNull final VirtualMachine vm);

  @Nullable
  InstanceStatus getInstanceStatus(@NotNull final VirtualMachine vm);

  @NotNull
  Map<String, String> getTeamcityParams(@NotNull final VirtualMachine vm);

  void dispose();
}
