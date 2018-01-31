package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.util.Pair;
import com.intellij.util.WaitFor;
import com.vmware.vim25.*;
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
import java.util.stream.Collectors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.server.*;
import jetbrains.buildServer.clouds.server.impl.CloudRegistryImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudClientParametersImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileUtil;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import jetbrains.buildServer.clouds.vmware.tasks.VmwarePooledUpdateInstanceTask;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.impl.ServerSettingsImpl;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_ID;


/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:04 PM
 */
@Test
public class VmwareCloudIntegrationTest extends BaseTestCase {

  protected static final String PROFILE_ID = "cp1";
  protected static final String PROJECT_ID = "project123";
  protected static final String TEST_SERVER_UUID = "1234-5678-9012";

  private VMWareCloudClient myClient;
  private FakeApiConnector myFakeApi;
  private CloudClientParameters myClientParameters;
  private CloudProfile myProfile;
  private VmwareUpdateTaskManager myTaskManager;
  private File myIdxStorage;
  private AtomicLong myStuckTime;
  private AtomicLong myLastRunTime;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    //org.apache.log4j.Logger.getLogger("jetbrains.buildServer").setLevel(Level.DEBUG);
    //org.apache.log4j.Logger.getRootLogger().addAppender(new ConsoleAppender());

    myIdxStorage = createTempDir();

