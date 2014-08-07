package jetbrains.buildServer.clouds.vmware;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 5/20/2014
 *         Time: 3:16 PM
 */
@Test
public class VmwareCloudImageTest extends BaseTestCase {

  private CloudAsyncTaskExecutor myStatusTask;
  private VMWareApiConnector myApiConnector;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myStatusTask = new CloudAsyncTaskExecutor("Test-vmware");
    myApiConnector = new FakeApiConnector();
    myStatusTask.start();
  }

  public void checkImageType() throws MalformedURLException, RemoteException {
    assertEquals(VMWareImageStartType.CLONE, new VmwareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask, VMWareImageStartType.START, 1).getStartType());

    assertEquals(VMWareImageStartType.START, new VmwareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.START, 1).getStartType());

    assertEquals(VMWareImageStartType.ON_DEMAND_CLONE, new VmwareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.ON_DEMAND_CLONE, 1).getStartType());
  }


  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

}
