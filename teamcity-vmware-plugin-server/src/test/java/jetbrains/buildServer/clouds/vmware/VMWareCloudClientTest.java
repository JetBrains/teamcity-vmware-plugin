package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitFor;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorInfoFactory;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:04 PM
 */
@Test
public class VMWareCloudClientTest extends BaseTestCase {

  private VMWareCloudClient myClient;
  private FakeApiConnector myFakeApi;
  private CloudClientParameters myClientParameters;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    System.setProperty("teamcity.vsphere.instance.status.update.delay.ms", "250");
    myClientParameters = new CloudClientParameters();
    myClientParameters.setParameter("serverUrl", "http://localhost:8080");
    myClientParameters.setParameter("username", "un");
    myClientParameters.setParameter("password", "pw");
    myClientParameters.setParameter("vmware_images_data", "image1;;;;START;3;X;:" +
                                                          "image2;snap*;cf;rp;ON_DEMAND_CLONE;3;X;:" +
                                                          "image_template;;cf;rp;CLONE;3;X;:");

    myFakeApi = new FakeApiConnector();
    FakeModel.instance().addFolder("cf");
    FakeModel.instance().addResourcePool("rp");
    FakeModel.instance().addVM("image1");
    FakeModel.instance().addVM("image2");
    FakeModel.instance().addVM("image_template");
    FakeModel.instance().addVMSnapshot("image2", "snap");

    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());
  }

  public void validate_objects_on_client_creation() throws MalformedURLException, RemoteException {
    FakeModel.instance().removeFolder("cf");
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
    FakeModel.instance().addVM("image2");
  }

  public void check_start_type() throws MalformedURLException, RemoteException {

    myFakeApi = new FakeApiConnector() {
      @Override
      public Map<String, VirtualMachine> getVirtualMachines(boolean filterClones) throws RemoteException {
        final Map<String, VirtualMachine> instances = super.getVirtualMachines(filterClones);
        instances.put("image_template", new FakeVirtualMachine("image_template", true, false));
        instances.put("image1", new FakeVirtualMachine("image1", false, false));
        return instances;
      }
    };

  }

  public void check_on_demand_snapshot() {
    myClientParameters.setParameter("vmware_images_data", "image1;;cf;rp;ON_DEMAND_CLONE;3;X;:");
  }

  public void check_startup_parameters() throws MalformedURLException, RemoteException {
    startNewInstanceAndWait("image1", Collections.singletonMap("customParam1", "customValue1"));
    final VirtualMachine vm = myFakeApi.getVirtualMachines(true).get("image1");
    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    final String userDataEncoded = getExtraConfigValue(extraConfig, VMWarePropertiesNames.USER_DATA);
    assertNotNull(userDataEncoded);
    final CloudInstanceUserData cloudInstanceUserData = CloudInstanceUserData.deserialize(userDataEncoded);
    assertEquals("customValue1", cloudInstanceUserData.getCustomAgentConfigurationParameters().get("customParam1"));
  }

  public void check_vm_clone() throws Exception {
    startAndCheckCloneDeletedAfterTermination("image1", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertEquals("image1", data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
      }
    }, false);
    assertEquals(3, FakeModel.instance().getVms().size());
    startAndCheckCloneDeletedAfterTermination("image_template", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertTrue(data.getInstanceId().startsWith("image_template"));
        assertTrue(data.getInstanceId().contains("clone"));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image_template", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
        assertTrue(StringUtil.isEmpty(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT)));
      }
    }, true);
    assertEquals(3, FakeModel.instance().getVms().size());
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertTrue(data.getInstanceId().startsWith("image2"));
        assertTrue(data.getInstanceId().contains("clone"));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
  }

  public void on_demand_clone_should_use_existing_vm_when_one_exists() throws Exception {
    final AtomicReference<String> instanceId = new AtomicReference<String>();
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        instanceId.set(data.getInstanceId());
        assertTrue(data.getInstanceId().startsWith("image2"));
        assertTrue(data.getInstanceId().contains("clone"));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
    assertContains(FakeModel.instance().getVms().keySet(), instanceId.get());
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertEquals(instanceId.get(), data.getInstanceId());
        assertTrue(data.getInstanceId().contains("clone"));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
  }

  public void on_demand_clone_should_create_more_when_not_enough() throws Exception {
    final AtomicReference<String> instanceId = new AtomicReference<String>();

    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) {
        instanceId.set(data.getInstanceId());
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
    final VMWareCloudInstance instance1 = startAndCheckInstance("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertEquals(instanceId.get(), data.getInstanceId());
      }
    });
    assertEquals(4, FakeModel.instance().getVms().size());
    final VMWareCloudInstance instance2 = startAndCheckInstance("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
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

    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("snap", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
        instanceId.set(data.getInstanceId());
      }
    }, false);
    assertEquals(4, FakeModel.instance().getVms().size());
    FakeModel.instance().addVMSnapshot("image2", "snap2");

    final VMWareCloudInstance instance1 = startAndCheckInstance("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertNotSame(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("snap2", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    });
    assertEquals(4, FakeModel.instance().getVms().size());
    assertNotContains(FakeModel.instance().getVms().keySet(), instanceId.get());
    final VMWareCloudInstance instance2 = startAndCheckInstance("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
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
    myClientParameters.setParameter("vmware_images_data", "image1;;cf;rp;ON_DEMAND_CLONE;3;X;:" +
                                                          "image2;snap*;cf;rp;ON_DEMAND_CLONE;3;X;:" +
                                                          "image_template;;cf;rp;CLONE;3;X;:");
    recreateClient();


    final AtomicReference<String> instanceId = new AtomicReference<String>();
    final String originalChangeVersion = FakeModel.instance().getVirtualMachine("image1").getConfig().getChangeVersion();
    startAndCheckCloneDeletedAfterTermination("image1", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
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

    final VMWareCloudInstance instance1 = startAndCheckInstance("image1", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertNotSame(instanceId.get(), data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals(updatedChangeVersion, vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_CHANGE_VERSION));
      }
    });
    assertEquals(4, FakeModel.instance().getVms().size());
    assertNotContains(FakeModel.instance().getVms().keySet(), instanceId.get());
    final VMWareCloudInstance instance2 = startAndCheckInstance("image1", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
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
        for (VMWareCloudImage image : myClient.getImages()) {
          final Collection<VMWareCloudInstance> instances = image.getInstances();
          cnt += instances.size();
          for (VMWareCloudInstance instance : instances) {
            assertEquals(InstanceStatus.RUNNING, instance.getStatus());
          }
        }
        return cnt == 3;
      }
    };
    for (VMWareCloudImage image : myClient.getImages()) {
      final Collection<VMWareCloudInstance> instances = image.getInstances();
      assertEquals(1, instances.size());
      final VMWareCloudInstance instance = instances.iterator().next();
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

  public void should_limit_new_instances_count(){
    int countStarted = 0;
    final VMWareCloudImage image_template = getImageByName("image_template");
    while (myClient.canStartNewInstance(image_template)){
      final CloudInstanceUserData userData = new CloudInstanceUserData(
        image_template + "_agent", "authToken", "http://localhost:8080", 30 * 60 * 1000l, "My profile", Collections.<String, String>emptyMap());
      myClient.startNewInstance(image_template, userData);
      countStarted++;
      assertTrue(countStarted <= 3);
    }
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = ".*Unable to find snapshot.*")
  public void shouldnt_start_when_snapshot_is_missing(){
    final VMWareCloudImage image2 = getImageByName("image2");
    FakeModel.instance().removeVmSnaphot(image2.getName(), "snap");
    startNewInstanceAndWait("image2");
  }

  public void should_power_off_if_no_guest_tools_avail(){
    final VMWareCloudImage image_template = getImageByName("image_template");
    final VMWareCloudInstance instance = startNewInstanceAndWait("image_template");
    assertContains(image_template.getInstances(),  instance);
    FakeModel.instance().getVirtualMachine(instance.getName()).disableGuestTools();
    myClient.terminateInstance(instance);
    new WaitFor(500) {
      @Override
      protected boolean condition() {
        return instance.getStatus() == InstanceStatus.STOPPED;
      }
    };
    assertNull(FakeModel.instance().getVirtualMachine(instance.getName()));
    assertNotContains(image_template.getInstances(),  instance);
  }

  private static String wrapWithArraySymbols(String str) {
    return String.format("[%s]", str);
  }

  private void startAndCheckCloneDeletedAfterTermination(String imageName,
                                                         Checker<VMWareCloudInstance> instanceChecker,
                                                         boolean shouldBeDeleted) throws Exception {
    final VMWareCloudInstance instance = startAndCheckInstance(imageName, instanceChecker);
    terminateAndDeleteIfNecessary(shouldBeDeleted, instance);
  }

  private void terminateAndDeleteIfNecessary(final boolean shouldBeDeleted, final VMWareCloudInstance instance) throws RemoteException {
    myClient.terminateInstance(instance);
    final String name = instance.getName();
    final WaitFor waitFor = new WaitFor(10 * 1000) {
      @Override
      protected boolean condition() {
        try {
          if (shouldBeDeleted) {
            return (myFakeApi.getVirtualMachines(false).get(name) == null);
          } else {
            return myFakeApi.getInstanceDetails(name).getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff;
          }
        } catch (RemoteException e) {
          return false;
        }
      }
    };
    waitFor.assertCompleted("template clone should be deleted after execution");
  }

  private VMWareCloudInstance startAndCheckInstance(final String imageName, final Checker<VMWareCloudInstance> instanceChecker) throws Exception {
    final VMWareCloudInstance instance = startNewInstanceAndWait(imageName);
    new WaitFor(10 * 1000) {

      @Override
      protected boolean condition() {
        final VirtualMachine vm;
        try {
          vm = myFakeApi.getVirtualMachines(false).get(instance.getName());
          return vm != null && myFakeApi.getInstanceStatus(vm) == InstanceStatus.RUNNING;
        } catch (RemoteException e) {
          return false;
        }
      }
    };
    final VirtualMachine vm = myFakeApi.getVirtualMachines(false).get(instance.getName());
    assertNotNull("instance " + instance.getName() + " must exists", vm);
    assertEquals("Must be running", InstanceStatus.RUNNING, myFakeApi.getInstanceStatus(vm));
    if (instanceChecker != null) {
      instanceChecker.check(instance);
    }
    return instance;
  }


  private VMWareCloudInstance startNewInstanceAndWait(String imageName) {
    return startNewInstanceAndWait(imageName, new HashMap<String, String>());
  }

  private VMWareCloudInstance startNewInstanceAndWait(String imageName, Map<String, String> parameters) {
    final CloudInstanceUserData userData = new CloudInstanceUserData(
      imageName + "_agent", "authToken", "http://localhost:8080", 30 * 60 * 1000l, "My profile", parameters);
    final VMWareCloudInstance vmWareCloudInstance = myClient.startNewInstance(getImageByName(imageName), userData);
    final WaitFor waitFor = new WaitFor(10 * 1000) {
      @Override
      protected boolean condition() {
        return vmWareCloudInstance.getStatus() == InstanceStatus.RUNNING;
      }
    };
    waitFor.assertCompleted();
    return vmWareCloudInstance;
  }

  private static String getExtraConfigValue(final OptionValue[] extraConfig, final String key) {
    for (OptionValue param : extraConfig) {
      if (param.getKey().equals(key)) {
        return String.valueOf(param.getValue());
      }
    }
    return null;
  }

  private VMWareCloudImage getImageByName(final String name) {
    for (CloudImage image : myClient.getImages()) {
      if (image.getName().equals(name)) {
        return (VMWareCloudImage)image;
      }
    }
    throw new RuntimeException("unable to find image by name: " + name);
  }

  private void recreateClient() throws MalformedURLException, RemoteException {
    myClient.dispose();
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
    if (myClient != null) {
      myClient.dispose();
    }
    FakeModel.instance().clear();
  }

  private static interface Checker<T> {
    void check(T data) throws Exception;
  }
}
