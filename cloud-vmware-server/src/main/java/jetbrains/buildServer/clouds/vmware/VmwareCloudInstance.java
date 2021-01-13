/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.INSTANCE_NAME;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:57 PM
 */
public class VmwareCloudInstance extends AbstractCloudInstance<VmwareCloudImage> {

  private static final Logger LOG = Logger.getInstance(VmwareCloudInstance.class.getName());
  @NotNull private volatile VmwareSourceState mySourceState = VmwareSourceState.from(null, null);

  private volatile boolean myIsReady = false;

  //
  protected VmwareCloudInstance(@NotNull final VmwareCloudImage image) {
    super(image);
  }

  protected VmwareCloudInstance(@NotNull final VmwareCloudImage image, @NotNull final String instanceName) {
    this(image, instanceName, VmwareSourceState.from(null, null));
  }

  public VmwareCloudInstance(@NotNull final VmwareCloudImage image,
                             @NotNull final String instanceName,
                             @NotNull final VmwareSourceState sourceState) {
    super(image);
    mySourceState = sourceState;
    setInstanceId(instanceName);
    setName(instanceName);
    myIsReady = true;
  }

  @NotNull
  public VmwareSourceState getSourceState() {
    return mySourceState;
  }

  public void setSourceState(@NotNull final VmwareSourceState sourceState) {
    mySourceState = sourceState;
  }

  public boolean containsAgent(@NotNull final AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return getInstanceId().equals(configParams.get(INSTANCE_NAME));
  }

  public boolean isInPermanentStatus(){
    final InstanceStatus status = getStatus();
    return status == InstanceStatus.STOPPED || status == InstanceStatus.RUNNING;
  }

  public boolean isReady(){
    return myIsReady;
  }

  public void setReady(final boolean ready) {
    myIsReady = ready;
  }

  @Override
  public String toString() {
    return "VmwareCloudInstance{" +
           "myInstanceName='" + getName() + "', " +
           "myState='" + getStatus().getName() + "', " +
           "myStatusUpdateTime='" + LogUtil.describe(getStatusUpdateTime()) + "'}";
  }
}
