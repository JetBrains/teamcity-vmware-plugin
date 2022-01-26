/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.WaitFor;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Task;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;
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
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.util.ThreadUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

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

    myCloudClient = new VMWareCloudClient(myProfile, myApiConnector, new VmwareUpdateTaskManager(), myIdxStorage);
    myCloudClient.populateImagesData(Collections.singletonList(myImageDetails));
    myUpdateTask = new UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient>(myApiConnector, myCloudClient, 10*1000, false);
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

  @TestFor(issues = "TW-54729")
  public void check_name_generator_doesnt_use_disk() throws IOException, InterruptedException {
    final Set<String> generatedNames = new HashSet<>();
    Thread nameGenerator = new Thread(()->{
      for (int i=0; i<10000; i++){
        if (Thread.currentThread().isInterrupted())
          break;
        generatedNames.add(myImage.generateNewVmName());
      }
    });
    nameGenerator.start();
    new WaitFor(500){

      @Override
      protected boolean condition() {
        return generatedNames.size() == 10000;
      }
    };
    assertEquals(10000, generatedNames.size());
    nameGenerator.join();
    assertEquals("1", FileUtil.readText(new File(myIdxStorage, myImage.getImageDetails().getSourceId() + ".idx")));
    myImage.storeIdx();
    assertEquals("10001", FileUtil.readText(new File(myIdxStorage, myImage.getImageDetails().getSourceId() + ".idx")));
  }

  public void should_generate_unique_name() throws IOException {
    VmwareCloudImage sameImage = new VmwareCloudImage(myApiConnector, myImageDetails, myTaskExecutor, myIdxStorage, myProfile){
      @Override
      protected Set<String> getInstanceIds() {
        return createSet("imageNickname-1", "imageNickname-3");
      }
    };
    assertEquals("1", FileUtil.readText(new File(myIdxStorage, myImage.getImageDetails().getSourceId() + ".idx")));
    assertEquals("imageNickname-2", sameImage.generateNewVmName());
    assertEquals("imageNickname-4", sameImage.generateNewVmName());

  }

  public void should_store_idx_on_dispose() throws IOException {
    new WaitFor(500){

      @Override
      protected boolean condition() {
        return myCloudClient.isInitialized();
      }
    };
    VmwareCloudImage img = myCloudClient.getImages().iterator().next();
    assertEquals("imageNickname-1", img.generateNewVmName());
    File file = new File(myIdxStorage, img.getImageDetails().getSourceId() + ".idx");
    assertEquals("1", FileUtil.readText(file));
    myCloudClient.dispose();
    assertEquals( "2", FileUtil.readText(file));
  }

  public void reset_idx_when_file_unreadable() throws IOException {
    final File idxFile = new File(myIdxStorage, myImage.getImageDetails().getSourceId() + ".idx");
    FileUtil.writeFileAndReportErrors(idxFile, "aaaaaaaaaasdasfdfsgfsgeaweewq");
    VmwareCloudImage sameImage = new VmwareCloudImage(myApiConnector, myImageDetails, myTaskExecutor, myIdxStorage, myProfile){
      @Override
      protected Set<String> getInstanceIds() {
        return createSet("imageNickname-1", "imageNickname-3");
      }
    };
    assertEquals("imageNickname-2", sameImage.generateNewVmName());
    assertEquals("imageNickname-4", sameImage.generateNewVmName());

  }

  @TestFor(issues = "TW-73293")
  public void check_name_generator() throws IOException {
    FileUtil.writeFile(new File(myIdxStorage, myImage.getImageDetails().getSourceId() + ".idx"), "2", StandardCharsets.UTF_8);
    final CloudInstanceUserData data = new CloudInstanceUserData("aaa", "bbbb", "localhost", 10000l, "profileDescr", Collections.<String, String>emptyMap());
    VmwareCloudImage sameImage = new VmwareCloudImage(myApiConnector, myImageDetails, myTaskExecutor, myIdxStorage, myProfile);
    VmwareCloudInstance i1 = sameImage.startNewInstance(data);
    VmwareCloudInstance i2 = sameImage.startNewInstance(data);
    then(sameImage.getInstances()).contains(i1, i2);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

}
