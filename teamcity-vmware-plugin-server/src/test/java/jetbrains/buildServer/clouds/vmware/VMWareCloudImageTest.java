package jetbrains.buildServer.clouds.vmware;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
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

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
  }

  public void checkImageType(){
    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.TEMPLATE, null, InstanceStatus.RUNNING, VMWareImageStartType.START).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.TEMPLATE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.START).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.TEMPLATE, null, InstanceStatus.RUNNING, VMWareImageStartType.CLONE).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.TEMPLATE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.CLONE).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.TEMPLATE, null, InstanceStatus.RUNNING, VMWareImageStartType.LINKED_CLONE).getStartType());

    assertEquals(VMWareImageStartType.LINKED_CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.TEMPLATE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.LINKED_CLONE).getStartType());

    assertEquals(VMWareImageStartType.START, new VMWareCloudImage(
      "myImage", VMWareImageType.INSTANCE, null, InstanceStatus.RUNNING, VMWareImageStartType.START).getStartType());

    assertEquals(VMWareImageStartType.START, new VMWareCloudImage(
      "myImage", VMWareImageType.INSTANCE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.START).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.INSTANCE, null, InstanceStatus.RUNNING, VMWareImageStartType.CLONE).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.INSTANCE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.CLONE).getStartType());

    assertEquals(VMWareImageStartType.CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.INSTANCE, null, InstanceStatus.RUNNING, VMWareImageStartType.LINKED_CLONE).getStartType());

    assertEquals(VMWareImageStartType.LINKED_CLONE, new VMWareCloudImage(
      "myImage", VMWareImageType.INSTANCE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.LINKED_CLONE).getStartType());

  }

  public void test_start_stop_instances(){
    final VMWareCloudImage image = new VMWareCloudImage("myImage", VMWareImageType.INSTANCE, "snapshot", InstanceStatus.RUNNING, VMWareImageStartType.START);
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
