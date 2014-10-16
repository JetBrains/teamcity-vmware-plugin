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
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorInfoFactory;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorType;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:23 PM
 */
public class VMWareCloudClient extends AbstractCloudClient<VmwareCloudInstance, VmwareCloudImage, VmwareCloudImageDetails>{

  private static final Logger LOG = Logger.getInstance(VMWareCloudClient.class.getName());

  private boolean myIsInitialized = false;


  public VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters,
                           @NotNull final Collection<VmwareCloudImageDetails> imageDetails,
                           @NotNull final VMWareApiConnector apiConnector) {
    super(cloudClientParameters, imageDetails, apiConnector);
    myIsInitialized = true;
  }

  public boolean isInitialized() {
    return myIsInitialized;
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

    /*
    final VmwareInstance instance = apiConnector.getInstanceDetails(imageDetails.getVmName());

    if (instance == null) {
      errorList.add(VMWareCloudErrorInfoFactory.noSuchVM(vmName).getMessage());
      break;
    }

    final VMWareImageStartType startType = VMWareImageStartType.valueOf(behaviourStr);
    final VMWareImageType imageType = instance.isReadonly() ? VMWareImageType.TEMPLATE : VMWareImageType.INSTANCE;
    if (startType.isUseOriginal()) {
      if (imageType == VMWareImageType.TEMPLATE){
        errorList.add(VMWareCloudErrorInfoFactory.error("Cannot use image % as Start/Stop - it's readonly", vmName).getMessage());
        break;
      }
      final VmwareCloudImage cloudImage = new VmwareCloudImage(
        myApiConnector, vmName, imageType, cloneFolder, resourcePool, snapshotName,
        instance.getInstanceStatus(), myAsyncTaskExecutor, startType, 0);
      myImageMap.put(vmName, cloudImage);
    } else {
      int maxInstances = 0;
      try {
        maxInstances = Integer.parseInt(maxInstancesStr);
      } catch (Exception ex) {
      }

      if (!myApiConnector.checkCloneFolderExists(cloneFolder)) {
        errorList.add(VMWareCloudErrorInfoFactory.noSuchFolder(cloneFolder).getMessage());
        break;
      }

      if (!myApiConnector.checkResourcePoolExists(resourcePool)) {
        errorList.add(VMWareCloudErrorInfoFactory.noSuchResourcePool(resourcePool).getMessage());
        break;
      }

    }
    */

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

  public void clearErrorInfo(){

  }
}
