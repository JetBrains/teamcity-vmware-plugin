/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:06 PM
 */
public interface VMWareApiConnector extends CloudApiConnector<VmwareCloudImage, VmwareCloudInstance> {

  String TEAMCITY_VMWARE_PREFIX = "teamcity.vmware.";
  String TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION = TEAMCITY_VMWARE_PREFIX + "image.change.version";
  String TEAMCITY_VMWARE_IMAGE_SNAPSHOT = TEAMCITY_VMWARE_PREFIX + "image.snapshot";
  String TEAMCITY_VMWARE_IMAGE_NAME = TEAMCITY_VMWARE_PREFIX + "image.name";
  String TEAMCITY_VMWARE_CLONED_INSTANCE = TEAMCITY_VMWARE_PREFIX + "cloned.instance";

  void test() throws VmwareCheckedCloudException;

  @NotNull
  Map<String, VmwareInstance> getVirtualMachines(boolean filterClones) throws VmwareCheckedCloudException;

  Map<String, String> getVMParams(@NotNull final String vmName) throws VmwareCheckedCloudException;

  @NotNull
  Map<String, VmwareManagedEntity> getFolders() throws VmwareCheckedCloudException;

  @NotNull
  Map<String, VmwareManagedEntity> getResourcePools() throws VmwareCheckedCloudException;

  @NotNull
  Map<String, VirtualMachineSnapshotTree> getSnapshotList(String vmName) throws VmwareCheckedCloudException;

  @Nullable
  String getLatestSnapshot(@NotNull final String vmName,@NotNull final String snapshotNameMask) throws VmwareCheckedCloudException;

  @Nullable
  Task startInstance(VmwareCloudInstance instance, String agentName, CloudInstanceUserData userData)
    throws VmwareCheckedCloudException, InterruptedException;

  Task reconfigureInstance(@NotNull final VmwareCloudInstance instance,
                           @NotNull final String agentName,
                           @NotNull final CloudInstanceUserData userData) throws VmwareCheckedCloudException;

  Task cloneVm(@NotNull final VmwareCloudInstance instance, @NotNull String resourcePool,@NotNull String folder) throws VmwareCheckedCloudException;

  boolean isStartedByTeamcity(String instanceName) throws VmwareCheckedCloudException;

  boolean isInstanceStopped(String instanceName) throws VmwareCheckedCloudException;

  boolean ensureSnapshotExists(String instanceName, String snapshotName) throws VmwareCheckedCloudException;

  Task stopInstance(VmwareCloudInstance instance);

  void restartInstance(VmwareCloudInstance instance) throws VmwareCheckedCloudException;

  boolean checkVirtualMachineExists(@NotNull String vmName);

  @NotNull
  VmwareInstance getInstanceDetails(String instanceName) throws VmwareCheckedCloudException;

  @Nullable
  String getImageName(@NotNull final VirtualMachine vm);

  @Nullable
  InstanceStatus getInstanceStatus(@NotNull final VirtualMachine vm);

  @NotNull
  Map<String, String> getTeamcityParams(@NotNull final VirtualMachine vm);

  void dispose();

  @NotNull
  Map<String, VmwareInstance> listImageInstances(@NotNull final VmwareCloudImage image) throws VmwareCheckedCloudException;
}
