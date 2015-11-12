package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.WaitFor;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Task;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.server.impl.*;
import jetbrains.buildServer.clouds.server.instances.CloudEventDispatcher;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:04 PM
 */
@Test
public class VmwareCloudIntegrationTest extends BaseTestCase {

  private static final String PROFILE_ID = "cp1";

  private VMWareCloudClient myClient;
  private FakeApiConnector myFakeApi;
  private CloudClientParameters myClientParameters;
  private File myIdxStorage;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myIdxStorage = createTempDir();

    System.setProperty("teamcity.vsphere.instance.status.update.delay.ms", "250");
    myClientParameters = new CloudClientParameters();
    myClientParameters.setParameter("serverUrl", "http://localhost:8080");
    myClientParameters.setParameter("username", "un");
    myClientParameters.setParameter("password", "pw");
    myClientParameters.setParameter("vmware_images_data", "[{sourceName:'image1', behaviour:'START_STOP'}," +
                                                          "{sourceName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                          "{sourceName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                          "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]");

    myFakeApi = new FakeApiConnector();
    FakeModel.instance().addDatacenter("dc");
    FakeModel.instance().addFolder("cf").setParent("dc", Datacenter.class);
    FakeModel.instance().addResourcePool("rp").setParentFolder("cf");
    FakeModel.instance().addVM("image1").setParentFolder("cf");
    FakeModel.instance().addVM("image2").setParentFolder("cf");
    FakeModel.instance().addVM("image_template").setParentFolder("cf");
    FakeModel.instance().addVMSnapshot("image2", "snap");

    recreateClient();
    assertNull(myClient.getErrorInfo());
  }

  public void validate_objects_on_client_creation() throws MalformedURLException, RemoteException {
    throw new SkipException("TODO: Add validation");
/*    FakeModel.instance().removeFolder("cf");
    recreateClient();
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchFolder("cf").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addFolder("cf");

    FakeModel.instance().removeResourcePool("rp");
    recreateClient();
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchResourcePool("rp").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addResourcePool("rp");

    FakeModel.instance().removeVM("image1");
    recreateClient();
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchVM("image1").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addVM("image1");

    FakeModel.instance().removeVM("image2");
    recreateClient();
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchVM("image2").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addVM("image2");*/
  }

  public void check_start_type() {

    myFakeApi = new FakeApiConnector() {
      @Override
      public Map<String, VmwareInstance> getVirtualMachines(boolean filterClones) throws VmwareCheckedCloudException {
        final Map<String, VmwareInstance> instances = super.getVirtualMachines(filterClones);
        instances.put("image_template", new VmwareInstance(new FakeVirtualMachine("image_template", true, false)));
        instances.put("image1", new VmwareInstance(new FakeVirtualMachine("image1", false, false)));
        return instances;
      }
    };

  }

  public void check_on_demand_snapshot() {
    myClientParameters.setParameter("vmware_images_data", "image1;;cf;rp;ON_DEMAND_CLONE;3;X;:");
  }

  public void check_startup_parameters() throws CheckedCloudException {
    startNewInstanceAndWait("image1", Collections.singletonMap("customParam1", "customValue1"));
    final VmwareInstance vm = myFakeApi.getVirtualMachines(true).get("image1");

    final String userDataEncoded = vm.getProperty(VMWarePropertiesNames.USER_DATA);
    assertNotNull(userDataEncoded);
    final CloudInstanceUserData cloudInstanceUserData = CloudInstanceUserData.deserialize(userDataEncoded);
    assertEquals("customValue1", cloudInstanceUserData.getCustomAgentConfigurationParameters().get("customParam1"));
  }

