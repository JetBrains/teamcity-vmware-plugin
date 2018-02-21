package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.WaitFor;
import java.io.File;
import java.util.*;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.clouds.server.impl.profile.CloudClientParametersImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudImageDataImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudImageParametersImpl;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeDatacenter;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.clouds.vmware.VmwareCloudIntegrationTest.PROFILE_ID;
import static jetbrains.buildServer.clouds.vmware.VmwareCloudIntegrationTest.TEST_SERVER_UUID;

/**
 * @author Sergey.Pak
 *         Date: 5/19/2014
 *         Time: 4:25 PM
 */
@Test
public class VmwareCloudInstanceTest extends BaseTestCase {

  private CloudAsyncTaskExecutor myStatusTask;
  private VMWareApiConnector myApiConnector;
  private VmwareCloudImage myImage;
  private VmwareCloudImageDetails myImageDetails;
  private File myIdxStorage;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myStatusTask = new CloudAsyncTaskExecutor("Test-vmware");
    myApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID);
    myIdxStorage = createTempDir();
    final Map<String, String> imgParams = new HashMap<>();
    imgParams.put("nickname", "imageNickname");
    imgParams.put("sourceVmName", "mySource");
    imgParams.put("snapshot", "mySourceSnapshot");
    imgParams.put("folder", "myFolder");
    imgParams.put("pool", "myPool");
    imgParams.put("behaviour", CloneBehaviour.FRESH_CLONE.toString());
    imgParams.put("maxInstances", "5");
    CloudImageParameters imageParameters = new CloudImageParametersImpl(new CloudImageDataImpl(imgParams), VmwareCloudIntegrationTest.PROJECT_ID, UUID.randomUUID().toString());
    myImageDetails = new VmwareCloudImageDetails(imageParameters);

    final CloudClientParameters clientParameters = new CloudClientParametersImpl(createMap(), createSet(imageParameters));

    final FakeDatacenter dc2 = FakeModel.instance().addDatacenter("dc2");
    FakeModel.instance().addFolder("myFolder").setParent(dc2);
    FakeModel.instance().addVM("mySource").setParentFolder("myFolder");
    FakeModel.instance().addVMSnapshot("mySource", "mySourceSnapshot");
    FakeModel.instance().addResourcePool("myPool");
    myImage = new VmwareCloudImage(myApiConnector,
                                   myImageDetails,
                                   myStatusTask,
                                   myIdxStorage,
                                   VmwareTestUtils.createProfileFromProps(clientParameters));
  }


  public void do_not_make_instance_start_date_earlier(){
    final CloudInstanceUserData data = new CloudInstanceUserData("aaa", "bbbb", "localhost", 10000l, "profileDescr", Collections.<String, String>emptyMap());

    final VmwareCloudInstance instance = myImage.startNewInstance(data);

    new WaitFor(5 * 1000){

      @Override
      protected boolean condition() {
        return instance.getStatus() == InstanceStatus.RUNNING;
      }
    };

    FakeVirtualMachine fakeVM = FakeModel.instance().getVirtualMachine(instance.getName());
    final Calendar calendarInstance = Calendar.getInstance();
    calendarInstance.set(2001, 1, 1);
    fakeVM.setBootTime(calendarInstance);
    calendarInstance.set(2001, 1, 1);
    final Calendar instance2 = Calendar.getInstance();
    instance2.set(2002, 1, 1);
    assertTrue(instance.getStartedTime().after(instance2.getTime()));
  }



  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }
}
