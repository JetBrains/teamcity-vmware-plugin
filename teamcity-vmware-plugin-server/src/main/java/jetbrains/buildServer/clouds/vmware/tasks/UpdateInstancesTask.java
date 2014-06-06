package jetbrains.buildServer.clouds.vmware.tasks;

import com.vmware.vim25.mo.VirtualMachine;
import java.rmi.RemoteException;
import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VMWareCloudImage;
import jetbrains.buildServer.clouds.vmware.VMWareCloudInstance;
import jetbrains.buildServer.clouds.vmware.VMWareImageStartType;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 4/24/2014
 *         Time: 1:51 PM
 */
public class UpdateInstancesTask implements Runnable {

  private VMWareApiConnector myApiConnector;
  private final VMWareCloudClient myCloudClient;


  public UpdateInstancesTask(final VMWareApiConnector apiConnector, final VMWareCloudClient cloudClient){
    myApiConnector = apiConnector;
    myCloudClient = cloudClient;
  }

  public void run() {
    try {
      final Collection<? extends CloudImage> images = myCloudClient.getImages();
      Map<String, VMWareCloudImage> imagesMap = new HashMap<String, VMWareCloudImage>();
      for (CloudImage image : images) {
        imagesMap.put(image.getId(), (VMWareCloudImage)image);
      }
      final Map<String, VirtualMachine> vms = myApiConnector.getVirtualMachines();
      final Map<String, Map<String, InstanceStatus>> runData = new HashMap<String, Map<String, InstanceStatus>>();
      for (Map.Entry<String, VirtualMachine> vmEntry : vms.entrySet()) {
        final VirtualMachine vm = vmEntry.getValue();


        final String imageName = myApiConnector.getImageName(vm);
        if (imageName == null) {
          if (imagesMap.containsKey(vm.getName())){
            // this instance wasn't started by TC:
            final VMWareCloudImage image = imagesMap.get(vm.getName());
            if (image.getStartType() == VMWareImageStartType.START){
              final InstanceStatus instanceStatus = myApiConnector.getInstanceStatus(vm);
              final VMWareCloudInstance imageInstance = (VMWareCloudInstance)image.getInstances().iterator().next();
              imageInstance.setStatus(instanceStatus);
              if (instanceStatus ==InstanceStatus.RUNNING) {
                imageInstance.setErrorInfo(new CloudErrorInfo("Instance wasn't started by TC"));
              } else {
                imageInstance.setErrorInfo(null);
              }
            }

          }
          continue;
        }

        if (runData.get(imageName) == null){
          runData.put(imageName, new HashMap<String, InstanceStatus>());
        }

        runData.get(imageName).put(vmEntry.getKey(), myApiConnector.getInstanceStatus(vm));

      }
      for (final CloudImage image : images) {
        if (runData.get(image.getId()) != null){
          ((VMWareCloudImage)image).populateRunningInstances(runData.get(image.getId()));
        }
      }
      for (final CloudImage imageBase : images) {
        final VMWareCloudImage image = (VMWareCloudImage) imageBase;
        image.updateRunningInstances(new VMWareCloudImage.ProcessImageInstancesTask() {
          public void processInstance(@NotNull final VMWareCloudInstance instance) {
            if (vms.containsKey(instance.getName())){
              instance.updateVMInfo(vms.get(instance.getName()));
              instance.setErrorInfo(null);
            } else {
              instance.setErrorInfo(new CloudErrorInfo("Unable to find information about VM"));
            }
          }
        });
      }
    } catch (RemoteException e) {
      e.printStackTrace();
    }

  }
}
