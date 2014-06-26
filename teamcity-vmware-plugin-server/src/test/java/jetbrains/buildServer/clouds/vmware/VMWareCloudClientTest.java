package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.WaitFor;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
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
    myClientParameters = new CloudClientParameters();
    myClientParameters.setParameter("serverUrl", "http://localhost:8080");
    myClientParameters.setParameter("username", "un");
    myClientParameters.setParameter("password", "pw");
    myClientParameters.setParameter("cloneFolder", "cf");
    myClientParameters.setParameter("resourcePool", "rp");
    myClientParameters.setParameter("vmware_images_data", "image1;;cf;rp;START;3;X;:" +
                                                          "image2;snap;cf;rp;ON_DEMAND_CLONE;3;X;:" +
                                                          "image_template;;cf;rp;CLONE;3;X;:");
    //myClientParameters.setParameter("images", "image1\nimage2@snap\nimage_template");
    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.START.name());

    myFakeApi = new FakeApiConnector();
    FakeModel.instance().addFolder("cf");
    FakeModel.instance().addResourcePool("rp");
    FakeModel.instance().addVM("image1");
    FakeModel.instance().addVM("image2");
    FakeModel.instance().addVM("image_template");
    FakeModel.instance().addVMSnapshot("image2", "snap");
  }

  public void validate_objects_on_client_creation() throws MalformedURLException, RemoteException {
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());

    FakeModel.instance().removeFolder("cf");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchFolder("cf").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addFolder("cf");

    FakeModel.instance().removeResourcePool("rp");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchResourcePool("rp").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addResourcePool("rp");

    FakeModel.instance().removeVM("image1");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchVM("image1").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addVM("image1");

    FakeModel.instance().removeVM("image2");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(wrapWithArraySymbols(VMWareCloudErrorInfoFactory.noSuchVM("image2").getMessage()),
                 myClient.getErrorInfo().getMessage());
    FakeModel.instance().addVM("image2");
  }

  public void check_start_type() throws MalformedURLException, RemoteException {

    myFakeApi = new FakeApiConnector(){
      @Override
      public Map<String, VirtualMachine> getVirtualMachines(boolean filterClones) throws RemoteException {
        final Map<String, VirtualMachine> instances = super.getVirtualMachines(filterClones);
        instances.put("image_template", new FakeVirtualMachine("image_template", true, false));
        instances.put("image1", new FakeVirtualMachine("image1", false, false));
        return instances;
      }
    };

  }

  public void check_on_demand_snapshot(){
    myClientParameters.setParameter("vmware_images_data", "image1;;cf;rp;ON_DEMAND_CLONE;3;X;:");
  }

  public void check_startup_parameters() throws MalformedURLException, RemoteException {
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());

    startNewInstance("image1", Collections.singletonMap("customParam1", "customValue1"));
    final VirtualMachine vm = myFakeApi.getVirtualMachines(true).get("image1");
    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    final String userDataEncoded = getExtraConfigValue(extraConfig, VMWarePropertiesNames.USER_DATA);
    final CloudInstanceUserData cloudInstanceUserData = CloudInstanceUserData.deserialize(userDataEncoded);
    assertEquals("customValue1", cloudInstanceUserData.getCustomAgentConfigurationParameters().get("customParam1"));
  }

  private static String wrapWithArraySymbols(String str){
    return String.format("[%s]", str);
  }


  public void check_vm_clone() throws MalformedURLException, RemoteException {
    startAndCheckCloneDeletedAfterTermination("image1", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertEquals("image1", data.getInstanceId());
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertNull(vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
      }
    }, false);
    startAndCheckCloneDeletedAfterTermination("image_template", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertTrue(data.getInstanceId().startsWith("image_template"));
        assertTrue(data.getInstanceId().contains("clone"));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image_template", vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
      }
    }, true);
    startAndCheckCloneDeletedAfterTermination("image2", new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) throws RemoteException {
        assertTrue(data.getInstanceId().startsWith("image2"));
        assertTrue(data.getInstanceId().contains("clone"));
        final Map<String, String> vmParams = myFakeApi.getVMParams(data.getInstanceId());
        assertEquals("true",vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_CLONED_INSTANCE));
        assertEquals("image2",vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_NAME));
        assertEquals("snap",vmParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
      }
    }, false);
  }

  private void startAndCheckCloneDeletedAfterTermination(String imageName) throws MalformedURLException, RemoteException {
    startAndCheckCloneDeletedAfterTermination(imageName, VMWareImageStartType.START);
  }

  private void startAndCheckCloneDeletedAfterTermination(String imageName, VMWareImageStartType cloneBehaviour) throws MalformedURLException, RemoteException {
    startAndCheckCloneDeletedAfterTermination(imageName, null, true);
  }


  private void startAndCheckCloneDeletedAfterTermination(String imageName,
                                                         Checker<VMWareCloudInstance> instanceChecker,
                                                         boolean shouldBeDeleted)
    throws MalformedURLException, RemoteException {
    try {
      myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
      assertNull(myClient.getErrorInfo());

      final VMWareCloudInstance instance = startNewInstance(imageName);
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
      myClient.terminateInstance(instance);
      assertEquals("template clone should be deleted after execution", shouldBeDeleted, myFakeApi.getVirtualMachines(false).get(instance.getName()) == null);
    } finally {
      myClient.dispose();
    }
  }


  private VMWareCloudInstance startNewInstance(String imageName){
    return startNewInstance(imageName, new HashMap<String, String>());
  }

  private VMWareCloudInstance startNewInstance(String imageName, Map<String, String> parameters){
    final CloudInstanceUserData userData = new CloudInstanceUserData(
      imageName + "_agent", "authToken", "http://localhost:8080", 30*60*1000l, "My profile",parameters);
    return (VMWareCloudInstance)myClient.startNewInstance(getImageByName(imageName), userData);
  }

  public void check_snapshot_existence_for_linked_clone(){

  }

  private static String getExtraConfigValue(final OptionValue[] extraConfig, final String key){
    for (OptionValue param : extraConfig) {
      if (param.getKey().equals(key))
        return String.valueOf(param.getValue());
    }
    return null;
  }

  private VMWareCloudImage getImageByName(final String name){
    for (CloudImage image : myClient.getImages()) {
      if (image.getName().equals(name)){
        return (VMWareCloudImage)image;
      }
    }
    throw new RuntimeException("unable to find image by name: " + name);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private static interface Checker<T>{
    void check(T data) throws RemoteException;
  }
}
