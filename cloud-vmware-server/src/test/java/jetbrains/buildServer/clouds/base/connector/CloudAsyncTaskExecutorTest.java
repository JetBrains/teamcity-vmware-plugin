package jetbrains.buildServer.clouds.base.connector;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by Sergey.Pak on 3/11/2016.
 */
@Test
public class CloudAsyncTaskExecutorTest extends BaseTestCase {

  private CloudAsyncTaskExecutor myCloudAsyncTaskExecutor;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCloudAsyncTaskExecutor = new CloudAsyncTaskExecutor("Test executor");
  }

  public void should_not_stuck_all_queue_on_single_task_failure() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(2);
    final MyAsyncCloudTask task1 = new MyAsyncCloudTask("task1", new CloudTaskResult(), 1000, null);
    final MyAsyncCloudTask task3 = new MyAsyncCloudTask("task3", new CloudTaskResult(), 1000, null);
    final TaskCallbackHandler countdownHandler = new TaskCallbackHandler() {
      @Override
      public void onComplete() {
        latch.countDown();
      }
    };
    myCloudAsyncTaskExecutor.executeAsync(task1, countdownHandler);
    myCloudAsyncTaskExecutor.executeAsync(
      new MyAsyncCloudTask("task2", new CloudTaskResult(), 5000, new NullPointerException()),
      countdownHandler);
    myCloudAsyncTaskExecutor.executeAsync(task3, countdownHandler);

    assertTrue(latch.await(2, TimeUnit.SECONDS));
  }

  private static class MyAsyncCloudTask implements AsyncCloudTask {

    private String myName;
    private long myStartDate;
    private CloudTaskResult myResult;
    private final long myTaskTime;
    private final Throwable myException;

    public MyAsyncCloudTask(String name, CloudTaskResult result, long time, Throwable exception) {
      myName = name;
      myResult = result;
      myTaskTime = time;
      myException = exception;
      myStartDate = System.currentTimeMillis();
    }
    @Override
    public CloudTaskResult executeOrGetResult() {
      try {
        final long millis = myTaskTime - (System.currentTimeMillis() - myStartDate);
        if (millis > 0) {
          Thread.sleep(millis);
        }
        throwMyExceptionIfNecessary();
      } catch (Exception e) {
        return createErrorTaskResult(e);
      }
      return myResult;
    }

    private void throwMyExceptionIfNecessary() throws ExecutionException, InterruptedException {
      if (myException != null) {
        if (myException instanceof ExecutionException) {
          throw (ExecutionException)myException;
        } else if (myException instanceof InterruptedException) {
          throw (InterruptedException)myException;
        } else {
          throw new RuntimeException(myException);
        }
      }
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public long getStartTime() {
      return myStartDate;
    }

    private CloudTaskResult createErrorTaskResult(Exception e){
      return new CloudTaskResult(true, e.toString(), e);
    }

    @Override
    public boolean isDone() {
      final long millis = myTaskTime - (System.currentTimeMillis() - myStartDate);
      try {
        throwMyExceptionIfNecessary();
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
      return millis < 0;
    }
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    myCloudAsyncTaskExecutor.dispose();
    super.tearDown();
  }
}
