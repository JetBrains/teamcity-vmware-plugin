package jetbrains.buildServer.clouds.base.connector;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.*;
import jetbrains.buildServer.util.NamedThreadFactory;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 3:51 PM
 */
public class CloudAsyncTaskExecutor {

  private static final Logger LOG = Logger.getInstance(CloudAsyncTaskExecutor.class.getName());

  private ScheduledExecutorService myExecutor;
  private final ConcurrentMap<Future<CloudTaskResult>, TaskCallbackHandler> myExecutingTasks;

  public CloudAsyncTaskExecutor() {
    myExecutingTasks = new ConcurrentHashMap<Future<CloudTaskResult>, TaskCallbackHandler>();
  }

  public void start(String clientName){
    myExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(clientName));
    myExecutor.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        checkTasks();
      }
    }, 0, 300, TimeUnit.MILLISECONDS);
  }

  public void executeAsync(final AsyncCloudTask operation) {
    executeAsync(operation, TaskCallbackHandler.DUMMY_HANDLER);
  }

  public void executeAsync(final AsyncCloudTask operation, final TaskCallbackHandler callbackHandler) {
    final Future<CloudTaskResult> future = operation.executeAsync();
    myExecutingTasks.put(future, callbackHandler);
  }

  private void checkTasks() {
    for (Future<CloudTaskResult> executingTask : myExecutingTasks.keySet()) {
      if (executingTask.isDone()) {
        final TaskCallbackHandler handler = myExecutingTasks.get(executingTask);
        try {
          final CloudTaskResult result = executingTask.get();
          myExecutingTasks.remove(executingTask);
          handler.onComplete();
          if (result.isHasErrors()) {
            handler.onError(result.getThrowable());
          } else {
            handler.onSuccess();
          }
        } catch (Exception e) {

        }
      }
    }
  }

  public void dispose(){
    myExecutor.shutdown();
    try {
      myExecutor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {}
    myExecutingTasks.clear();
  }

}
