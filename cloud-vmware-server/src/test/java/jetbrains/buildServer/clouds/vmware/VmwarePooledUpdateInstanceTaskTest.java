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

import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.VirtualMachine;
import java.io.File;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.impl.profile.CloudClientParametersImpl;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.tasks.VmwarePooledUpdateInstanceTask;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateInstanceTask;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by sergeypak on 27/10/2016.
 */
@Test
public class VmwarePooledUpdateInstanceTaskTest extends BaseTestCase {

  protected static final String PROJECT_ID = "project123";
  protected static final String PROFILE_ID = "cp1";
  protected static final String TEST_SERVER_UUID = "1234-5678-9012";
  private File myIdxStorage;
  private FakeApiConnector myFakeApiConnector;
  private MyExposingVmwareUpdateTaskManager myTaskManager;


  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdxStorage = createTempDir();
    FakeModel.instance().addDatacenter("dc");
    FakeModel.instance().addFolder("cf").setParent("dc", Datacenter.class);
    FakeModel.instance().addResourcePool("rp").setParentFolder("cf");
    FakeModel.instance().addVM("image1").setParentFolder("cf");
    FakeModel.instance().addVM("image2").setParentFolder("cf");
    FakeModel.instance().addVM("image_template").setParentFolder("cf");
    FakeModel.instance().addVMSnapshot("image2", "snap");
    FakeModel.instance().getCustomizationSpecs().put("someCustomization", new CustomizationSpec());
    FakeModel.instance().getCustomizationSpecs().put("linux", new CustomizationSpec());
    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID, null);
    setInternalProperty("teamcity.vsphere.instance.status.update.delay.ms", "250");
    myTaskManager = new MyExposingVmwareUpdateTaskManager();
  }

  public void check_called_once() throws MalformedURLException {
    final AtomicBoolean listAllCanBeCalled = new AtomicBoolean();
    final AtomicBoolean listAllCalledOnce = new AtomicBoolean();

    final AtomicBoolean getByNameCanBeCalled = new AtomicBoolean();
    final AtomicBoolean getByNameCalledOnce = new AtomicBoolean();

    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID, null) {

      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        if (!listAllCanBeCalled.get()) {
          fail("Shouldn't be called");
        }
        assertFalse(listAllCalledOnce.get());
        listAllCalledOnce.set(true);
        return super.findAllEntitiesAsMapOld(instanceType);
      }

      @Override
      protected Map<String, VirtualMachine> searchVMsByNames(@NotNull final Collection<String> names, @Nullable final Datacenter dc) throws VmwareCheckedCloudException {
        if (!getByNameCanBeCalled.get()) {
          fail("Shouldn't be called");
        }
        assertFalse(getByNameCalledOnce.get());
        getByNameCalledOnce.set(true);
        return super.searchVMsByNames(names, dc);
      }
    };

    final CloudClientParameters clientParameters1 = VmwareTestUtils.getClientParameters(PROJECT_ID, "[{sourceVmName:'image1', behaviour:'START_STOP'}]");
    final VMWareCloudClient client1 = new MyClient(clientParameters1, null);


    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));
    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);

    final CloudClientParameters clientParameters3 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{'source-id':'image_template',sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
      "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"));
    final VMWareCloudClient client3 = new MyClient(clientParameters3, null);

    final VmwareUpdateInstanceTask task1 = myTaskManager.createUpdateTask(myFakeApiConnector, client1);
    final VmwareUpdateInstanceTask task2 = myTaskManager.createUpdateTask(myFakeApiConnector, client2);
    final VmwareUpdateInstanceTask task3 = myTaskManager.createUpdateTask(myFakeApiConnector, client3);

    listAllCanBeCalled.set(true);
    listAllCalledOnce.set(false);
    getByNameCalledOnce.set(false);
    getByNameCanBeCalled.set(true);
    task1.run();
    task2.run();
    task3.run();
    assertTrue(listAllCalledOnce.get());
    assertTrue(getByNameCalledOnce.get());
  }

  public void check_cleared_after_dispose(){
    final CloudClientParameters clientParameters1 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,"[{sourceVmName:'image1', behaviour:'START_STOP'}]"));
    final VMWareCloudClient client1 = new MyClient(clientParameters1, null);


    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);

    final AtomicInteger expectedImagesCount = new AtomicInteger();
    final AtomicBoolean hasRun = new AtomicBoolean(false);
    final AtomicReference<Collection<VmwareCloudImage>> images2process = new AtomicReference<>();

    myTaskManager = new MyExposingVmwareUpdateTaskManager(){
      @Override
      protected VmwarePooledUpdateInstanceTask createNewPooledTask(@NotNull final VMWareApiConnector connector, @NotNull final VMWareCloudClient client) {
        return new VmwarePooledUpdateInstanceTask(connector, client){
          @Override
          public void run() {
            final Collection<VmwareCloudImage> images = getImages();
            images2process.set(images);
            assertEquals(expectedImagesCount.get(), images.size());
            hasRun.set(true);
          }
        };
      }
    };

    myTaskManager.createUpdateTask(myFakeApiConnector, client1);
    VmwareUpdateInstanceTask task = myTaskManager.createUpdateTask(myFakeApiConnector, client2);

    client1.dispose(); // the base task will remain
    expectedImagesCount.set(1);
    task.run();

    assertTrue(hasRun.get());
    assertEquals(1, images2process.get().size());
    assertEquals("image2", images2process.get().iterator().next().getName());

    hasRun.set(false);
    client2.dispose();
    task.run();
    assertFalse(hasRun.get());
  }

  public void check_cleared_after_dispose_2() throws MalformedURLException {
    final AtomicBoolean canBeCalled = new AtomicBoolean();
    final AtomicInteger callsCount = new AtomicInteger();

    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID, null) {
      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        processChecks();
        return Collections.emptyMap();
      }

      private void processChecks() {
        if (!canBeCalled.get()) {
          fail("Shouldn't be called");
        }
        assertTrue(callsCount.get() < 2);
        callsCount.incrementAndGet();
      }

      @Override
      protected Map<String, VirtualMachine> searchVMsByNames(@NotNull final Collection<String> names, @Nullable final Datacenter dc) throws VmwareCheckedCloudException {
        processChecks();
        return Collections.emptyMap();
      }
    };

    final CloudClientParameters clientParameters1 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,"[{sourceVmName:'image1', behaviour:'START_STOP'}]"));
    final VMWareCloudClient client1 = new MyClient(clientParameters1, null);


    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);
    final VmwareUpdateInstanceTask task1 = myTaskManager.createUpdateTask(myFakeApiConnector, client1);


    canBeCalled.set(true);
    callsCount.set(0);
    task1.run();
    assertTrue(callsCount.get() > 0);

    client1.dispose();
    final VmwareUpdateInstanceTask task2 = myTaskManager.createUpdateTask(myFakeApiConnector, client2);
    canBeCalled.set(true);
    callsCount.set(0);
    task2.run();
    assertTrue(callsCount.get() > 0);


  }

  public void concurrent_create_dispose() throws MalformedURLException {
    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);
    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID);

    final VmwareUpdateInstanceTask[] tasks = new VmwareUpdateInstanceTask[2];


    for (int i=0; i<1000; i++){
      final int finalI = i;
      Thread thread1 = new Thread(new Runnable() {
        @Override
        public void run() {
          final VmwareUpdateInstanceTask updateTask = myTaskManager.createUpdateTask(myFakeApiConnector, client2);
          tasks[finalI % 2] = updateTask;
        }
      });
      Thread thread2 = new Thread(new Runnable() {
        @Override
        public void run() {
          VmwareUpdateInstanceTask task = tasks[(finalI + 1) % 2];
          if (task == null)
            return;

          task.clientDisposing(client2);
        }
      });
      thread1.start();
      thread2.start();
      new WaitFor(1000) {
        @Override
        protected boolean condition() {
          try {
            thread1.join(100);
            thread2.join(100);
          } catch (InterruptedException e) {
            fail("Can't join!");
          }
          return !thread1.isAlive() && !thread2.isAlive();
        }
      };
      assertTrue("Blocked concurrent_create_dispose at i=" + i, !thread1.isAlive() && !thread2.isAlive());
      assertTrue("Should cleanup tasks", myTaskManager.getPoolSize() < 2);
    }

  }

  public void concurrent_create_run() throws MalformedURLException {
    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);
    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID);

    final VmwareUpdateInstanceTask[] tasks = new VmwareUpdateInstanceTask[2];


    for (int i=0; i<1000; i++){
      final int finalI = i;
      Thread thread1 = new Thread(new Runnable() {
        @Override
        public void run() {
          final VmwareUpdateInstanceTask updateTask = myTaskManager.createUpdateTask(myFakeApiConnector, client2);
          tasks[finalI % 2] = updateTask;
        }
      });
      Thread thread2 = new Thread(new Runnable() {
        @Override
        public void run() {
          VmwareUpdateInstanceTask task = tasks[(finalI + 1) % 2];
          if (task == null)
            return;

          task.run();
        }
      });
      thread1.start();
      thread2.start();
      new WaitFor(1000) {
        @Override
        protected boolean condition() {
          try {
            thread1.join(100);
            thread2.join(100);
          } catch (InterruptedException e) {
            fail("Can't join!");
          }
          return !thread1.isAlive() && !thread2.isAlive();
        }
      };
      assertTrue("Blocked concurrent_create_run at i=" + i, !thread1.isAlive() && !thread2.isAlive());
      VmwareUpdateInstanceTask task = tasks[(finalI + 1) % 2];
      if (task != null){
        task.clientDisposing(client2);
      }
      assertTrue("Should cleanup tasks", myTaskManager.getPoolSize() < 2);
    }

  }

  public void concurrent_run_dispose() throws MalformedURLException {
    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), VmwareTestUtils.getImageParameters(PROJECT_ID,
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);
    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID);


    for (int i=0; i<1000; i++){
      final VmwareUpdateInstanceTask updateTask = myTaskManager.createUpdateTask(myFakeApiConnector, client2);
      Thread thread1 = new Thread(new Runnable() {
        @Override
        public void run() {
          updateTask.run();
        }
      });
      Thread thread2 = new Thread(new Runnable() {
        @Override
        public void run() {
          updateTask.clientDisposing(client2);
        }
      });
      thread1.start();
      thread2.start();
      new WaitFor(1000) {
        @Override
        protected boolean condition() {
          try {
            thread1.join(100);
            thread2.join(100);
          } catch (InterruptedException e) {
            fail("Can't join!");
          }
          return !thread1.isAlive() && !thread2.isAlive();
        }
      };
      assertTrue("Blocked concurrent_run_dispose at i=" + i, !thread1.isAlive() && !thread2.isAlive());
      assertTrue("Should cleanup tasks", myTaskManager.getPoolSize() < 2);
    }

  }


  private class MyClient extends VMWareCloudClient{

    private final Map<VMWareCloudClient, Boolean> myGetImagesCalled;

    public MyClient(@NotNull final CloudClientParameters params,
                    Map<VMWareCloudClient, Boolean> getImagesCalled) {
      this(VmwareTestUtils.createProfileFromProps(params), getImagesCalled);
    }
    public MyClient(@NotNull final CloudProfile profile,
                    Map<VMWareCloudClient, Boolean> getImagesCalled) {
      super(profile, myFakeApiConnector, myTaskManager, myIdxStorage);
      myGetImagesCalled = getImagesCalled;
      populateImagesData(VMWareCloudClientFactory.parseImageDataInternal(profile.getParameters()));
    }

    @NotNull
    @Override
    public Collection<VmwareCloudImage> getImages() throws CloudException {
      if (myGetImagesCalled != null)
        assertNull(myGetImagesCalled.put(this, true));

      return super.getImages();
    }

    @Override
    public void populateImagesData(@NotNull final Collection<VmwareCloudImageDetails> imageDetails) {
      imageDetails.stream().forEach((key) -> myImageMap.put(key.getSourceId(), checkAndCreateImage(key)));
    }
  }

  private class MyExposingVmwareUpdateTaskManager extends VmwareUpdateTaskManager{

    public int getPoolSize(){
      return myUpdateTasks.size();
    }

    @Override
    protected VmwarePooledUpdateInstanceTask createNewPooledTask(@NotNull final VMWareApiConnector connector, @NotNull final VMWareCloudClient client) {
      return new VmwarePooledUpdateInstanceTask(connector, client){
        @Override
        public void addClient(@NotNull final VMWareCloudClient client) {
          assertFalse("Shouldn't try to add client to task scheduled for clean-up", 1 == getSpecialState());
          super.addClient(client);
        }
      };
    }
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    System.getProperties().remove("teamcity.vsphere.instance.status.update.delay.ms");
    FakeModel.instance().clear();
    super.tearDown();
  }
}
