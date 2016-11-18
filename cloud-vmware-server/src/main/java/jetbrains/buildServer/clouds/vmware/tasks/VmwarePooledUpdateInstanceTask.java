package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 26/10/2016.
 */
public class VmwarePooledUpdateInstanceTask
  extends UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> {
  private static final Logger LOG = Logger.getInstance(VmwarePooledUpdateInstanceTask.class.getName());

  private volatile List<VMWareCloudClient> myClients = new CopyOnWriteArrayList<>();
  private final List<VMWareCloudClient> myNewClients = new ArrayList<>();
  private volatile AtomicBoolean myAlreadyRunning = new AtomicBoolean(false);
  private final PooledTaskObsoleteHandler myHandler;


  public VmwarePooledUpdateInstanceTask(@NotNull final VMWareApiConnector connector,
                                        @NotNull final VMWareCloudClient client,
                                        @NotNull final PooledTaskObsoleteHandler obsoleteHandler) {
    super(connector, client);
    myHandler = obsoleteHandler;
  }

  @Used("tests")
  public VmwarePooledUpdateInstanceTask(@NotNull final VMWareApiConnector connector,
                                 @NotNull final VMWareCloudClient client,
                                 @NotNull final PooledTaskObsoleteHandler obsoleteHandler,
                                 @Used("Tests")
                                 final long stuckTimeMillis,
                                 @Used("Tests")
                                 final boolean rethrowException
                                 ) {
    super(connector, client, stuckTimeMillis, rethrowException);
    myHandler = obsoleteHandler;
  }

  public void run(){
    super.run();
  }

  /**
   * Only run if the client is the top one in the list
   */
  public void runIfNecessary(@NotNull final VMWareCloudClient client){
    if (!myAlreadyRunning.compareAndSet(false, true))
      return;
    try {
      synchronized (this) {
        if (myNewClients.size() != 0) {
          myClients.addAll(myNewClients);
          myNewClients.clear();
        }
      }
      if (myClients.size() == 0) {
        return;
      }
      if (client != myClients.get(0)) {
        return;
      }
      run();
      myClients.forEach(VMWareCloudClient::setInitializedIfNecessary);
    } finally {
      myAlreadyRunning.set(false);
    }
  }

  @NotNull
  @Override
  protected Collection<VmwareCloudImage> getImages() {
    return myClients.stream()
                    .flatMap(client->client.getImages().stream())
                    .collect(Collectors.toList());
  }

  public void addClient(@NotNull VMWareCloudClient client){
    synchronized (this) {
      myNewClients.add(client);
    }
  }

  public void removeClient(@NotNull VMWareCloudClient client){
    synchronized (this) {
      myClients.remove(client);
      myNewClients.remove(client);
      if (myClients.isEmpty() && myNewClients.isEmpty()){
        myHandler.pooledTaskObsolete(this);
      }
    }
  }

  public String getKey(){
    return myConnector.getKey();
  }

  public interface PooledTaskObsoleteHandler {
    void pooledTaskObsolete(@NotNull VmwarePooledUpdateInstanceTask task);
  }
}