    setInternalProperty("teamcity.vsphere.instance.status.update.delay.ms", "50");
    myClientParameters = new CloudClientParametersImpl(
      Collections.emptyMap(),
                                                       CloudProfileUtil.collectionFromJson("[{sourceVmName:'image1', behaviour:'START_STOP'}," +
                                                                                           "{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                                                                                           "customizationSpec:'someCustomization'}," +
                                                                                           "{sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                                                           "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]",
                                                                                           PROJECT_ID));

    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID, null);
    FakeModel.instance().addDatacenter("dc");
    FakeModel.instance().addFolder("cf").setParent("dc", Datacenter.class);
    FakeModel.instance().addResourcePool("rp").setParentFolder("cf");
    FakeModel.instance().addVM("image1").setParentFolder("cf");
    FakeModel.instance().addVM("image2").setParentFolder("cf");
    FakeModel.instance().addVM("image_template").setParentFolder("cf");
    FakeModel.instance().addVMSnapshot("image2", "snap");
    FakeModel.instance().getCustomizationSpecs().put("someCustomization", new CustomizationSpec());
    FakeModel.instance().getCustomizationSpecs().put("linux", new CustomizationSpec());
    myLastRunTime = new AtomicLong(0);
    myStuckTime = new AtomicLong(2*60*1000);

    myTaskManager = new VmwareUpdateTaskManager(){
      @Override
      protected VmwarePooledUpdateInstanceTask createNewPooledTask(@NotNull final VMWareApiConnector connector, @NotNull final VMWareCloudClient client) {
        return new VmwarePooledUpdateInstanceTask(connector, client, myStuckTime.get(), false){
          @Override
          public void run() {
            super.run();
            myLastRunTime.set(System.currentTimeMillis());
          }
        };
      }
    };

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

  public void check_start_type() throws MalformedURLException {

    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID) {
      @Override
      public List<VmwareInstance> getVirtualMachines(boolean filterClones) throws VmwareCheckedCloudException {
        final List<VmwareInstance> instances = super.getVirtualMachines(filterClones);
        instances.add(new VmwareInstance(new FakeVirtualMachine("image_template", true, false), "datacenter-10"));
        instances.add(new VmwareInstance(new FakeVirtualMachine("image1", false, false), "datacenter-10"));
        return instances;
      }
    };

  }

  public void check_startup_parameters() throws CheckedCloudException {
    startNewInstanceAndWait("image1", Collections.singletonMap("customParam1", "customValue1"));
    final VmwareInstance vm = myFakeApi.getAllVMsMap(true).get("image1");

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
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME));
      }
    }, false);
    assertEquals(3, FakeModel.instance().getVms().size());
    startAndCheckCloneDeletedAfterTermination("image_template", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertTrue("image_template-1".equals(data.getInstanceId()));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image_template", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME));
        assertEquals(VmwareConstants.CURRENT_STATE, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, true);
    assertEquals(3, FakeModel.instance().getVms().size());
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertTrue("image2-1".equals(data.getInstanceId()));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME));
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
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME));
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
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME));
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
    updateClientParameters(CloudProfileUtil.collectionFromJson("[{sourceVmName:'image1', snapshot:'" + VmwareConstants.CURRENT_STATE + "',folder:'cf',pool:'rp',maxInstances:3, behaviour:'ON_DEMAND_CLONE'}," +
                                                               "{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                               "{sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE + "', folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]",
                                                               PROJECT_ID));
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
    Thread.sleep(2000); // to ensure that version will change
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

  private void updateClientParameters(final Collection<CloudImageParameters> cloudImageParameters) {
    myClientParameters = updateClientParameters(myClientParameters, cloudImageParameters);
  }

  private CloudClientParameters updateClientParameters(CloudClientParameters clientParameters,  final Collection<CloudImageParameters> cloudImageParameters) {
    return new CloudClientParametersImpl(
      clientParameters.getParameters(),
                                                       cloudImageParameters);
  }
  private void updateClientParameters(Map<String, String> params) {
    final Map<String, String> parameters = new HashMap<>( myClientParameters.getParameters() );
    parameters.putAll(params);
    myClientParameters = new CloudClientParametersImpl(
      parameters,
                                                       myClientParameters.getCloudImages());
  }

  public void catch_tc_started_instances_on_startup() throws MalformedURLException, RemoteException {
    final FakeVirtualMachine image2 = FakeModel.instance().getVirtualMachine("image2");
    final FakeVirtualMachine image_template = FakeModel.instance().getVirtualMachine("image_template");

    startNewInstanceAndWait("image1");
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image_template");
    assertEquals(5, FakeModel.instance().getVms().size());

    recreateClient();
    assertNull(myClient.getErrorInfo());
    new WaitFor(5*1000){
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
        assertEquals("snap", instance.getSourceState().getSnapshotName());
        assertEquals(image2.getMOR().getVal(), instance.getSourceState().getSourceVmId());
      } else if ("image_template".equals(image.getName())) {
        assertTrue(instance.getName().startsWith(image.getName()));
        assertEquals(image_template.getMOR().getVal(), instance.getSourceState().getSourceVmId());
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

  public void shouldnt_start_when_snapshot_is_missing(){
    final VmwareCloudImage image2 = getImageByName("image2");
    FakeModel.instance().removeVmSnaphot(image2.getName(), "snap");
    startNewInstanceAndCheck("image2", new HashMap<>(), false);
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
    updateClientParameters(CloudProfileUtil.collectionFromJson("[{sourceVmName:'image1', behaviour:'START_STOP'}," +
                                                               "{sourceVmName:'image2', behaviour:'START_STOP'}," +
                                                               "{sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                               "', folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]", PROJECT_ID));

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

    // instance should not start
    startNewInstanceAndCheck("image_template", new HashMap<>(), false);
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

  public void do_not_clear_image_instances_list_on_error() throws ExecutionException, InterruptedException, MalformedURLException {
    final AtomicBoolean failure = new AtomicBoolean(false);
    final AtomicLong lastApiCallTime = new AtomicLong(0);
    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID){
      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        lastApiCallTime.set(System.currentTimeMillis());
        if (failure.get()){
          throw new VmwareCheckedCloudException("Cannot connect");
        }
        return super.findAllEntitiesOld(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        lastApiCallTime.set(System.currentTimeMillis());
        if (failure.get()){
          throw new VmwareCheckedCloudException("Cannot connect");
        }
        return super.findAllEntitiesAsMapOld(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> Pair<T,Datacenter> findEntityByIdNameOld(final String name, final Class<T> instanceType) throws VmwareCheckedCloudException {
        lastApiCallTime.set(System.currentTimeMillis());
        if (failure.get()){
          throw new VmwareCheckedCloudException("Cannot connect");
        }
        return super.findEntityByIdNameOld(name, instanceType);
      }
    };
    recreateClient(250);
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image2");
    startNewInstanceAndWait("image2");
    Thread.sleep(5*1000);
    failure.set(true);
    final long problemStart = System.currentTimeMillis();
    new WaitFor(5*1000){

      @Override
      protected boolean condition() {
        return myLastRunTime.get() > problemStart;
      }
    }.assertCompleted("Should have been checked at least once - delay set to 2 sec");

    assertEquals(3, getImageByName("image2").getInstances().size());
  }

  public void canstart_check_shouldnt_block_thread() throws InterruptedException, MalformedURLException {
    final Lock lock = new ReentrantLock();
    final AtomicBoolean shouldLock = new AtomicBoolean(false);
    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID){
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

  public void check_same_datacenter() throws InterruptedException {
    FakeModel.instance().addDatacenter("dc2");
    FakeModel.instance().addFolder("cf2").setParent("dc2", Datacenter.class);
    FakeModel.instance().addResourcePool("rp2").setParentFolder("cf2");
    FakeModel.instance().addVM("image3").setParentFolder("cf");
    updateClientParameters(CloudProfileUtil.collectionFromJson(
      "[{sourceVmName:'image1', behaviour:'START_STOP'}," +
        "{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
        "{sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE + "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}, " +
        "{sourceVmName:'image3',snapshot:'" + VmwareConstants.CURRENT_STATE + "'," + "folder:'cf2',pool:'rp2',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}]", PROJECT_ID));
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
    updateClientParameters(CloudProfileUtil.collectionFromJson(
      "[{sourceVmName:'image1', behaviour:'START_STOP'}," +
                                                          "{nickname:'image2Nick1', sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                          "{nickname:'image2Nick2', sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE'}," +
                                                          "{sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                          "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE'}]", PROJECT_ID));
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

  public void profile_creation_should_not_block_ui() throws ExecutionException, InterruptedException, MalformedURLException {
    final int extraProfileCount = 5;
    final List<CloudClientParameters> profileParams = new ArrayList<CloudClientParameters>();
    for (int i=0; i< extraProfileCount; i++){
      profileParams.add(new CloudClientParametersImpl(
        createMap(VMWareWebConstants.SERVER_URL, "http://localhost:8080",
                          VMWareWebConstants.USERNAME, "un",
                          VMWareWebConstants.PASSWORD, "pw"),
        CloudProfileUtil.collectionFromJson("[{sourceVmName:'image_new" + i + "', behaviour:'START_STOP'}]", PROJECT_ID)
      ));
    }


    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID){

      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        return super.findAllEntitiesOld(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        return super.findAllEntitiesAsMapOld(instanceType);
      }

      @Override
      protected <T extends ManagedEntity> T findEntityByIdNameNullableOld(final String name, final Class<T> instanceType, final Datacenter dc) throws VmwareCheckedCloudException {
        try {Thread.sleep(1000);} catch (InterruptedException e) {}
        return super.findEntityByIdNameNullableOld(name, instanceType, dc);
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
    final CloudManagerBase cloudManagerBase = m.mock(CloudManagerBase.class);

    final PluginDescriptor pd = m.mock(PluginDescriptor.class);
    final CloudState state = m.mock(CloudState.class);
    m.checking(new Expectations(){{
      allowing(pd).getPluginResourcesPath("vmware-settings.html"); will(returnValue("aaa.html"));
      allowing(state).getProfileId(); will(returnValue(PROFILE_ID));
      allowing(state).getProjectId(); will(returnValue(PROJECT_ID));
      allowing(cloudManagerBase).findProfileById(PROJECT_ID, PROFILE_ID); will(returnValue(myProfile));
    }});

    final VMWareCloudClientFactory factory = new VMWareCloudClientFactory(cloudRegistrar, pd, new ServerPaths(myIdxStorage.getAbsolutePath()),
                                                                          new CloudInstancesProvider() {
                                                                            public void iterateInstances(@NotNull final CloudInstancesProviderCallback callback) {}
                                                                            public void iterateInstances(@NotNull final CloudInstancesProviderExtendedCallback callback) {}
                                                                            public void iterateProfileInstances(@NotNull final CloudProfile profile,
                                                                                                                @NotNull final CloudInstancesProviderCallback callback) {}
                                                                            public void markInstanceExpired(@NotNull final CloudProfile profile,
                                                                                                            @NotNull final CloudInstance instance) {}
                                                                            public boolean isInstanceExpired(@NotNull final CloudProfile profile,
                                                                                                             @NotNull final CloudInstance instance) {return false;}
                                                                          },
                                                                          cloudManagerBase, new ServerSettingsImpl(),
                                                                          myTaskManager){

      @NotNull
      @Override
      protected VMWareApiConnector createConnectorFromParams(@NotNull final CloudState state, final CloudClientParameters params) {
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
    myStuckTime.set(3*1000);
    recreateClient(250);
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

    new WaitFor(3*1000){
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
    }.assertCompleted("Should have stopped");
    // requires time for orphaned timeout
    Thread.sleep(500);

    System.setProperty("teamcity.vmware.stopped.orphaned.timeout", "200");

    startNewInstanceAndCheck("image_template", new HashMap<>(), false);

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
    }.assertCompleted("should have only 1 instance");


    for (int i=0; i<2; i++){
      final VmwareCloudInstance instance = startNewInstanceAndWait("image_template");
      assertEquals("image_template-" + (i+5), instance.getName());
    }
  }

  public void markInstanceExpiredWhenSnapshotNameChanges() throws MalformedURLException {
    final Map<String, CloudInstance> instancesMarkedExpired = new HashMap<String, CloudInstance>();
    final CloudInstancesProvider instancesProviderStub = new CloudInstancesProvider() {
      public void iterateInstances(@NotNull final CloudInstancesProviderCallback callback) {
        throw new UnsupportedOperationException(".iterateInstances");

        //
      }

      public void iterateInstances(@NotNull final CloudInstancesProviderExtendedCallback callback) {
        throw new UnsupportedOperationException(".iterateInstances");

        //
      }

      public void iterateProfileInstances(@NotNull final CloudProfile profile, @NotNull final CloudInstancesProviderCallback callback) {
        throw new UnsupportedOperationException(".iterateProfileInstances");

        //
      }

      public void markInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        instancesMarkedExpired.put(instance.getInstanceId(),instance );
      }

      public boolean isInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        return instancesMarkedExpired.containsKey(instance.getInstanceId());
      }
    };
    myFakeApi = new FakeApiConnector(null, null, instancesProviderStub);
    recreateClient();
    final VmwareCloudInstance in = startNewInstanceAndWait("image2");
    assertEquals(VirtualMachinePowerState.poweredOn, FakeModel.instance().getVirtualMachine(in.getName()).getRuntime().getPowerState());
    FakeModel.instance().addVMSnapshot("image2", "snap2");
    myFakeApi.checkImage(getImageByName("image2"));
    assertTrue(instancesProviderStub.isInstanceExpired(myProfile, in));
  }

  public void mark_instance_expired_when_sourcevmid_changes() throws MalformedURLException {
    final Map<String, CloudInstance> instancesMarkedExpired = new HashMap<String, CloudInstance>();
    final CloudInstancesProvider instancesProviderStub = new CloudInstancesProvider() {
      public void iterateInstances(@NotNull final CloudInstancesProviderCallback callback) {
        throw new UnsupportedOperationException(".iterateInstances");
      }

      public void iterateInstances(@NotNull final CloudInstancesProviderExtendedCallback callback) {
        throw new UnsupportedOperationException(".iterateInstances");
      }

      public void iterateProfileInstances(@NotNull final CloudProfile profile, @NotNull final CloudInstancesProviderCallback callback) {
        throw new UnsupportedOperationException(".iterateProfileInstances");
      }

      public void markInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        instancesMarkedExpired.put(instance.getInstanceId(),instance );
      }

      public boolean isInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        return instancesMarkedExpired.containsKey(instance.getInstanceId());
      }
    };
    myFakeApi = new FakeApiConnector(null, null, instancesProviderStub);
    recreateClient(250);

    final VmwareCloudInstance in = startNewInstanceAndWait("image2");
    assertEquals(VirtualMachinePowerState.poweredOn, FakeModel.instance().getVirtualMachine(in.getName()).getRuntime().getPowerState());

    final FakeVirtualMachine image2 = FakeModel.instance().getVirtualMachine("image2");
    final ManagedObjectReference mor = new ManagedObjectReference();
    mor.setVal("vm-123456");
    mor.setType("VirtualMachine");
    image2.setMOR(mor);

    myFakeApi.checkImage(getImageByName("image2"));
    assertTrue(instancesProviderStub.isInstanceExpired(myProfile, in));
  }

  public void shouldnt_mark_expired_if_vmsourceid_is_absent() throws MalformedURLException {
    final Map<String, CloudInstance> instancesMarkedExpired = new HashMap<String, CloudInstance>();
    final CloudInstancesProvider instancesProviderStub = new CloudInstancesProvider() {
      public void iterateInstances(@NotNull final CloudInstancesProviderCallback callback) {
        throw new UnsupportedOperationException(".iterateInstances");
      }

      public void iterateInstances(@NotNull final CloudInstancesProviderExtendedCallback callback) {
        throw new UnsupportedOperationException(".iterateInstances");
      }

      public void iterateProfileInstances(@NotNull final CloudProfile profile, @NotNull final CloudInstancesProviderCallback callback) {
        throw new UnsupportedOperationException(".iterateProfileInstances");
      }

      public void markInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        instancesMarkedExpired.put(instance.getInstanceId(),instance );
      }

      public boolean isInstanceExpired(@NotNull final CloudProfile profile, @NotNull final CloudInstance instance) {
        return instancesMarkedExpired.containsKey(instance.getInstanceId());
      }
    };
    myFakeApi = new FakeApiConnector(null, null, instancesProviderStub);
    recreateClient(250);

    final VmwareCloudInstance in = startNewInstanceAndWait("image2");
    assertEquals(VirtualMachinePowerState.poweredOn, FakeModel.instance().getVirtualMachine(in.getName()).getRuntime().getPowerState());

    final FakeVirtualMachine clonedVM = FakeModel.instance().getVirtualMachine(in.getName());
    final VirtualMachineConfigInfo oldConfig = clonedVM.getConfig();
    clonedVM.setConfigInfo(new VirtualMachineConfigInfo(){
      @Override
      public String getName() {
        return oldConfig.getName();
      }

      @Override
      public boolean isTemplate() {
        return oldConfig.isTemplate();
      }

      @Override
      public String getChangeVersion() {
        return oldConfig.getChangeVersion();
      }

      @Override
      public OptionValue[] getExtraConfig() {
        final OptionValue[] extraConfig = oldConfig.getExtraConfig();
        return Arrays.stream(extraConfig).filter(o->!o.getKey().equals(TEAMCITY_VMWARE_IMAGE_SOURCE_VM_ID)).toArray(OptionValue[]::new);
      }
    });

    recreateClient(250);
    new WaitFor(5*1000){
      protected boolean condition() {
        return getImageByName("image2").getInstances().size() == 1;
      }
    }.assertCompleted("Should have found started instance");



    final ManagedObjectReference mor = new ManagedObjectReference();
    mor.setVal("vm-123456");
    mor.setType("VirtualMachine");
    FakeModel.instance().getVirtualMachine("image2").setMOR(mor);


    myFakeApi.checkImage(getImageByName("image2"));
    assertFalse(instancesProviderStub.isInstanceExpired(myProfile, in));
  }

  public void shouldConsiderProfileInstancesLimit(){
    updateClientParameters(createMap(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "1"));
    recreateClient();
    assertTrue(myClient.canStartNewInstance(getImageByName("image2")));
    assertTrue(myClient.canStartNewInstance(getImageByName("image1")));
    assertTrue(myClient.canStartNewInstance(getImageByName("image_template")));
    startNewInstanceAndWait("image2");
    assertFalse(myClient.canStartNewInstance(getImageByName("image2")));
    assertFalse(myClient.canStartNewInstance(getImageByName("image1")));
    assertFalse(myClient.canStartNewInstance(getImageByName("image_template")));
  }

  public void checkCustomization(){
    final CustomizationSpec linuxSpec = FakeModel.instance().getCustomizationSpec("linux");
    final CustomizationLinuxOptions linuxOptions = new CustomizationLinuxOptions();
    linuxSpec.setOptions(linuxOptions);
    final VmwareCloudInstance newInstance = startNewInstanceAndWait("image_template");
    final FakeVirtualMachine virtualMachine = FakeModel.instance().getVirtualMachine(newInstance.getName());
    assertEquals(linuxSpec, virtualMachine.getCustomizationSpec());
  }

  public void handle_instances_deleted_in_the_middle_of_check() throws MalformedURLException, VmwareCheckedCloudException {
    if (true)
      throw new SkipException("Not relevant, because we now retrieve all data at once, without additional calls");
    final Set<String> instances2RemoveAfterGet = new HashSet<>();

    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID){
      @NotNull
      @Override
      protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        final Map<String, T> entities = super.findAllEntitiesAsMapOld(instanceType);
        final Map<String, T> result = new HashMap<String, T>();
        for (Map.Entry<String, T> entry : entities.entrySet()) {
          if (instances2RemoveAfterGet.contains(entry.getKey()) && entry.getValue() instanceof FakeVirtualMachine){
            final FakeVirtualMachine fvm = (FakeVirtualMachine) entry.getValue();
            fvm.setGone(true);
          }
          result.put(entry.getKey(), entry.getValue());
        }
        return result;
      }
    };
    final VmwareCloudInstance i1 = startNewInstanceAndWait("image2");
    final VmwareCloudInstance i2 = startNewInstanceAndWait("image2");
    final VmwareCloudInstance i3 = startNewInstanceAndWait("image2");

    instances2RemoveAfterGet.add(i1.getInstanceId());
    instances2RemoveAfterGet.add(i2.getInstanceId());

    final Map<String, AbstractInstance> image2Instances = myFakeApi.fetchInstances(getImageByName("image2"));
    assertEquals(1, image2Instances.size());
  }

  public void check_profile_id_and_server_uuid() throws Exception {

    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VmwareCloudInstance>() {
      public void check(final VmwareCloudInstance data) throws CheckedCloudException {
        assertTrue("image2-1".equals(data.getInstanceId()));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SOURCE_VM_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
        assertEquals(TEST_SERVER_UUID, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_SERVER_UUID));
        assertEquals(PROFILE_ID, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_PROFILE_ID));
      }
    }, false);

  }

  public void do_not_catch_instances_from_another_server() throws MalformedURLException {
    startNewInstanceAndWait("image2");
    myFakeApi = new FakeApiConnector("2345-6789-0123", PROFILE_ID);
    recreateClient();
    assertEquals(0, getImageByName("image2").getInstances().size());
    startNewInstanceAndWait("image2");
    recreateClient();
    assertEquals(1, getImageByName("image2").getInstances().size());
  }

  public void do_catch_instances_from_another_profile() throws MalformedURLException {
    startNewInstanceAndWait("image2");
    recreateClient();
    assertEquals(1, getImageByName("image2").getInstances().size());
    myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, "cp2");
    recreateClient();
    assertEquals(1, getImageByName("image2").getInstances().size());
  }

  public void start_instance_should_not_block_ui() throws MalformedURLException, InterruptedException, CheckedCloudException {
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    final Lock lock = new ReentrantLock();
    final AtomicBoolean shouldLock = new AtomicBoolean(false);
    try {
      myFakeApi = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID) {

        @Override
        protected <T extends ManagedEntity> T findEntityByIdNameNullableOld(final String name, final Class<T> instanceType, final Datacenter dc) throws VmwareCheckedCloudException {
          try {
            if (shouldLock.get()) {
              lock.lock(); // will stuck here
            }
            return super.findEntityByIdNameNullableOld(name, instanceType, dc);
          } finally {
            if (shouldLock.get())
              lock.unlock();
          }
        }

        @Override
        protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
          try {
            if (shouldLock.get()) {
              lock.lock(); // will stuck here
            }
            return super.findAllEntitiesOld(instanceType);
          } finally {
            if (shouldLock.get())
            lock.unlock();
          }
        }

        @Override
        protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMapOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
          try {
            if (shouldLock.get()) {
              lock.lock(); // will stuck here
            }
            return super.findAllEntitiesAsMapOld(instanceType);
          } finally {
            if (shouldLock.get())
            lock.unlock();
          }
        }

      };

      recreateClient();
      final VmwareCloudInstance startedInstance = startNewInstanceAndWait("image2");
      terminateAndDeleteIfNecessary(false, startedInstance);

      shouldLock.set(true);
      lock.lock();
      executor.execute(new Runnable() {
        public void run() {
          // start already existing clone
          myClient.startNewInstance(getImageByName("image2"), createUserData("image2_agent"));
          // start-stop instance
          myClient.startNewInstance(getImageByName("image1"), createUserData("image1_agent"));
          // clone a new one
          myClient.startNewInstance(getImageByName("image_template"), createUserData("image_template_agent"));
        }
      });
      executor.shutdown();
      assertTrue("canStart method blocks the thread!", executor.awaitTermination(100000, TimeUnit.MILLISECONDS));
    } finally {
      lock.unlock();
      executor.shutdownNow();
    }

  }

  public void should_consider_profile_limit_on_reload(){
    final CloudClientParameters clientParameters2 =
      new CloudClientParametersImpl(Collections.emptyMap(), CloudProfileUtil.collectionFromJson(
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:1,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]", PROJECT_ID));
    final CloudClientParameters clientParameters3 =
      new CloudClientParametersImpl(Collections.emptyMap(), CloudProfileUtil.collectionFromJson(
      "[{'source-id':'image_template',sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
      "',folder:'cf',pool:'rp',maxInstances:1,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]",
      PROJECT_ID));
    final VMWareCloudClient client2 = recreateClient(myClient, clientParameters2);
    final VMWareCloudClient client3 = recreateClient(null, clientParameters3);

    startNewInstanceAndWait(client2, "image2");

    startNewInstanceAndWait(client3, "image_template");


    client2.dispose();
    client3.dispose();

    final VMWareCloudClient client3_1 = recreateClient(null, clientParameters3);
    final VMWareCloudClient client2_1 = recreateClient(null, clientParameters2);
    try {
      startNewInstanceAndWait(client3_1, "image_template");
      fail("Shouldn't start more of client3");
    } catch (Exception ex) {

    }


    try {
      startNewInstanceAndWait(client2_1, "image2");
      fail("Shouldn't start more of image2");
    } catch (Exception ex) {

    }

  }

  public void should_consider_profile_limit_on_reload_2(){
    final CloudClientParameters clientParameters2 = new CloudClientParametersImpl(
      Collections.emptyMap(), CloudProfileUtil.collectionFromJson(
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:1,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]", PROJECT_ID));
    final CloudClientParameters clientParameters3 = new CloudClientParametersImpl(
      Collections.emptyMap(), CloudProfileUtil.collectionFromJson(
      "[{'source-id':'image_template',sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
      "',folder:'cf',pool:'rp',maxInstances:1,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]",
      PROJECT_ID));
    final VMWareCloudClient client2 = recreateClient(myClient, clientParameters2);
    final VMWareCloudClient client3 = recreateClient(null, clientParameters3);

    startNewInstanceAndWait(client2, "image2");

    startNewInstanceAndWait(client3, "image_template");


    client2.dispose();
    client3.dispose();

    final VMWareCloudClient client3_1 = recreateClient(null, clientParameters3, false);
    final VMWareCloudClient client2_1 = recreateClient(null, clientParameters2, false);
    new WaitFor(5000) {
      @Override
      protected boolean condition() {
        return client3_1.isInitialized() || client2_1.isInitialized();
      }
    };
    try {
      if (client3_1.isInitialized() && client3_1.canStartNewInstance(getImageByName("image_template"))) {
        startNewInstanceAndWait(client3_1, "image_template");
        fail("Shouldn't start more of client3");
      }
    } catch (Exception ex) {

    }
    try {
      if (client2_1.isInitialized() && client2_1.canStartNewInstance(getImageByName("image2"))) {
        startNewInstanceAndWait(client2_1, "image2");
        fail("Shouldn't start more of image2");
      }
    } catch (Exception ex) {

    }

    new WaitFor(5000) {
      @Override
      protected boolean condition() {
        return client3_1.isInitialized() && client2_1.isInitialized();
      }
    }.assertCompleted("clients should be initialized in time");

  }

  @TestFor(issues = "TW-47486")
  public void shouldnt_throw_error_when_stopping_nonexisting_instance(){
    setInternalProperty("teamcity.vsphere.instance.status.update.delay.ms", "250000");
    recreateClient();
    final VmwareCloudInstance startedInstance = startNewInstanceAndWait("image_template");
    final FakeVirtualMachine vm = FakeModel.instance().getVirtualMachine(startedInstance.getName());
    assertNotNull(vm);
    FakeModel.instance().removeVM(vm.getName());
    final VmwareCloudImage image = startedInstance.getImage();
    assertContains(image.getInstances(), startedInstance);
    myClient.terminateInstance(startedInstance);
    assertNotContains(image.getInstances(), startedInstance);
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
    map.put(CloudConstants.PROFILE_ID, PROFILE_ID);
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
            return (myFakeApi.getAllVMsMap(false).get(name) == null);
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
    new WaitFor(3 * 1000) {

      @Override
      protected boolean condition() {
        final VmwareInstance vm;
        try {
          vm = myFakeApi.getAllVMsMap(false).get(instance.getName());
          return vm != null && vm.getInstanceStatus() == InstanceStatus.RUNNING;
        } catch (CheckedCloudException e) {
          return false;
        }
      }
    };
    final VmwareInstance vm = myFakeApi.getAllVMsMap(false).get(instance.getName());
    assertNotNull("instance " + instance.getName() + " must exists", vm);
    assertEquals("Must be running", InstanceStatus.RUNNING, vm.getInstanceStatus());
    if (instanceChecker != null) {
      instanceChecker.check(instance);
    }
    return instance;
  }

  private VmwareCloudInstance startNewInstanceAndWait(VMWareCloudClient client, String imageName) {
    return startNewInstanceAndCheck(client, imageName, new HashMap<String, String>(), true);
  }
  private VmwareCloudInstance startNewInstanceAndWait(String imageName) {
    return startNewInstanceAndWait(imageName, new HashMap<String, String>());
  }

  private VmwareCloudInstance startNewInstanceAndWait(String imageName, Map<String, String> parameters) {
    return startNewInstanceAndCheck(imageName, parameters, true);
  }

  private VmwareCloudInstance startNewInstanceAndCheck(String imageName, Map<String, String> parameters, boolean instanceShouldStart) {
    return startNewInstanceAndCheck(myClient, imageName, parameters, instanceShouldStart);
  }
  private VmwareCloudInstance startNewInstanceAndCheck(VMWareCloudClient client, String imageName, Map<String, String> parameters, boolean instanceShouldStart) {
    final CloudInstanceUserData userData = createUserData(imageName + "_agent", parameters);
    final VmwareCloudImage image = getImageByName(client, imageName);
    final Collection<VmwareCloudInstance> runningInstances = image
      .getInstances()
      .stream()
      .filter(i->i.getStatus() == InstanceStatus.RUNNING)
      .collect(Collectors.toList());

    final VmwareCloudInstance vmwareCloudInstance = client.startNewInstance(image, userData);
    final boolean ready = vmwareCloudInstance.isReady();
    System.out.printf("Instance '%s'. Ready: %b%n", vmwareCloudInstance.getName(), ready);
    final WaitFor waitFor = new WaitFor(2 * 1000) {
      @Override
      protected boolean condition() {
        if (ready) {
          return vmwareCloudInstance.getStatus() == InstanceStatus.RUNNING;
        } else {
          return image
            .getInstances()
            .stream()
            .anyMatch(i->i.getStatus() == InstanceStatus.RUNNING && !runningInstances.contains(i));
        }
      }
    };
    if (instanceShouldStart) {
      waitFor.assertCompleted();

      if (!ready) {
        final VmwareCloudInstance startedInstance = image
          .getInstances()
          .stream()
          .filter(i -> i.getStatus() == InstanceStatus.RUNNING && !runningInstances.contains(i)).findAny().get();
        assertNotNull(startedInstance);
        return startedInstance;
      } else {
        return vmwareCloudInstance;
      }
    } else {
      assertFalse(waitFor.isConditionRealized());
      return null;
    }
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
    return getImageByName(myClient, name);
  }

  private VmwareCloudImage getImageByName(final VMWareCloudClient client, final String name) {
    for (CloudImage image : client.getImages()) {
      if (image.getName().equals(name)) {
        return (VmwareCloudImage)image;
      }
    }
    throw new RuntimeException("unable to find image by name: " + name);
  }

  private void recreateClient(){
    recreateClient(TeamCityProperties.getLong("teamcity.vsphere.instance.status.update.delay.ms", 250));
  }

  private void recreateClient(final long updateDelay) {
    myClient = recreateClient(myClient, myClientParameters, updateDelay, true);
  }

  private VMWareCloudClient recreateClient(final VMWareCloudClient oldClient,
                                           final CloudClientParameters parameters){
    final long updateTime = TeamCityProperties.getLong("teamcity.vsphere.instance.status.update.delay.ms", 250);
    return recreateClient(oldClient, parameters, updateTime, true);
  }
  private VMWareCloudClient recreateClient(final VMWareCloudClient oldClient,
                                           final CloudClientParameters parameters,
                                           boolean waitForInitialization){
    final long updateTime = TeamCityProperties.getLong("teamcity.vsphere.instance.status.update.delay.ms", 250);
    return recreateClient(oldClient, parameters, updateTime, waitForInitialization);
  }

  private VMWareCloudClient recreateClient(final VMWareCloudClient oldClient,
                                           final CloudClientParameters parameters,
                                           long updateTime,
                                           boolean waitForInitialization){
    if (oldClient != null) {
      oldClient.dispose();
    }
    myProfile = VmwareTestUtils.createProfileFromProps(parameters);
    final VMWareCloudClient newClient = new VMWareCloudClient(myProfile, myFakeApi, myTaskManager, myIdxStorage);

    final Collection<VmwareCloudImageDetails> images = VMWareCloudClientFactory.parseImageDataInternal(parameters);
    newClient.populateImagesData(images, updateTime, updateTime);
    if (waitForInitialization) {
      new WaitFor(5000) {
        @Override
        protected boolean condition() {
          return newClient.isInitialized();
        }
      }.assertCompleted("Must be initialized");
    }
    return newClient;
  }

  @AfterMethod
  public void tearDown() throws Exception {
    removeInternalProperty("teamcity.vsphere.instance.status.update.delay.ms");
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
