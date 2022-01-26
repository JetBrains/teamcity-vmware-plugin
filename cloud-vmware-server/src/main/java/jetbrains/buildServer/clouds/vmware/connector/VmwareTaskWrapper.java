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

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Task;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.clouds.base.connector.AsyncCloudTask;
import jetbrains.buildServer.clouds.base.connector.CloudTaskResult;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.util.impl.Lazy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 6:22 PM
 */
public class VmwareTaskWrapper implements AsyncCloudTask {

  private final Callable<Task> myVmwareTask;
  private final String myTaskName;
  private volatile long myStartTime;
  private final Lazy<CloudTaskResult> myResultLazy;
  private final AtomicBoolean myIsDone = new AtomicBoolean(false);

  public VmwareTaskWrapper(@NotNull final Callable<Task> vmwareTask, String taskName){
    myVmwareTask = vmwareTask;
    myTaskName = taskName;
    myResultLazy = new Lazy<CloudTaskResult>() {
      @Override
      protected CloudTaskResult createValue() {
        try {
          myStartTime = System.currentTimeMillis();
          return getResult(myVmwareTask.call());
        } catch (Exception e) {
          return createErrorTaskResult(e);
        } finally {
          myIsDone.set(true);
        }
      }
    };
  }

  @Override
  public CloudTaskResult executeOrGetResult() {
    return myResultLazy.getValue();
  }

  @NotNull
  public String getName() {
    return myTaskName;
  }

  public long getStartTime() {
    return myStartTime;
  }

  @Override
  public boolean isDone() {
    return myIsDone.get();
  }

  @NotNull
  private CloudTaskResult getResult(final Task task) {
    try {
      final String result = task.waitForTask();
      final TaskInfo taskInfo = task.getTaskInfo();
      if (taskInfo.getState() == TaskInfoState.error){
        final LocalizedMethodFault error = taskInfo.getError();
        return new CloudTaskResult(true, result, new Exception(error== null ? "Unknown error" : error.getLocalizedMessage()));
      } else {
        return new CloudTaskResult(result);
      }
    } catch (Exception e) {
      return new CloudTaskResult(true, e.toString(), e);
    }
  }

  private CloudTaskResult createErrorTaskResult(Exception e){
    return new CloudTaskResult(true, e.toString(), e);
  }

  @Override
  public String toString() {
    return "VmwareTaskWrapper{" +
           "TaskName='" + myTaskName + '\'' +
           ",StartTime=" + LogUtil.describe(new Date(myStartTime)) +
           '}';
  }
}