  public void check_vm_clone() throws Exception {
    startAndCheckCloneDeletedAfterTermination("image1", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertEquals("image1", data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_NAME));
      }
    }, false);
    assertEquals(3, FakeModel.instance().getVms().size());
    startAndCheckCloneDeletedAfterTermination("image_template", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertTrue("image_template-1".equals(data.getInstanceId()));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image_template", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_NAME));
        assertEquals(VmwareConstants.CURRENT_STATE, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, true);
    assertEquals(3, FakeModel.instance().getVms().size());
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertTrue("image2-1".equals(data.getInstanceId()));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
  }

  public void on_demand_clone_should_use_existing_vm_when_one_exists() throws Exception {
    final AtomicReference<String> instanceId = new AtomicReference<String>();
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        instanceId.set(data.getInstanceId());
        assertTrue(data.getInstanceId().startsWith("image2"));
        assertTrue("image2-1".equals(data.getInstanceId()));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
    assertContains(FakeModel.instance().getVms().keySet(), instanceId.get());
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertEquals(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
  }

  public void on_demand_clone_should_create_more_when_not_enough() throws Exception {
    final AtomicReference<String> instanceId = new AtomicReference<String>();

    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) {
        instanceId.set(data.getInstanceId());
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
    final VmwareCloudInstance instance1 = startAndCheckInstance("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws RemoteException {
        assertEquals(instanceId.get(), data.getInstanceId());
      }
    });
    assertEquals(4, FakeModel.instance().getVms().size());
    final VmwareCloudInstance instance2 = startAndCheckInstance("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws RemoteException {
        assertNotSame(instanceId.get(), data.getInstanceId());
      }
    });
    assertEquals(5, FakeModel.instance().getVms().size());
    final Map<String, FakeVirtualMachine> vms = FakeModel.instance().getVms();
    assertEquals(VirtualMachinePowerState.poweredOn, vms.get(instance1.getName()).getRuntime().getPowerState());
    assertEquals(VirtualMachinePowerState.poweredOn, vms.get(instance2.getName()).getRuntime().getPowerState());
  }

  public void on_demand_clone_should_create_new_when_latest_snapshot_changes() throws Exception {

    final AtomicReference<String> instanceId = new AtomicReference<String>();

    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
        instanceId.set(data.getInstanceId());
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
    FakeModel.instance().addVMSnapshot("image2", "snap2");
    final VmwareCloudInstance instance1 = startAndCheckInstance("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertNotSame(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("snap2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    });
    assertEquals(4, FakeModel.instance().getVms().size());
    assertNotContains(FakeModel.instance().getVms().keySet(), instanceId.get());
    final VmwareCloudInstance instance2 = startAndCheckInstance("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertNotSame(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("snap2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    });
    assertEquals(5, FakeModel.instance().getVms().size());
    final Map<String, FakeVirtualMachine> vms = FakeModel.instance().getVms();
    assertEquals(VirtualMachinePowerState.poweredOn, vms.get(instance1.getName()).getRuntime().getPowerState());
    assertEquals(VirtualMachinePowerState.poweredOn, vms.get(instance2.getName()).getRuntime().getPowerState());
  }

  public void on_demand_clone_should_create_new_when_version_changes() throws Exception {
    myClientParameters.setParameter("vmware_images_data","[{sourceName:'image1', snapshot:'"+VmwareConstants.CURRENT_STATE +"',folder:'cf',pool:'rp',maxInstances:3, behaviour:'ON_DEMAND_CLONE'}," +
                                                         "{sourceName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                         "{sourceName:'image_template', snapshot:'"+VmwareConstants.CURRENT_STATE +"', folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]");
    recreateClient();


    final AtomicReference<String> instanceId = new AtomicReference<String>();
    final String originalChangeVersion = FakeModel.instance().getVirtualMachine("image1").getConfig().getChangeVersion();
    startAndCheckCloneDeletedAfterTermination("image1", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals(VmwareConstants.CURRENT_STATE, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
        assertEquals(originalChangeVersion, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION));
        instanceId.set(data.getInstanceId());
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());

    // start and stop Instance
    final Task powerOnTask = FakeModel.instance().getVirtualMachine("image1").powerOnVM_Task(null);
    assertEquals("success", powerOnTask.waitForTask());
    FakeModel.instance().getVirtualMachine("image1").shutdownGuest();
    final String updatedChangeVersion = FakeModel.instance().getVirtualMachine("image1").getConfig().getChangeVersion();
    assertNotSame(originalChangeVersion, updatedChangeVersion);

    final VmwareCloudInstance instance1 = startAndCheckInstance("image1", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertNotSame(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals(updatedChangeVersion, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION));
      }
    });
    assertEquals(4, FakeModel.instance().getVms().size());
    assertNotContains(FakeModel.instance().getVms().keySet(), instanceId.get());
    final VmwareCloudInstance instance2 = startAndCheckInstance("image1", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertNotSame(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals(updatedChangeVersion, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION));
      }
    });
    assertEquals(5, FakeModel.instance().getVms().size());
    final Map<String, FakeVirtualMachine> vms = FakeModel.instance().getVms();
    assertEquals(VirtualMachinePowerState.poweredOn, vms.get(instance1.getName()).getRuntime().getPowerState());
    assertEquals(VirtualMachinePowerState.poweredOn, vms.get(instance2.getName()).getRuntime().getPowerState());
  }

  public void catch_tc_started_instances_on_startup() throws MalformedURLException, RemoteException {
    startNewInstanceAndWait("image1");
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image_template");
    assertEquals(5, FakeModel.instance().getVms().size());

    recreateClient();
    assertNull(myClient.getErrorInfo());
    new WaitFor(10*1000){
      protected boolean condition() {
        int cnt = 0;
        for (VmwareCloudImage image : myClient.getImages()) {
          final Collection<VmwareCloudInstance> instances = image.getInstances();
          cnt += instances.size();
          for (VmwareCloudInstance instance : instances) {
            assertEquals(InstanceStatus.RUNNING, instance.getStatus());
          }
        }
        return cnt == 3;
      }
    };
    for (VmwareCloudImage image : myClient.getImages()) {
      final Collection<VmwareCloudInstance> instances = image.getInstances();
      assertEquals(1, instances.size());
      final VmwareCloudInstance instance = instances.iterator().next();
      if ("image1".equals(image.getName())){
        assertEquals("image1", instance.getName());
      } else if ("image2".equals(image.getName())) {
        assertTrue(instance.getName().startsWith(image.getName()));
        assertEquals("snap", instance.getSnapshotName());
      } else if ("image_template".equals(image.getName())) {
        assertTrue(instance.getName().startsWith(image.getName()));
      }
    }

  }

  public void sync_start_stop_instance_status() throws RemoteException {
    final VmwareCloudImage img1 = getImageByName("image1");
    assertEquals(1, img1.getInstances().size());
    final VmwareCloudInstance inst1 = img1.getInstances().iterator().next();
    assertEquals(InstanceStatus.STOPPED, inst1.getStatus());
    startNewInstanceAndWait("image1");
    assertEquals(InstanceStatus.RUNNING, inst1.getStatus());
    FakeModel.instance().getVirtualMachine("image1").shutdownGuest();
    new WaitFor(3000){
      protected boolean condition() {
        return img1.getInstances().iterator().next().getStatus() == InstanceStatus.STOPPED;
      }
    }.assertCompleted("Should have caught the stopped status");

    FakeModel.instance().getVirtualMachine("image1").powerOnVM_Task(null);

    new WaitFor(3000){
      protected boolean condition() {
        return img1.getInstances().iterator().next().getStatus() == InstanceStatus.RUNNING;
      }
    }.assertCompleted("Should have caught the running status");
  }

  public void sync_clone_status() throws RemoteException {
    final VmwareCloudImage img1 = getImageByName("image_template");
    assertEquals(0, img1.getInstances().size());
    final VmwareCloudInstance inst = startNewInstanceAndWait("image_template");
    assertEquals(InstanceStatus.RUNNING, inst.getStatus());
    FakeModel.instance().getVirtualMachine(inst.getName()).shutdownGuest();
    new WaitFor(3000){
      protected boolean condition() {
        return img1.getInstances().iterator().next().getStatus() == InstanceStatus.STOPPED;
      }
    }.assertCompleted("Should have caught the stopped status");

    FakeModel.instance().getVirtualMachine(inst.getName()).powerOnVM_Task(null);

    new WaitFor(3000){
      protected boolean condition() {
        return img1.getInstances().iterator().next().getStatus() == InstanceStatus.RUNNING;
      }
    }.assertCompleted("Should have caught the running status");
  }

  public void should_limit_new_instances_count(){
    int countStarted = 0;
    final VmwareCloudImage image_template = getImageByName("image_template");
    while (myClient.canStartNewInstance(image_template)){
      final CloudInstanceUserData userData = createUserData(image_template + "_agent");
      myClient.startNewInstance(image_template, userData);
      countStarted++;
      assertTrue(countStarted <= 3);
    }
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = ".*Unable to find snapshot.*")
  public void shouldnt_start_when_snapshot_is_missing(){
    final VmwareCloudImage image2 = getImageByName("image2");
    FakeModel.instance().removeVmSnaphot(image2.getName(), "snap");
    startNewInstanceAndWait("image2");
  }

  public void should_power_off_if_no_guest_tools_avail() throws InterruptedException {
    final VmwareCloudImage image_template = getImageByName("image_template");
    final VmwareCloudInstance instance = startNewInstanceAndWait("image_template");
    assertContains(image_template.getInstances(), instance);
    FakeModel.instance().getVirtualMachine(instance.getName()).disableGuestTools();
    myClient.terminateInstance(instance);
    new WaitFor(2000) {
      @Override
      protected boolean condition() {
        return instance.getStatus() == InstanceStatus.STOPPED && !image_template.getInstances().contains(instance);
      }
    };
    assertNull(FakeModel.instance().getVirtualMachine(instance.getName()));
    assertNotContains(image_template.getInstances(), instance);
  }

  public void existing_clones_with_start_stop() {
    final VmwareCloudInstance cloneInstance = startNewInstanceAndWait("image2");
    myClientParameters.setParameter("vmware_images_data", "[{sourceName:'image1', behaviour:'START_STOP'}," +
                                                          "{sourceName:'image2', behaviour:'START_STOP'}," +
                                                          "{sourceName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                          "', folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]");

    recreateClient();
    boolean checked = false;
    for (VmwareCloudImage image : myClient.getImages()) {
      if (!"image2".equals(image.getName()))
        continue;

      final Collection<VmwareCloudInstance> instances = image.getInstances();
      final VmwareCloudInstance singleInstance = instances.iterator().next();
      assertEquals(1, instances.size());
      assertEquals("image2", singleInstance.getName());
      checked = true;
    }
    assertTrue(checked);
  }

  public void dont_exceed_max_instances_limit_fresh_clones() throws RemoteException {
    final VmwareCloudInstance[] instances = new VmwareCloudInstance[]{startNewInstanceAndWait("image_template"),
      startNewInstanceAndWait("image_template")};
    // shutdown all instances
    for (VmwareCloudInstance instance : instances) {
      FakeModel.instance().getVirtualMachine(instance.getName()).powerOffVM_Task();
    }
    recreateClient();
    startNewInstanceAndWait("image_template");
    try {
      startNewInstanceAndWait("image_template");
      fail("Should not clone, because limit exceeded");
    } catch (QuotaException qex){
      assertTrue(qex.getMessage().contains("image_template"));
    }
  }

  @Test(expectedExceptions = QuotaException.class, expectedExceptionsMessageRegExp = "Unable to start more instances of image image2")
  public void check_max_instances_count_on_profile_start() {
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image2");
    System.setProperty("teamcity.vsphere.instance.status.update.delay.ms", "25000");
    recreateClient();
    startNewInstanceAndWait("image2");
  }

  public void do_not_clear_image_instances_list_on_error() throws ExecutionException, InterruptedException {
    final AtomicBoolean failure = new AtomicBoolean(false);
    final AtomicLong lastApiCallTime = new AtomicLong(0);
    final AtomicLong lastUpdateTime = new AtomicLong(0);
    myFakeApi = new FakeApiConnector(){
      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntities(final Class<T> instanceType) throws VmwareCheckedCloudException {
        lastApiCallTime.set(System.currentTimeMillis());
        if (failure.get()){
          throw new VmwareCheckedCloudException("Cannot connect");
        }
        return super.findAllEntities(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMap(final Class<T> instanceType) throws VmwareCheckedCloudException {
        lastApiCallTime.set(System.currentTimeMillis());
        if (failure.get()){
          throw new VmwareCheckedCloudException("Cannot connect");
        }
        return super.findAllEntitiesAsMap(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> T findEntityByIdName(final String name, final Class<T> instanceType) throws VmwareCheckedCloudException {
        lastApiCallTime.set(System.currentTimeMillis());
        if (failure.get()){
          throw new VmwareCheckedCloudException("Cannot connect");
        }
        return super.findEntityByIdName(name, instanceType);
      }
    };
    recreateClient(2, lastUpdateTime, 2*60*1000);
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image2");
    failure.set(true);
    final long problemStart = System.currentTimeMillis();
    new WaitFor(5*1000){

      @Override
      protected boolean condition() {
        return lastUpdateTime.get() > problemStart;
      }
    }.assertCompleted("Should have been checked at least once - delay set to 2 sec");

    assertEquals(3, getImageByName("image2").getInstances().size());
  }

  public void canstart_check_shouldnt_block_thread() throws InterruptedException {
    final Lock lock = new ReentrantLock();
    final AtomicBoolean shouldLock = new AtomicBoolean(false);
    myFakeApi = new FakeApiConnector(){
      @Override
      public void test() throws VmwareCheckedCloudException {
        if (shouldLock.get()){
          lock.lock(); // will stuck here
        }
        super.test();
      }
    };

    recreateClient();
    shouldLock.set(true);
    lock.lock();
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.execute(new Runnable() {
      public void run() {
        getImageByName("image1").canStartNewInstance();
        getImageByName("image2").canStartNewInstance();
        getImageByName("image_template").canStartNewInstance();
      }
    });
    executor.shutdown();
    assertTrue("canStart method blocks the thread!", executor.awaitTermination(100, TimeUnit.MILLISECONDS));
  }

  public void create_within_the_same_datacenter() throws InterruptedException {
    FakeModel.instance().addDatacenter("dc2");
    FakeModel.instance().addFolder("cf2").setParent("dc2", Datacenter.class);
    FakeModel.instance().addResourcePool("rp2").setParentFolder("cf2");
    FakeModel.instance().addVM("image3").setParentFolder("cf");
    myClientParameters.setParameter("vmware_images_data"
      , "[{sourceName:'image1', behaviour:'START_STOP'}," +
        "{sourceName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
        "{sourceName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE + "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}, " +
        "{sourceName:'image3',snapshot:'" + VmwareConstants.CURRENT_STATE + "'," + "folder:'cf2',pool:'rp2',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}]");
    recreateClient();

    final CloudInstanceUserData userData = createUserData("image3_agent");
    final VmwareCloudInstance vmwareCloudInstance = myClient.startNewInstance(getImageByName("image3"), userData);
    new WaitFor(10 * 1000) {
      @Override
      protected boolean condition() {
        return vmwareCloudInstance.getStatus() == InstanceStatus.ERROR && vmwareCloudInstance.getErrorInfo() != null;
      }
    }.assertCompleted();

    final String msg = vmwareCloudInstance.getErrorInfo().getMessage();
    assertContains(msg, "Unable to find folder cf2 in datacenter dc");
  }

  public void check_nickname(){
    myClientParameters.setParameter("vmware_images_data", "[{sourceName:'image1', behaviour:'START_STOP'}," +
                                                          "{nickname:'image2Nick1', sourceName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                          "{nickname:'image2Nick2', sourceName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                          "{sourceName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                          "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]");
    recreateClient();
    startNewInstanceAndWait("image2Nick1");
    boolean checked1 = false;
    boolean checked2 = false;
    String startedInstanceName = null;
    for (VmwareCloudImage image : myClient.getImages()) {
      if ("image2Nick1".equals(image.getName())){
        final Collection<VmwareCloudInstance> instances = image.getInstances();
        final VmwareCloudInstance singleInstance = instances.iterator().next();
        startedInstanceName = singleInstance.getName();
        assertEquals(1, instances.size());
        assertTrue(singleInstance.getName().startsWith("image2Nick1"));
        checked1 = true;
      } else if ("image2Nick2".equals(image.getName())){
        assertEquals(0, image.getInstances().size());
        checked2 = true;
      }
    }
    startNewInstanceAndWait("image2Nick2");
    startNewInstanceAndWait("image2Nick2");
    for (VmwareCloudImage image : myClient.getImages()) {
      if ("image2Nick1".equals(image.getName())){
        final Collection<VmwareCloudInstance> instances = image.getInstances();
        final VmwareCloudInstance singleInstance = instances.iterator().next();
        assertEquals(startedInstanceName, singleInstance.getName());
        assertTrue(singleInstance.getName().startsWith("image2Nick1"));
        assertEquals(1, instances.size());
        checked1 = true;
      } else if ("image2Nick2".equals(image.getName())){
        assertEquals(2, image.getInstances().size());
        for (VmwareCloudInstance instance : image.getInstances()) {
          assertTrue(instance.getName().startsWith("image2Nick2"));
        }
        checked2 = true;
      }
    }

    assertTrue(checked1 && checked2);

  }

  public void profile_creation_should_not_block_ui() throws ExecutionException, InterruptedException {
    final int extraProfileCount = 5;
    final List<CloudClientParameters> profileParams = new ArrayList<CloudClientParameters>();
    for (int i=0; i< extraProfileCount; i++){
      final CloudClientParameters parameters = new CloudClientParameters();
      profileParams.add(parameters);
      parameters.setParameter("vmware_images_data", "[{sourceName:'image_new"+i+"', behaviour:'START_STOP'}]");
      parameters.setParameter(VMWareWebConstants.SERVER_URL, "http://localhost:8080");
      parameters.setParameter(VMWareWebConstants.USERNAME, "un");
      parameters.setParameter(VMWareWebConstants.PASSWORD, "pw");
    }


    myFakeApi = new FakeApiConnector(){

      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntities(final Class<T> instanceType) throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        return super.findAllEntities(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMap(final Class<T> instanceType) throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        return super.findAllEntitiesAsMap(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> T findEntityByIdNameNullable(final String name, final Class<T> instanceType, final Datacenter dc) throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        return super.findEntityByIdNameNullable(name, instanceType, dc);
      }

      @Override
      public void test() throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        super.test();
      }
    };

    final CloudEventDispatcher dispatcher = new CloudEventDispatcher();
    final CloudRegistryImpl cloudRegistrar = new CloudRegistryImpl(dispatcher);
    final Mockery m = new Mockery();
    final PluginDescriptor pd = m.mock(PluginDescriptor.class);
    m.checking(new Expectations(){{
      allowing(pd).getPluginResourcesPath("vmware-settings.html"); will(returnValue("aaa.html"));
    }});
    final CloudState state = m.mock(CloudState.class);
    final VMWareCloudClientFactory factory = new VMWareCloudClientFactory(cloudRegistrar, pd, new ServerPaths(myIdxStorage.getAbsolutePath())){

      @NotNull
      @Override
      protected VMWareApiConnector createConnectorFromParams(final CloudClientParameters params) {
        return myFakeApi;
      }

    };

    Runnable r = new Runnable() {
      public void run() {
        for (CloudClientParameters param : profileParams) {
          factory.createNewClient(state, param);
        }
      }
    };

    final Future<?> future = Executors.newSingleThreadExecutor().submit(r);
    future.get();
    new WaitFor(500){
      @Override
      protected boolean condition() {
        return future.isDone();
      }
    }.assertCompleted("Recreation of cloud profiles takes too long");

  }

  public void enforce_change_of_stuck_instance_status() throws RemoteException, ExecutionException, InterruptedException {
    recreateClient(2, new AtomicLong(0), 3*1000);
    final VmwareCloudInstance instance = startNewInstanceAndWait("image1");
    FakeModel.instance().getVms().get(instance.getName()).shutdownGuest();
    instance.setStatus(InstanceStatus.STOPPING);
    new WaitFor(6*1000){
      @Override
      protected boolean condition() {
        return instance.getStatus() == InstanceStatus.STOPPED;
      }
    }.assertCompleted("should have changed the status");

  }

  public void shouldDeleteOldInstancesIfLimitReached() throws VmwareCheckedCloudException, RemoteException, InterruptedException {
    final List<VmwareCloudInstance> instances2stop = new ArrayList<VmwareCloudInstance>();
    for (int i=0; i<3; i++){
      final VmwareCloudInstance instance = startNewInstanceAndWait("image_template");
      if (i%2 == 0)
        instances2stop.add(instance);
    }
    new WaitFor(3*1000){
      @Override
      protected boolean condition() {
        return getImageByName("image_template").getInstances().size() == 3;
      }
    }.assertCompleted("should have started and catched");

    for (VmwareCloudInstance inst : instances2stop) {
      FakeModel.instance().getVirtualMachine(inst.getName()).shutdownGuest();
    }

    new WaitFor(10*1000){
      @Override
      protected boolean condition() {
        boolean stoppedAll = true;
        for (VmwareCloudInstance inst : instances2stop) {
          stoppedAll = stoppedAll && FakeModel.instance().getVirtualMachine(inst.getName()).getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff;
        }
        if (stoppedAll){
          final VmwareCloudImage img = getImageByName("image_template");

          if (System.currentTimeMillis() % 20 == 0){
            System.out.printf("%s%n", img.getInstances());
          }
          return img.canStartNewInstance();
        }
        return false;
      }
    }.assertCompleted("Should have stopped and state updated");

    new WaitFor(10*1000){
      @Override
      protected boolean condition() {
        final VmwareCloudImage img = getImageByName("image_template");
        int stoppedCount = 0;
        for (VmwareCloudInstance instance : img.getInstances()) {
          if (instance.getStatus() ==InstanceStatus.STOPPED)
            stoppedCount++;
        }
        return stoppedCount ==2;
      }
    };
    // requires time for orphaned timeout
    Thread.sleep(500);

    System.setProperty("teamcity.vmware.stopped.orphaned.timeout", "200");
    try {
      startNewInstanceAndWait("image_template");
      fail("Should have failed to start");
    } catch(Exception ex){}


    new WaitFor(5000){
      @Override
      protected boolean condition() {
        boolean instancesDeleted = true;
        for (VmwareCloudInstance inst : instances2stop) {
          instancesDeleted = instancesDeleted && (FakeModel.instance().getVirtualMachine(inst.getName()) == null);
        }
        return instancesDeleted;
      }
    }.assertCompleted("Should have deleted");

    new WaitFor(10*1000){
      @Override
      protected boolean condition() {
        final VmwareCloudImage img = getImageByName("image_template");
        return img.getInstances().size() == 1;
      }
    };


    for (int i=0; i<2; i++){
      final VmwareCloudInstance instance = startNewInstanceAndWait("image_template");
      assertEquals("image_template-" + (i+5), instance.getName());
    }
  }

  /*
  *
  *
  * Helper methods
  *
  *
  * */

  private static CloudInstanceUserData createUserData(String agentName){
    return createUserData(agentName, Collections.<String, String>emptyMap());
  }

  private static CloudInstanceUserData createUserData(String agentName, Map<String, String> parameters){
    Map<String, String> map = new HashMap<String, String>(parameters);
    map.put(CloudContants.PROFILE_ID, PROFILE_ID);
    CloudInstanceUserData userData = new CloudInstanceUserData(agentName,
                                                               "authToken", "http://localhost:8080", 3 * 60 * 1000l, "My profile", map);
    return userData;
  }

  private static String wrapWithArraySymbols(String str) {
    return String.format("[%s]", str);
  }

  private VmwareCloudInstance startAndCheckCloneDeletedAfterTermination(String imageName,
                                                         Checker<VmwareCloudInstance> instanceChecker,
                                                         boolean shouldBeDeleted) throws Exception {
    final VmwareCloudInstance instance = startAndCheckInstance(imageName, instanceChecker);
    terminateAndDeleteIfNecessary(shouldBeDeleted, instance);
    return instance;
  }

  private void terminateAndDeleteIfNecessary(final boolean shouldBeDeleted, final VmwareCloudInstance instance) throws CheckedCloudException {
    myClient.terminateInstance(instance);
    new WaitFor(5*1000){
      protected boolean condition() {
        return instance.getStatus()==InstanceStatus.STOPPED;
      }
    }.assertCompleted();
    final String name = instance.getName();
    final WaitFor waitFor = new WaitFor(10 * 1000) {
      @Override
      protected boolean condition() {
        try {
          if (shouldBeDeleted) {
            return (myFakeApi.getVirtualMachines(false).get(name) == null);
          } else {
            return myFakeApi.getInstanceDetails(name).getInstanceStatus() == InstanceStatus.STOPPED;
          }
        } catch (CheckedCloudException e) {
          return false;
        }
      }
    };
    waitFor.assertCompleted("template clone should be deleted after execution");
  }

  private VmwareCloudInstance startAndCheckInstance(final String imageName, final Checker<VmwareCloudInstance> instanceChecker) throws Exception {
    final VmwareCloudInstance instance = startNewInstanceAndWait(imageName);
    new WaitFor(10 * 1000) {

      @Override
      protected boolean condition() {
        final VmwareInstance vm;
        try {
          vm = myFakeApi.getVirtualMachines(false).get(instance.getName());
          return vm != null && vm.getInstanceStatus() == InstanceStatus.RUNNING;
        } catch (CheckedCloudException e) {
          return false;
        }
      }
    };
    final VmwareInstance vm = myFakeApi.getVirtualMachines(false).get(instance.getName());
    assertNotNull("instance " + instance.getName() + " must exists", vm);
    assertEquals("Must be running", InstanceStatus.RUNNING, vm.getInstanceStatus());
    if (instanceChecker != null) {
      instanceChecker.check(instance);
    }
    return instance;
  }

  private VmwareCloudInstance startNewInstanceAndWait(String imageName) {
    return startNewInstanceAndWait(imageName, new HashMap<String, String>());
  }

  private VmwareCloudInstance startNewInstanceAndWait(String imageName, Map<String, String> parameters) {
    final CloudInstanceUserData userData = createUserData(imageName + "_agent", parameters);
    final VmwareCloudInstance vmwareCloudInstance = myClient.startNewInstance(getImageByName(imageName), userData);
    final WaitFor waitFor = new WaitFor(10 * 1000) {
      @Override
      protected boolean condition() {
        return vmwareCloudInstance.getStatus() == InstanceStatus.RUNNING;
      }
    };
    waitFor.assertCompleted();
    return vmwareCloudInstance;
  }

  private static String getExtraConfigValue(final OptionValue[] extraConfig, final String key) {
    for (OptionValue param : extraConfig) {
      if (param.getKey().equals(key)) {
        return String.valueOf(param.getValue());
      }
    }
    return null;
  }

  private VmwareCloudImage getImageByName(final String name) {
    for (CloudImage image : myClient.getImages()) {
      if (image.getName().equals(name)) {
        return (VmwareCloudImage)image;
      }
    }
    throw new RuntimeException("unable to find image by name: " + name);
  }

  private void recreateClient()  {
    if (myClient != null) {
      myClient.dispose();
    }
    final Collection<VmwareCloudImageDetails> images = VMWareCloudClientFactory.parseImageDataInternal(myClientParameters);
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi, myIdxStorage){
      @Override
      public Future<?> populateImagesDataAsync(@NotNull final Collection<VmwareCloudImageDetails> imageDetails) {
        return super.populateImagesDataAsync(imageDetails, 2);
      }
    };
    final Future<?> future = myClient.populateImagesDataAsync(images);
    new WaitFor(1000){
      @Override
      protected boolean condition() {
        return future.isDone();
      }
    };
    assertNull(myClient.getErrorInfo());
  }

  private void recreateClient(final long updateDelay, final AtomicLong lastUpdateTime, final long stuckTime) throws ExecutionException, InterruptedException {
    if (myClient != null) {
      myClient.dispose();
    }
    final Collection<VmwareCloudImageDetails> images = VMWareCloudClientFactory.parseImageDataInternal(myClientParameters);
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi, myIdxStorage){
      @Override
      public Future<?> populateImagesDataAsync(@NotNull final Collection<VmwareCloudImageDetails> imageDetails) {
        return myAsyncTaskExecutor.submit("Populate", new Runnable() {
          public void run() {
            populateImagesData(imageDetails, updateDelay, updateDelay);
          }
        });
      }

      @Override
      protected UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> createUpdateInstancesTask() {
        return new UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient>(myFakeApi, this, stuckTime){
          @Override
          public void run() {
            super.run();
            lastUpdateTime.set(System.currentTimeMillis());
          }
        };
      }
    };
    myClient.populateImagesDataAsync(images).get();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    System.getProperties().remove("teamcity.vsphere.instance.status.update.delay.ms");
    if (myClient != null) {
      myClient.dispose();
      myClient = null;
    }
    FakeModel.instance().clear();
    super.tearDown();
  }

  private interface Checker<T> {
    void check(T data) throws Exception;
  }

}
