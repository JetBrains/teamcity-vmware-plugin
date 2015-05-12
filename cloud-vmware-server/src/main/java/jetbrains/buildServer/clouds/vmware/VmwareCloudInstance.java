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
import com.vmware.vim25.VirtualMachinePowerState;
import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.RunningInstanceInfo;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.INSTANCE_NAME;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:57 PM
 */
public class VmwareCloudInstance implements CloudInstance {

  private static final Logger LOG = Logger.getInstance(VmwareCloudInstance.class.getName());

  private final String myInstanceName;
  private final String myImageId;
  private final String mySnapshotName;

  private CloudErrorInfo myErrorInfo = null;
  private InstanceStatus myStatus = InstanceStatus.UNKNOWN;
  private String myIpAddress;
  private Date myStartedDate;

  public VmwareCloudInstance(@NotNull final String imageId, @NotNull final String instanceName, @NotNull final String snapshotName) {
    myImageId = imageId;
    myInstanceName = instanceName;
    mySnapshotName = snapshotName;
    // set to current Date;
    myStartedDate = new Date();
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
    return myImageId;
  }

  @NotNull
  public Date getStartedTime() {
    return myStartedDate;
  }


  @Nullable
  public String getNetworkIdentity() {
    return myIpAddress;
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public void clearErrorInfo(){
    myErrorInfo = null;
  }

  public void setErrorInfo(@Nullable final CloudErrorInfo errorInfo) {
    myErrorInfo = errorInfo;
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

  public boolean containsAgent(@NotNull AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return getInstanceId().equals(configParams.get(INSTANCE_NAME));
  }

  public void updateInfo(@NotNull final RunningInstanceInfo info) {
    if (!info.isInitialized()){
      if (myStatus != InstanceStatus.SCHEDULED_TO_START) {  // not still cloning
        setStatus(InstanceStatus.UNKNOWN);
      }
      return;
    }

    final InstanceStatus instanceStatus = info.getInstanceStatus();
    if (instanceStatus == InstanceStatus.RUNNING) {
      if (myStatus == InstanceStatus.STOPPED) {
        setStatus(InstanceStatus.RUNNING);
      }
      myStartedDate = info.getStartDate();
      myIpAddress = info.getIpAddress();
    } else {
      if (myStatus != InstanceStatus.SCHEDULED_TO_START && myStatus != InstanceStatus.STOPPED) {
        setStatus(InstanceStatus.STOPPED);
      }
    }
  }

}
