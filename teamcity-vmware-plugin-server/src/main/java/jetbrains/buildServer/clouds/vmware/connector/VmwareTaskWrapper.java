/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Task;
import java.rmi.RemoteException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.clouds.base.connector.AsyncCloudTask;
import jetbrains.buildServer.clouds.base.connector.CloudTaskResult;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 6:22 PM
 */
public class VmwareTaskWrapper implements AsyncCloudTask {

  private final Callable<Task> myVmwareTask;
  private final AtomicBoolean myTaskCancelled;

  public VmwareTaskWrapper(@NotNull final Callable<Task> vmwareTask){
    myVmwareTask = vmwareTask;
    myTaskCancelled = new AtomicBoolean(false);
  }

  public Future<CloudTaskResult> executeAsync() {
    final Task task;
    try {
      task = myVmwareTask.call();
    } catch (final Exception e) {
      return createExceptionFuture(e);
    }
    return new Future<CloudTaskResult>() {
      public boolean cancel(final boolean mayInterruptIfRunning) {
        try {
          task.cancelTask();
          myTaskCancelled.set(true);
          return true;
        } catch (RemoteException e) {
          e.printStackTrace();
          return false;
        }
      }

      public boolean isCancelled() {
        return myTaskCancelled.get();
      }

      public boolean isDone() {
        try {
          final TaskInfo taskInfo = task.getTaskInfo();
          return (taskInfo.getState() == TaskInfoState.success || taskInfo.getState() == TaskInfoState.error);
        } catch (RemoteException e) {
          return false;
        }
      }

      public CloudTaskResult get() throws InterruptedException, ExecutionException {
        try {
          final String result = task.waitForTask();
          TaskInfo taskInfo = task.getTaskInfo();
          if (taskInfo.getState() == TaskInfoState.error){
            final LocalizedMethodFault error = taskInfo.getError();
            return new CloudTaskResult(true, result, new Exception(error== null ? "Unknown error" : error.getLocalizedMessage()));
          } else {
            return new CloudTaskResult(result);
          }
        } catch (RemoteException e) {
          return new CloudTaskResult(true, e.toString(), e);
        }
      }

      public CloudTaskResult get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final int mss = (int)TimeUnit.MILLISECONDS.convert(timeout, unit);
        try {
          final String result = task.waitForTask(mss / 2, mss / 2);
          TaskInfo taskInfo = task.getTaskInfo();
          if (taskInfo.getState() == TaskInfoState.error){
            final LocalizedMethodFault error = taskInfo.getError();
            return new CloudTaskResult(true, result, new Exception(error== null ? "Unknown error" : error.getLocalizedMessage()));
          } else {
            return new CloudTaskResult(result);
          }
        } catch (RemoteException e) {
          return new CloudTaskResult(true, e.toString(), e);
        }
      }
    };
  }

  private Future<CloudTaskResult> createExceptionFuture(final Exception e) {
    return new Future<CloudTaskResult>() {
      public boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
      }

      public boolean isCancelled() {
        return false;
      }

      public boolean isDone() {
        return true;
      }

      public CloudTaskResult get() throws InterruptedException, ExecutionException {
        throw new ExecutionException (e);
      }

      public CloudTaskResult get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new ExecutionException (e);
      }
    };
  }


}
