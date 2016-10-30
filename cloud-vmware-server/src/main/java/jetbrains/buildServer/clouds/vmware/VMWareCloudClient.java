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
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
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
  @NotNull private final VmwareUpdateTaskManager myTaskManager;
  @NotNull private final File myIdxStorage;
  private final Integer myProfileInstancesLimit;
  private final List<DisposeHandler> myDisposeHandlers = new ArrayList<>();


  public VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters,
                           @NotNull final VMWareApiConnector apiConnector,
                           @NotNull final VmwareUpdateTaskManager taskManager,
                           @NotNull final File idxStorage) {
    super(cloudClientParameters, apiConnector);
    myTaskManager = taskManager;
    myIdxStorage = idxStorage;
    final String limitStr = cloudClientParameters.getParameter(VMWareWebConstants.PROFILE_INSTANCE_LIMIT);
    myProfileInstancesLimit = StringUtil.isEmpty(limitStr) ? null : Integer.valueOf(limitStr);
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
    return new VmwareCloudImage(apiConnector, imageDetails, myAsyncTaskExecutor, myIdxStorage);
  }

  @Override
  public boolean canStartNewInstance(@NotNull final CloudImage baseImage) {
    if (myProfileInstancesLimit != null) {
      final AtomicLong count = new AtomicLong(0);
      myImageMap.forEach((s, img) -> {
        count.addAndGet(img.getInstances().stream().filter(i -> i.getStatus().isCanTerminate()).count());
      });
      if (count.get() >= myProfileInstancesLimit){
        return false;
      }
    }
    return super.canStartNewInstance(baseImage);
  }

  @NotNull
  @Override
  protected UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> createUpdateInstancesTask() {
    return myTaskManager.createUpdateTask((VMWareApiConnector)myApiConnector, this);
  }

  @Nullable
  public String generateAgentName(@NotNull AgentDescription agentDescription) {
    return agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME);
  }

  public void addDisposeHandler(@NotNull DisposeHandler disposeHandler){
    myDisposeHandlers.add(disposeHandler);
  }

  @Override
  public void dispose() {
    myDisposeHandlers.forEach(d->{
      try {
        d.clientDisposing(this);
      } catch (Exception e) {
        LOG.warn("An exception occurred while disposing client", e);
      }
    });
    super.dispose();
  }

  public static interface DisposeHandler{
    public void clientDisposing(@NotNull final  VMWareCloudClient client);
  }
}
