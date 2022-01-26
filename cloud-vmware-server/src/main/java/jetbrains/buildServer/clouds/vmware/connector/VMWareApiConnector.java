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

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.beans.FolderBean;
import jetbrains.buildServer.clouds.vmware.connector.beans.ResourcePoolBean;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
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
  String TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME = TEAMCITY_VMWARE_PREFIX + "image.name";
  String TEAMCITY_VMWARE_IMAGE_SOURCE_VM_ID = TEAMCITY_VMWARE_PREFIX + "image.vm.id";
  String TEAMCITY_VMWARE_IMAGE_SOURCE_ID = TEAMCITY_VMWARE_PREFIX + "image.nickname";
  String TEAMCITY_VMWARE_CLONED_INSTANCE = TEAMCITY_VMWARE_PREFIX + "cloned.instance";
  String TEAMCITY_VMWARE_PROFILE_ID = TEAMCITY_VMWARE_PREFIX + CloudConstants.PROFILE_ID;
  String TEAMCITY_VMWARE_SERVER_UUID = TEAMCITY_VMWARE_PREFIX + "server.uuid";

  void test() throws VmwareCheckedCloudException;

  @NotNull
  List<VmwareInstance> getVirtualMachines(boolean filterClones) throws VmwareCheckedCloudException;

  Map<String, String> getVMParams(@NotNull final String vmName) throws VmwareCheckedCloudException;

  @NotNull
  List<FolderBean> getFolders() throws VmwareCheckedCloudException;

  @NotNull
  List<ResourcePoolBean> getResourcePools() throws VmwareCheckedCloudException;

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

  Task cloneAndStartVm(@NotNull final VmwareCloudInstance instance) throws VmwareCheckedCloudException;

  <T extends ManagedEntity> boolean hasPrivilegeOnResource(@NotNull final String entityId,
                                                           @NotNull final Class<T> instanceType,
                                                           @NotNull final String permission) throws VmwareCheckedCloudException;
  Task stopInstance(VmwareCloudInstance instance);

  Task deleteInstance(VmwareCloudInstance instance);

  void restartInstance(VmwareCloudInstance instance) throws VmwareCheckedCloudException;

  boolean checkVirtualMachineExists(@NotNull String vmName);

  void processImageInstances(@NotNull  final VmwareCloudImage image, @NotNull final VmwareInstanceProcessor processor);

  @NotNull
  VmwareInstance getInstanceDetails(String instanceName) throws VmwareCheckedCloudException;

  @Nullable
  InstanceStatus getInstanceStatus(@NotNull final VirtualMachine vm);

  void dispose();

  @Override
  @NotNull
  <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final VmwareCloudImage image) throws VmwareCheckedCloudException;

  @NotNull
  @Override
  <R extends AbstractInstance> Map<VmwareCloudImage, Map<String, R>> fetchInstances(@NotNull final Collection<VmwareCloudImage> images) throws VmwareCheckedCloudException;

  @NotNull
  Map<String,String> getCustomizationSpecs();

  CustomizationSpec getCustomizationSpec(String name) throws VmwareCheckedCloudException;

  interface VmwareInstanceProcessor {
    void process(VmwareInstance instance);
  }
}
