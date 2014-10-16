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

package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.rmi.RemoteException;
import java.util.*;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 4/24/2014
 *         Time: 1:51 PM
 */
public class VmwareUpdateInstancesTask {

  private static final Logger LOG = Logger.getInstance(VmwareUpdateInstancesTask.class.getName());

  private VMWareApiConnector myApiConnector;
  private final VMWareCloudClient myCloudClient;


  public VmwareUpdateInstancesTask(final VMWareApiConnector apiConnector, final VMWareCloudClient cloudClient){
    myApiConnector = apiConnector;
    myCloudClient = cloudClient;
  }

  public void run() {
    try {
      myCloudClient.clearErrorInfo();
      final Collection<VmwareCloudImage> images = myCloudClient.getImages();
      Map<String, VmwareCloudImage> imagesMap = new HashMap<String, VmwareCloudImage>();
      for (VmwareCloudImage image : images) {
        imagesMap.put(image.getId(), image);
      }
      final Map<String, VmwareInstance> vmInstances = myApiConnector.getVirtualMachines(false);
      final Map<String, Map<String, VmwareInstance>> runData = new HashMap<String, Map<String, VmwareInstance>>();
      for (Map.Entry<String, VmwareCloudImage> entry : imagesMap.entrySet()) {
        final VmwareCloudImage image = entry.getValue();
        final VmwareInstance vmInstance = vmInstances.get(entry.getKey());
        if (vmInstance != null){
          if (image.getImageDetails().getCloneType().isUseOriginal()){
            final InstanceStatus instanceStatus = vmInstance.getInstanceStatus();
            final VmwareCloudInstance imageInstance = image.getInstances().iterator().next();
            if (imageInstance.isInPermanentStatus()) {
              imageInstance.setStatus(instanceStatus);
            }
          }
          image.clearErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
        } else {
          image.setErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
        }
      }
      for (final String vmName : vmInstances.keySet()) {
        final VmwareInstance vmInstance = vmInstances.get(vmName);

        final String imageName;
        if (imagesMap.get(vmName) != null) {
            imageName = vmName;
        } else {
          imageName = vmInstance.getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME);
          if (imageName == null) { // we skip not ready instances too.
            continue;
          }
        }

        final VmwareCloudImage image = imagesMap.get(imageName);
        if (image != null) {
          if (imageName.equals(vmName) != image.getImageDetails().getCloneType().isUseOriginal())
            continue;
          if (runData.get(imageName) == null){
            runData.put(imageName, new HashMap<String, VmwareInstance>());
          }

          runData.get(imageName).put(vmName, vmInstance);
        }
      }
      for (final CloudImage image : images) {
        if (runData.get(image.getId()) != null){
          ((VmwareCloudImage)image).populateInstances(runData.get(image.getId()));
        }
      }
      for (final VmwareCloudImage image : images) {
        image.updateRunningInstances(new VmwareCloudImage.ProcessImageInstancesTask() {
          public void processInstance(@NotNull final VmwareCloudInstance instance) {
            VmwareInstance vm = null;
            try {
              vm = vmInstances.get(instance.getName());
              if (vm == null) {
                vm = myApiConnector.getInstanceDetails(instance.getName());
              }
            } catch (RemoteException e) {
              LOG.error(e.toString() + ":" + e.getStackTrace()[0].toString());
            }
            if (vm != null){
              instance.updateVMInfo(vm);
              instance.clearErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
            } else {
              instance.setErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
            }
          }
        });
      }
    } catch (Exception e) {
      LOG.error(e.toString() + ":" + e.getStackTrace()[0].toString());
    }

  }
}
