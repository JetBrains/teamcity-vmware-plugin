/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.base.connector;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 3:51 PM
 */
public class CloudAsyncTaskExecutor {

  private static final Logger LOG = Logger.getInstance(CloudAsyncTaskExecutor.class.getName());
  private static final long LONG_TASK_TIME = 60*1000l;

  private final ScheduledExecutorService myExecutor;
  private final ConcurrentMap<AsyncCloudTask, TaskCallbackHandler> myExecutingTasks;
  private final Map<AsyncCloudTask, Long> myLongTasks = new HashMap<AsyncCloudTask, Long>();
  private final String myPrefix;
  private final boolean myExecuteAllAsync;

  public CloudAsyncTaskExecutor(@NotNull String prefix) {
    myPrefix = prefix;
    myExecutingTasks = new ConcurrentHashMap<AsyncCloudTask, TaskCallbackHandler>();

    int threadCount = TeamCityProperties.getInteger("teamcity.vmware.profile.async.threads", 2);
    int delay = TeamCityProperties.getInteger("teamcity.vmware.profile.async.delay.millis", 300);
    myExecuteAllAsync = threadCount > 1;
    myExecutor = ExecutorsFactory.newFixedScheduledDaemonExecutor(prefix, threadCount);
    scheduleWithFixedDelay("Check for tasks", new Runnable() {
      public void run() {
        checkTasks();
      }
    }, 0, delay, TimeUnit.MILLISECONDS);
  }

  public void executeAsync(@NotNull final AsyncCloudTask operation, @NotNull final TaskCallbackHandler callbackHandler) {
    if (myExecuteAllAsync) {
      submit(operation.getName(), ()->{
        myExecutingTasks.put(operation, callbackHandler);
        operation.executeOrGetResult();
      });
    } else {
      myExecutingTasks.put(operation, callbackHandler);
      operation.executeOrGetResult();
    }
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull final String taskName, @NotNull final Runnable task, final long initialDelay, final long delay, final TimeUnit unit){
    return myExecutor.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        NamedThreadFactory.executeWithNewThreadName(taskName, task);
      }
    }, initialDelay, delay, unit);
  }

  public Future<?> submit(final String taskName, @NotNull final Runnable r){
    return myExecutor.submit(new Runnable() {
      public void run() {
        try {
          LOG.debug("Starting " + taskName);
          NamedThreadFactory.executeWithNewThreadName(taskName, r);
        } finally {
          LOG.debug("Finished " + taskName);
        }
      }
    });
  }

  private void checkTasks() {
    long startTime = System.currentTimeMillis();
    Set<AsyncCloudTask> tasks = myExecutingTasks.keySet();
    int size = tasks.size();
    for (AsyncCloudTask task : tasks) {
      try {
        processSingleTask(task);
      } catch (Throwable th) {
        LOG.warnAndDebugDetails("An error occurred during checking " + task, th);
      }
    }
    if (size > 0) {
      LOG.debug("Check Tasks processed " + size + " tasks in " + (System.currentTimeMillis() - startTime) + " ms.");
    }
  }

  private void processSingleTask(@NotNull AsyncCloudTask task) {
    if (task.isDone()) {
      final TaskCallbackHandler handler = myExecutingTasks.get(task);
      try {
        final CloudTaskResult result = task.executeOrGetResult();
        handler.onComplete();
        if (result.isHasErrors()) {
          handler.onError(result.getThrowable());
        } else {
          handler.onSuccess();
        }
      } catch (Exception e) {
        LOG.warnAndDebugDetails("An error occurred while executing : '" + task + "'", e);
        handler.onError(e);
      }
      myExecutingTasks.remove(task);
      if (myLongTasks.remove(task) != null) {
        final long operationTime = System.currentTimeMillis() - task.getStartTime();
        LOG.info(String.format("Long operation finished: '%s' took %d seconds to execute", task.toString(), operationTime / 1000));
      }
    } else {
      final long operationTime = System.currentTimeMillis() - task.getStartTime();
      if (operationTime > LONG_TASK_TIME) {
        final Long lastTimeReported = myLongTasks.get(task);
        if (lastTimeReported == null || (System.currentTimeMillis() - lastTimeReported) > LONG_TASK_TIME) {
          LOG.info(String.format("Detected long running task:('%s', running for %d seconds)", task.toString(), operationTime / 1000));
          myLongTasks.put(task, System.currentTimeMillis());
        }
      }
    }
  }

  public void dispose(){
    ThreadUtil.shutdownNowAndWait(myExecutor, myPrefix);
    myExecutingTasks.clear();
  }

}
