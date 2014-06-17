package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.Task;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 6/9/2014
 *         Time: 4:26 PM
 */
public class TaskStatusUpdater implements Runnable {
  private static final Logger LOG = Logger.getInstance(TaskStatusUpdater.class.getName());


  private final ConcurrentMap<Task, TaskCallbackHandler> myTasks;

  public TaskStatusUpdater() {
    myTasks = new ConcurrentHashMap<Task, TaskCallbackHandler>();
  }

  public void submit(@Nullable final Task task, @NotNull final TaskCallbackHandler handler) {
    LOG.info("Submitted new task. Handler: " + handler.getClass());
    if (task != null) {
      myTasks.put(task, handler);
    } else {
      handler.onComplete();
      handler.onSuccess();
    }
  }

  public void run() {
    for (Map.Entry<Task, TaskCallbackHandler> entry : myTasks.entrySet()) {
      final Task task = entry.getKey();
      final TaskInfo taskInfo;
      try {
        taskInfo = task.getTaskInfo();
        if (taskInfo.getState() == TaskInfoState.queued || taskInfo.getState() == TaskInfoState.running) {
          continue;
        }
        final TaskCallbackHandler handler = entry.getValue();
        LOG.info("Task completed. Handler: " + handler.getClass());
        handler.onComplete();
        if (taskInfo.getState() == TaskInfoState.success) {
          handler.onSuccess();
        } else {
          handler.onError(taskInfo.getError());
        }
      } catch (Exception e) {
        LOG.error("Unable to get taskInfo: " + e.toString());
        LOG.debug("Unable to get taskInfo", e);
      }
      myTasks.remove(task);
    }
  }

  public static interface TaskCallbackHandler {
    void onSuccess();

    void onError(LocalizedMethodFault fault);

    void onComplete();
  }

  public static class TaskCallbackAdapter implements TaskCallbackHandler {

    public void onSuccess() {

    }

    public void onError(final LocalizedMethodFault fault) {

    }

    public void onComplete() {

    }
  }
}
