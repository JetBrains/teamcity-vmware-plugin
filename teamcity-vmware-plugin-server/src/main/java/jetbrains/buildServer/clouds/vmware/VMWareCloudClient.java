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
import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:23 PM
 */
public class VMWareCloudClient extends AbstractCloudClient<VmwareCloudInstance, VmwareCloudImage, VmwareCloudImageDetails>{

  private static final Logger LOG = Logger.getInstance(VMWareCloudClient.class.getName());


  public VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters,
                           @NotNull final VMWareApiConnector apiConnector) {
    super(cloudClientParameters, apiConnector);
  }

  @Nullable
  public VmwareCloudInstance findInstanceByAgent(@NotNull AgentDescription agentDescription) {
    final String imageName = agentDescription.getAvailableParameters().get(VMWarePropertiesNames.IMAGE_NAME);
    if (imageName != null) {
      final VmwareCloudImage cloudImage = findImageById(imageName);
      if (cloudImage != null) {
        return cloudImage.findInstanceById(agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME));
      }
    }
    return null;
  }

  @Override
  protected VmwareCloudImage checkAndCreateImage(@NotNull final VmwareCloudImageDetails imageDetails) {
    final VMWareApiConnector apiConnector = (VMWareApiConnector)myApiConnector;
    return new VmwareCloudImage(apiConnector, imageDetails, myAsyncTaskExecutor);
  }

  @Override
  protected UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> createUpdateInstancesTask() {
    return new UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient>(myApiConnector, this);
  }

  @Nullable
  public String generateAgentName(@NotNull AgentDescription agentDescription) {
    return agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME);
  }

}
