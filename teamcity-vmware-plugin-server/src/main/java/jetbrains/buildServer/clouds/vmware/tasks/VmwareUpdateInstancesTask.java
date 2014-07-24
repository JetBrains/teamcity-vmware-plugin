package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.mo.VirtualMachine;
import java.rmi.RemoteException;
import java.util.*;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VMWareCloudImage;
import jetbrains.buildServer.clouds.vmware.VMWareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 4/24/2014
 *         Time: 1:51 PM
 */
public class VmwareUpdateInstancesTask implements Runnable {

  private static final Logger LOG = Logger.getInstance(VmwareUpdateInstancesTask.class.getName());

  private VMWareApiConnector myApiConnector;
  private final VMWareCloudClient myCloudClient;


  public VmwareUpdateInstancesTask(final VMWareApiConnector apiConnector, final VMWareCloudClient cloudClient){
    super();
    myApiConnector = apiConnector;
    myCloudClient = cloudClient;
  }

  public void run() {
    try {
      myCloudClient.clearErrorInfo();
      final Collection<VMWareCloudImage> images = myCloudClient.getImages();
      Map<String, VMWareCloudImage> imagesMap = new HashMap<String, VMWareCloudImage>();
      for (VMWareCloudImage image : images) {
        imagesMap.put(image.getId(), image);
      }
      final Map<String, VirtualMachine> vms = myApiConnector.getVirtualMachines(false);
      final Map<String, Map<String, VirtualMachine>> runData = new HashMap<String, Map<String, VirtualMachine>>();
      for (Map.Entry<String, VMWareCloudImage> entry : imagesMap.entrySet()) {
        final VirtualMachine vm = vms.get(entry.getKey());
        final VMWareCloudImage image = entry.getValue();
        if (vm != null){
          if (image.getStartType().isUseOriginal()){
            final InstanceStatus instanceStatus = myApiConnector.getInstanceStatus(vm);
            final VMWareCloudInstance imageInstance = image.getInstances().iterator().next();
            if (imageInstance.isInPermanentStatus()) {
              imageInstance.setStatus(instanceStatus);
            }
          }
          image.clearErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
        } else {
          image.setErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
        }
      }
      for (Map.Entry<String, VirtualMachine> vmEntry : vms.entrySet()) {
        final VirtualMachine vm = vmEntry.getValue();
        final String vmName = vmEntry.getKey();

        final String imageName;
        if (imagesMap.get(vmName) != null) {
            imageName = vmName;
        } else {
          imageName = myApiConnector.getImageName(vm);
          if (imageName == null) { // we skip not ready instances too.
            continue;
          }
        }

        final VMWareCloudImage image = imagesMap.get(imageName);
        if (image != null) {
          if (imageName.equals(vmName) != image.getStartType().isUseOriginal())
            continue;
          if (runData.get(imageName) == null){
            runData.put(imageName, new HashMap<String, VirtualMachine>());
          }

          runData.get(imageName).put(vmEntry.getKey(), vm);
        }
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
              instance.clearErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
            } else {
              instance.setErrorType(VMWareCloudErrorType.IMAGE_NOT_EXISTS);
            }
          }
        });
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
