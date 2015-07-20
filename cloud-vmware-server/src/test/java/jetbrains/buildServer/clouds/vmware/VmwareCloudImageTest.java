package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.WaitFor;
import com.intellij.util.containers.ConcurrentHashSet;
import com.vmware.vim25.mo.Task;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
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
  private VmwareCloudImage myImage;
  private VmwareCloudImageDetails myImageDetails;
  private File myIdxStorage;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myStatusTask = new CloudAsyncTaskExecutor("Test-vmware");
    myApiConnector = new FakeApiConnector();
    myIdxStorage = createTempDir();
    myImageDetails = new VmwareCloudImageDetails("imageNickname", "mySource", "mySourceSnapshot"
      , "myFolder", "myPool", CloneBehaviour.FRESH_CLONE, 5);
    FakeModel.instance().addVM("mySource");
    FakeModel.instance().addVMSnapshot("mySource", "mySourceSnapshot");
    FakeModel.instance().addFolder("myFolder");
    FakeModel.instance().addResourcePool("myPool");
    myImage = new VmwareCloudImage(myApiConnector, myImageDetails, myStatusTask, myIdxStorage);
  }

  public void check_clone_name_generation(){
    for (int i=0; i<10; i++){
      assertEquals(String.format("%s-%d", myImageDetails.getNickname(), i + 1), myImage.generateNewVmName());
    }
    FileUtil.delete(myIdxStorage);
    final String newName = myImage.generateNewVmName();
    assertTrue(newName.startsWith(myImage.getName()));
    final int i = Integer.parseInt(newName.substring(myImage.getName().length() + 1));
    assertTrue(i > 100000);
  }

  public void check_can_start_new_instance_limits() throws RemoteException, InterruptedException {
    final CloudInstanceUserData data = new CloudInstanceUserData("aaa", "bbbb", "localhost", 10000l, "profileDescr", Collections.<String, String>emptyMap());
    assertTrue(myImage.canStartNewInstance());
    myImage.startNewInstance(data);
    assertTrue(myImage.canStartNewInstance());
    myImage.startNewInstance(data);
    assertTrue(myImage.canStartNewInstance());
    myImage.startNewInstance(data);
    assertTrue(myImage.canStartNewInstance());
    myImage.startNewInstance(data);
    assertTrue(myImage.canStartNewInstance());
    final VmwareCloudInstance instance2Stop = myImage.startNewInstance(data);
    assertFalse(myImage.canStartNewInstance());
    new WaitFor(5*1000){

      @Override
      protected boolean condition() {
        return instance2Stop.getStatus() == InstanceStatus.RUNNING;
      }
    };
    final FakeVirtualMachine vm2Stop = FakeModel.instance().getVirtualMachine(instance2Stop.getName());
    final String result = vm2Stop.powerOffVM_Task().waitForTask();
    assertEquals(Task.SUCCESS, result);
    instance2Stop.setStatus(InstanceStatus.STOPPED);
    assertTrue(myImage.canStartNewInstance());
    System.setProperty(VmwareConstants.CONSIDER_STOPPED_VMS_LIMIT, "true");
    assertFalse(myImage.canStartNewInstance());
    System.getProperties().remove(VmwareConstants.CONSIDER_STOPPED_VMS_LIMIT);
    assertTrue(myImage.canStartNewInstance());
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

}
