package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.DummyApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 27/10/2016.
 */
public class VmwareUpdateInstanceTask
  extends UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient>
  implements VMWareCloudClient.DisposeHandler {

  private static final Logger LOG = Logger.getInstance(VmwareUpdateInstanceTask.class.getName());


  @NotNull private final VMWareCloudClient myClient;
  @NotNull private final VmwarePooledUpdateInstanceTask myPoolTask;

  VmwareUpdateInstanceTask(@NotNull final String key,
                           @NotNull final VMWareCloudClient client,
                           @NotNull final VmwarePooledUpdateInstanceTask poolTask) {
    super(new DummyApiConnector(key), client);
    myClient = client;
    myPoolTask = poolTask;
  }

  void register(){
    myPoolTask.addClient(myClient);
    myClient.addDisposeHandler(this);
  }

  @Override
  public void run() {
    LOG.info("Run inside...");
    myPoolTask.runIfNecessary(myClient);
  }

  @Override
  public void clientDisposing(@NotNull final VMWareCloudClient client) {
    myPoolTask.removeClient(myClient);
  }
}
