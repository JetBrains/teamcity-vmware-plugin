/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.vmware.tasks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 27/10/2016.
 */
public class VmwareUpdateTaskManager {

  public static final ReadWriteLock POOLED_TASKS_LOCK = new ReentrantReadWriteLock();

  // protected 4 tests
  protected final Map<String, VmwarePooledUpdateInstanceTask> myUpdateTasks;

  public VmwareUpdateTaskManager(){
    myUpdateTasks = new HashMap<>();
  }

  @NotNull
  public VmwareUpdateInstanceTask createUpdateTask(@NotNull final VMWareApiConnector connector, @NotNull final VMWareCloudClient client){
    final Set<String> keys = new HashSet<>();
    final VmwarePooledUpdateInstanceTask pooledTask;
    try {
      POOLED_TASKS_LOCK.writeLock().lock();
      myUpdateTasks.forEach((key, t) -> {
        if (t.isExhausted()) {
          keys.add(connector.getKey());
        }
      });
      keys.forEach(myUpdateTasks::remove);
      pooledTask = myUpdateTasks.computeIfAbsent(connector.getKey(), k -> createNewPooledTask(connector, client));
      pooledTask.setShouldKeep();
    } finally {
      POOLED_TASKS_LOCK.writeLock().unlock();
    }
    final VmwareUpdateInstanceTask task = new VmwareUpdateInstanceTask(connector.getKey(), client, pooledTask);
    task.register();

    return task;
  }

  protected VmwarePooledUpdateInstanceTask createNewPooledTask(@NotNull final VMWareApiConnector connector,
                                                               @NotNull final VMWareCloudClient client){
    return new VmwarePooledUpdateInstanceTask(connector, client);
  }

}
