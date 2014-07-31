package jetbrains.buildServer.clouds.base.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:52 PM
 */
public abstract class AbstractUpdateInstancesTask implements Runnable {
  private static final Logger LOG = Logger.getInstance(AbstractUpdateInstancesTask.class.getName());

  @NotNull private final CloudApiConnector myConnector;
  protected AbstractCloudClient myClient;


  public AbstractUpdateInstancesTask(@NotNull final CloudApiConnector connector) {
    myConnector = connector;
  }

  public void run() {
    final Collection<? extends AbstractCloudImage> images = myClient.getImages();
    for (final AbstractCloudImage image : images) {
      final String imageName = image.getName();
      image.updateErrors(myConnector.checkImage(imageName));
      final Map<String, AbstractInstance> realInstances = myConnector.listImageInstances(imageName);
      for (String realInstanceName : realInstances.keySet()) {
        if (image.findInstanceById(realInstanceName) == null){
           image.createInstance(realInstanceName);
        }
        final AbstractCloudInstance instance = image.findInstanceById(realInstanceName);
        if (instance == null) {
          LOG.warn("Unable to find just created instance " + realInstanceName);
          continue;
        }
        final InstanceStatus realInstanceStatus = myConnector.getInstanceStatus(realInstanceName);
        if (isStatusPermanent(instance.getStatus()) && realInstanceStatus != instance.getStatus()){
          LOG.debug(String.format("Updated instance '%s' status to %s based on API information", realInstanceName, realInstanceStatus));
          instance.setStatus(realInstanceStatus);
        }
      }

      for (final AbstractCloudInstance cloudInstance : image.getInstances()) {
        final String instanceName = cloudInstance.getName();
        if (!realInstances.containsKey(instanceName)){
          image.removeInstance(instanceName);
        }
        cloudInstance.updateErrors(myConnector.checkInstance(instanceName));
      }
    }
  }

  private static boolean isStatusPermanent(InstanceStatus status){
    return status == InstanceStatus.STOPPED || status == InstanceStatus.RUNNING;
  }
}
