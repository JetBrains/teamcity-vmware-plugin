

package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager.POOLED_TASKS_LOCK;

/**
 * Created by sergeypak on 26/10/2016.
 */
public class VmwarePooledUpdateInstanceTask
  extends UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> {
  private static final Logger LOG = Logger.getInstance(VmwarePooledUpdateInstanceTask.class.getName());

  private static final int TO_BE_REMOVED = 1;
  private static final int NOT_TO_BE_REMOVED = 2;

  private volatile List<VMWareCloudClient> myClients = new CopyOnWriteArrayList<>();
  private final List<VMWareCloudClient> myNewClients = new ArrayList<>();
  private volatile AtomicBoolean myAlreadyRunning = new AtomicBoolean(false);

  // 0 - regular state
  // 1 - to be removed (and nothing to be added)
  // 2 - not to be removed (even if empty)
  private final AtomicInteger mySpecialState = new AtomicInteger(0);


  public VmwarePooledUpdateInstanceTask(@NotNull final VMWareApiConnector connector,
                                        @NotNull final VMWareCloudClient client) {
    super(connector, client);
  }

  @Used("tests")
  public VmwarePooledUpdateInstanceTask(@NotNull final VMWareApiConnector connector,
                                 @NotNull final VMWareCloudClient client,
                                 @Used("Tests")
                                 final long stuckTimeMillis,
                                 @Used("Tests")
                                 final boolean rethrowException
                                 ) {
    super(connector, client, stuckTimeMillis, rethrowException);
  }

  public void run(){
    super.run();
  }

  /**
   * Only run if the client is the top one in the list
   */
  public void runIfNecessary(@NotNull final VMWareCloudClient client) {
    if (mySpecialState.get() != 0) {
      return;
    }

    if (!myAlreadyRunning.compareAndSet(false, true)) {
      return;
    }
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

  protected int getSpecialState(){
    return mySpecialState.get();
  }

  public boolean isExhausted(){
    return mySpecialState.get() == TO_BE_REMOVED;
  }

  public boolean setShouldKeep(){
    return mySpecialState.compareAndSet(0, NOT_TO_BE_REMOVED);
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
      mySpecialState.set(0);
    }
  }

  public void removeClient(@NotNull VMWareCloudClient client){
    synchronized (this) {
      myClients.remove(client);
      myNewClients.remove(client);
      try {
        POOLED_TASKS_LOCK.readLock().lock();
        if (myClients.size() == 0 && myNewClients.size() == 0) {
          mySpecialState.compareAndSet(0, TO_BE_REMOVED);
        }
      } finally {
        POOLED_TASKS_LOCK.readLock().unlock();
      }
    }
  }

  public String getKey(){
    return myConnector.getKey();
  }
}