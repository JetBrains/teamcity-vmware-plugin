package jetbrains.buildServer.clouds.vmware.tasks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 27/10/2016.
 */
public class VmwareUpdateTaskManager implements VmwarePooledUpdateInstanceTask.PooledTaskObsoleteHandler {

  private final ConcurrentHashMap<String, VmwarePooledUpdateInstanceTask> myUpdateTasks;

  public VmwareUpdateTaskManager(){
    myUpdateTasks = new ConcurrentHashMap<>();
  }

  @NotNull
  public VmwareUpdateInstanceTask createUpdateTask(@NotNull final VMWareApiConnector connector,
                                                   @NotNull final VMWareCloudClient client){
    final AtomicBoolean taskCreated = new AtomicBoolean();
    final VmwarePooledUpdateInstanceTask task =
      myUpdateTasks.computeIfAbsent(connector.getKey(), k -> {
        taskCreated.set(true);
        return createNewPooledTask(connector, client);
      });

    final VmwareUpdateInstanceTask retval = new VmwareUpdateInstanceTask(connector.getKey(), client, task);
    retval.register();

    return retval;
  }

  protected VmwarePooledUpdateInstanceTask createNewPooledTask(@NotNull final VMWareApiConnector connector,
                                                               @NotNull final VMWareCloudClient client){
    return new VmwarePooledUpdateInstanceTask(connector, client, this);
  }

  @Override
  public void pooledTaskObsolete(@NotNull final VmwarePooledUpdateInstanceTask task) {
    myUpdateTasks.remove(task.getKey());
  }
}
