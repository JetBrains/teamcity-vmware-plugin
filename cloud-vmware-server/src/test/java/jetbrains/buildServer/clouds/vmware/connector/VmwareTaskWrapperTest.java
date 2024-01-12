

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.mo.Task;
import java.rmi.RemoteException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.base.connector.CloudTaskResult;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Created by Sergey.Pak on 3/11/2016.
 */
@Test
public class VmwareTaskWrapperTest extends BaseTestCase {


  public void should_report_isdone_for_error_tasks() throws ExecutionException, InterruptedException {
    throw new SkipException("works differently now");
    /*
    VmwareTaskWrapper taskWrapper = new VmwareTaskWrapper(new Callable<Task>() {
      @Override
      public Task call() throws Exception {
        return new Task(null, null){
          @Override
          public TaskInfo getTaskInfo() throws InvalidProperty, RuntimeFault, RemoteException {
            throw new RuntimeException("getTaskInfo exception");
          }

          @Override
          public String waitForTask() throws RuntimeFault, RemoteException, InterruptedException {
            throw new RuntimeException("getTaskInfo exception");
          }

          @Override
          public void cancelTask() throws RuntimeFault, RemoteException {
            throw new RuntimeException("getTaskInfo exception");
          }
        };
      }
    }, "myTask");
    final Future<CloudTaskResult> async = taskWrapper.executeOrGetResultAsync();
    int cnt = 0;
    while (!async.isDone()){
      cnt++;
      assertTrue(cnt < 5);
    }
    final CloudTaskResult result = async.get();
    assertTrue(result.isHasErrors());
    */
  }

}