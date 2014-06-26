package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.mo.VirtualMachine;
import java.rmi.RemoteException;
import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
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

  private static final Logger LOG = Logger.getInstance(UpdateInstancesTask.class.getName());


  private VMWareApiConnector myApiConnector;
  private final VMWareCloudClient myCloudClient;


  public UpdateInstancesTask(final VMWareApiConnector apiConnector, final VMWareCloudClient cloudClient){
    myApiConnector = apiConnector;
    myCloudClient = cloudClient;
  }

  public void run() {
    try {
      myCloudClient.clearErrorInfo();
      final Collection<VMWareCloudImage> images = myCloudClient.getImages();
      Map<String, VMWareCloudImage> imagesMap = new HashMap<String, VMWareCloudImage>();
      for (VMWareCloudImage image : images) {
        image.setErrorInfo(null);
        imagesMap.put(image.getId(), image);
        for (VMWareCloudInstance inst : image.getInstances()) {
          inst.setErrorInfo(null);
        }
      }
      final Map<String, VirtualMachine> vms = myApiConnector.getVirtualMachines(false);
      final Map<String, Map<String, InstanceStatus>> runData = new HashMap<String, Map<String, InstanceStatus>>();
      for (Map.Entry<String, VMWareCloudImage> entry : imagesMap.entrySet()) {
        final VirtualMachine vm = vms.get(entry.getKey());
        final VMWareCloudImage image = entry.getValue();
        if (vm != null){
          if (image.getStartType().isUseOriginal()){
            final InstanceStatus instanceStatus = myApiConnector.getInstanceStatus(vm);
            final VMWareCloudInstance imageInstance = image.getInstances().iterator().next();
            imageInstance.setStatus(instanceStatus);
          }
        } else {
          image.setErrorInfo(new CloudErrorInfo("Can't find vm with name " + image.getName()));
        }
      }
      for (Map.Entry<String, VirtualMachine> vmEntry : vms.entrySet()) {
        final VirtualMachine vm = vmEntry.getValue();


        final String imageName = myApiConnector.getImageName(vm);
        if (imageName == null) {
          continue;
        }

        if (runData.get(imageName) == null){
          runData.put(imageName, new HashMap<String, InstanceStatus>());
        }

        runData.get(imageName).put(vmEntry.getKey(), myApiConnector.getInstanceStatus(vm));

      }
      for (final CloudImage image : images) {
        if (runData.get(image.getId()) != null){
          ((VMWareCloudImage)image).populateInstances(runData.get(image.getId()));
        }
      }
      for (final VMWareCloudImage image : images) {
        image.updateRunningInstances(new VMWareCloudImage.ProcessImageInstancesTask() {
          public void processInstance(@NotNull final VMWareCloudInstance instance) {
            VirtualMachine vm = null;
            try {
              vm = vms.get(instance.getName());
              if (vm == null) {
                vm = myApiConnector.getInstanceDetails(instance.getName());
              }
            } catch (RemoteException e) {
              LOG.error(e.toString() + ":" + e.getStackTrace()[0].toString());
            }
            if (vm != null){
              instance.updateVMInfo(vm);
              instance.setErrorInfo(null);
            } else {
              instance.setErrorInfo(new CloudErrorInfo("Unable to find information about VM"));
            }
          }
        });
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
