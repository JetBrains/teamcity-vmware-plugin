package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.WaitFor;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Task;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.clouds.server.impl.profile.CloudClientParametersImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudImageDataImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudImageParametersImpl;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
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

  private CloudAsyncTaskExecutor myTaskExecutor;
  private VMWareApiConnector myApiConnector;
  private VmwareCloudImage myImage;
  private VmwareCloudImageDetails myImageDetails;
  private File myIdxStorage;
  private UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> myUpdateTask;
  private VMWareCloudClient myCloudClient;
  private CloudProfile myProfile;


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    FakeModel.instance().clear();
    myTaskExecutor = new CloudAsyncTaskExecutor("Test-vmware");
    myApiConnector = new FakeApiConnector(VmwareCloudIntegrationTest.TEST_SERVER_UUID, VmwareCloudIntegrationTest.PROFILE_ID);
    myIdxStorage = createTempDir();

    myProfile = VmwareTestUtils.createProfileFromProps(new CloudClientParametersImpl(Collections.emptyMap(), Collections.emptyList()));

    Map<String, String> params = new HashMap<>();
    params.put("nickname", "imageNickname");
    params.put("sourceVmName", "srcVM");
    params.put("snapshot", "srcVMSnap");
    params.put("folder", "folderId");
    params.put("pool", "rpId");
    params.put("behaviour", CloneBehaviour.FRESH_CLONE.toString());
    params.put("maxInstances", "5");
    CloudImageParameters imageParameters = new CloudImageParametersImpl(new CloudImageDataImpl(params), myProfile.getProjectId(), UUID.randomUUID().toString());

    myImageDetails = new VmwareCloudImageDetails(imageParameters);

    FakeModel.instance().addDatacenter("dc2");
    FakeModel.instance().addFolder("folderId").setParent("dc2", Datacenter.class);
    FakeModel.instance().addVM("srcVM").setParentFolder("folderId");
    FakeModel.instance().addResourcePool("rpId").setParentFolder("folderId");

    FakeModel.instance().addVMSnapshot("srcVM", "srcVMSnap");

    myImage = new VmwareCloudImage(myApiConnector, myImageDetails, myTaskExecutor, myIdxStorage, myProfile);

    myCloudClient = new VMWareCloudClient(myProfile, myApiConnector, new VmwareUpdateTaskManager(), createTempDir());
    myCloudClient.populateImagesData(Collections.singletonList(myImageDetails));
    myUpdateTask = new UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient>(myApiConnector, myCloudClient, 10*1000, false);
  }

  public void check_clone_name_generation(){
    for (int i=0; i<10; i++){
      assertEquals(String.format("%s-%d", myImageDetails.getSourceId(), i + 1), myImage.generateNewVmName());
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

  public void terminate_instance_if_cant_reconfigure() throws IOException {
    final CloudInstanceUserData data = new CloudInstanceUserData("aaa", "bbbb", "localhost", 10000l, "profileDescr", Collections.<String, String>emptyMap());
    final AtomicBoolean stopInstanceCalled = new AtomicBoolean();
    myApiConnector = new FakeApiConnector(VmwareCloudIntegrationTest.TEST_SERVER_UUID, VmwareCloudIntegrationTest.PROFILE_ID){
      @Override
      public Task reconfigureInstance(@NotNull final VmwareCloudInstance instance, @NotNull final String agentName, @NotNull final CloudInstanceUserData userData)
        throws VmwareCheckedCloudException {
        return FakeVirtualMachine.failureTask();
      }

      @Override
      public Task stopInstance(@NotNull final VmwareCloudInstance instance) {
        stopInstanceCalled.set(true);
        return super.stopInstance(instance);
      }
    };
    myImage = new VmwareCloudImage(myApiConnector, myImageDetails, myTaskExecutor, myIdxStorage, myProfile);

    myCloudClient = new VMWareCloudClient(myProfile, myApiConnector, new VmwareUpdateTaskManager(), createTempDir());
    myCloudClient.populateImagesData(Collections.singletonList(myImageDetails));

    myImage.startNewInstance(data);

    new WaitFor(1000){

      @Override
      protected boolean condition() {
        return stopInstanceCalled.get();
      }
    };

    assertTrue("Should have stopped if can't reconfigure", stopInstanceCalled.get());
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

}
