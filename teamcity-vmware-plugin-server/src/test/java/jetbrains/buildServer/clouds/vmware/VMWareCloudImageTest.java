package jetbrains.buildServer.clouds.vmware;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.TaskStatusUpdater;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 5/20/2014
 *         Time: 3:16 PM
 */
@Test
public class VMWareCloudImageTest extends BaseTestCase {

  private TaskStatusUpdater myStatusTask;
  private VMWareApiConnector myApiConnector;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myStatusTask = new TaskStatusUpdater();
    myApiConnector = new FakeApiConnector();
  }

  public void checkImageType() throws MalformedURLException, RemoteException {
    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask, VMWareImageStartType.START, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask, VMWareImageStartType.START, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask, VMWareImageStartType.CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.LINKED_CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.LINKED_CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.TEMPLATE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.LINKED_CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.START, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.START, 1).getStartType());

    assertEquals(VMWareImageStartType.START, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.START, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", null, InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.LINKED_CLONE, 1).getStartType());

    assertEquals(VMWareImageStartType.LINKED_CLONE, new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask,VMWareImageStartType.LINKED_CLONE, 1).getStartType());

  }

  public void test_start_stop_instances(){
    final VMWareCloudImage image = new VMWareCloudImage(myApiConnector,
      "myImage", VMWareImageType.INSTANCE, "folder", "pool", "snapshot", InstanceStatus.RUNNING, myStatusTask, VMWareImageStartType.START, 1);
    assertEquals(1, image.getInstances().size());
    final CloudInstance instance = image.getInstances().iterator().next();
    assertEquals(InstanceStatus.RUNNING,instance.getStatus());

    image.instanceStopped(instance.getName());
    assertEquals(InstanceStatus.STOPPED,image.getInstances().iterator().next().getStatus());
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

}
