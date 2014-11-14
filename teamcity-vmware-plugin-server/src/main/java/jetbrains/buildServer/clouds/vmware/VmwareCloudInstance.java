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

package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.INSTANCE_NAME;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:57 PM
 */
public class VmwareCloudInstance extends AbstractCloudInstance<VmwareCloudImage> {

  private static final Logger LOG = Logger.getInstance(VmwareCloudInstance.class.getName());

  private final String myInstanceName;
  private final VmwareCloudImage myImage;
  private InstanceStatus myStatus = InstanceStatus.UNKNOWN;
  private String myIpAddress;
  private String mySnapshotName;

  public VmwareCloudInstance(@NotNull final VmwareCloudImage image, @NotNull final String instanceName, @NotNull final String snapshotName) {
    super(image, instanceName, instanceName);
    myImage = image;
    myInstanceName = instanceName;
    mySnapshotName = snapshotName;
  }

  @NotNull
  public String getInstanceId() {
    return myInstanceName;
  }

  @NotNull
  public String getName() {
    return myInstanceName;
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @NotNull
  public VmwareCloudImage getImage() {
    return myImage;
  }


  @Nullable
  public String getNetworkIdentity() {
    return myIpAddress;
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  @NotNull
  public String getSnapshotName() {
    return mySnapshotName;
  }

  public void setStatus(@NotNull final InstanceStatus status) {
    if (myStatus == status)
      return;
    LOG.info(String.format("Changing %s(%x) status from %s to %s ", getName(), hashCode(), myStatus, status));
    myStatus = status;
  }

  public void updateVMInfo(@NotNull final VmwareInstance vm) {
    if (!vm.isInitialized()){
      if (myStatus != InstanceStatus.SCHEDULED_TO_START) {
        setStatus(InstanceStatus.UNKNOWN); // still cloning
      }
      return;
    }
    if (vm.isPoweredOn()) {
      if (myStatus == InstanceStatus.STOPPED) {
        setStatus(InstanceStatus.RUNNING);
      }
      myIpAddress = vm.getIpAddress();
    } else {
      if (myStatus != InstanceStatus.SCHEDULED_TO_START && myStatus != InstanceStatus.STOPPED) {
        setStatus(InstanceStatus.STOPPED);
      }
    }
  }

  public boolean containsAgent(@NotNull AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return getInstanceId().equals(configParams.get(INSTANCE_NAME));
  }

  public boolean isInPermanentStatus(){
    return myStatus == InstanceStatus.STOPPED || myStatus == InstanceStatus.RUNNING;
  }
}
