package jetbrains.buildServer.clouds.vmware;

import com.vmware.vim25.OptionValue;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.*;
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
    myClientParameters.setParameter("images", "image1\nimage2@snap\nimage_template");
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
    assertEquals(VMWareCloudErrorInfoFactory.noSuchFolder("cf").getMessage(), myClient.getErrorInfo().getMessage());
    FakeModel.instance().addFolder("cf");

    FakeModel.instance().removeResourcePool("rp");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(VMWareCloudErrorInfoFactory.noSuchResourcePool("rp").getMessage(), myClient.getErrorInfo().getMessage());
    FakeModel.instance().addResourcePool("rp");

    FakeModel.instance().removeVM("image1");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(VMWareCloudErrorInfoFactory.noSuchImages(Collections.singletonList("image1")).getMessage(), myClient.getErrorInfo().getMessage());
    FakeModel.instance().addVM("image1");

    FakeModel.instance().removeVM("image2");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(VMWareCloudErrorInfoFactory.noSuchImages(Collections.singletonList("image2")).getMessage(), myClient.getErrorInfo().getMessage());
    FakeModel.instance().addVM("image2");

    FakeModel.instance().removeVmSnaphot("image2", "snap");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(VMWareCloudErrorInfoFactory.noSuchSnapshot("snap", "image2").getMessage(), myClient.getErrorInfo().getMessage());
  }

  public void check_start_type() throws MalformedURLException, RemoteException {
    myClientParameters.setParameter("images", "image1\nimage_template");

    myFakeApi = new FakeApiConnector(){
      @Override
      public Map<String, VirtualMachine> getVirtualMachines() throws RemoteException {
        final Map<String, VirtualMachine> instances = super.getVirtualMachines();
        instances.put("image_template", new FakeVirtualMachine("image_template", true, false));
        instances.put("image1", new FakeVirtualMachine("image1", false, false));
        return instances;
      }
    };

    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.START.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertEquals(VMWareImageStartType.CLONE, getImageByName("image_template").getStartType());
    assertEquals(VMWareImageStartType.START, getImageByName("image1").getStartType());

    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.CLONE.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertEquals(VMWareImageStartType.CLONE, getImageByName("image_template").getStartType());
    assertEquals(VMWareImageStartType.CLONE, getImageByName("image1").getStartType());

    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.LINKED_CLONE.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    // doesn't depend on snapshot existence
    assertEquals(VMWareImageStartType.LINKED_CLONE, getImageByName("image_template").getStartType());
    assertEquals(VMWareImageStartType.LINKED_CLONE, getImageByName("image1").getStartType());
  }

  public void check_startup_parameters() throws MalformedURLException, RemoteException {
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());

    startNewInstance("image1", Collections.singletonMap("customParam1", "customValue1"));
    final VirtualMachine vm = myFakeApi.getVirtualMachines().get("image1");
    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    final String userDataEncoded = getExtraConfigValue(extraConfig, VMWarePropertiesNames.USER_DATA);
    final CloudInstanceUserData cloudInstanceUserData = CloudInstanceUserData.deserialize(userDataEncoded);
    assertEquals("customValue1", cloudInstanceUserData.getCustomAgentConfigurationParameters().get("customParam1"));
  }


  public void check_vm_clone() throws MalformedURLException, RemoteException {
    startAndCheckCloneDeletedAfterTermination("image1", VMWareImageStartType.START, null, false);
    startAndCheckCloneDeletedAfterTermination("image_template", VMWareImageStartType.START, new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) {
        assertTrue(data.getInstanceId().startsWith("image_template"));
        assertTrue(data.getInstanceId().contains("clone"));
      }
    }, true);
    startAndCheckCloneDeletedAfterTermination("image2", VMWareImageStartType.LINKED_CLONE, new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) {
        assertTrue(data.getInstanceId().startsWith("image_template"));
        assertTrue(data.getInstanceId().contains("clone"));
        //TODO add check for this
      }
    }, false);

    startAndCheckCloneDeletedAfterTermination("image2", VMWareImageStartType.LINKED_CLONE, new Checker<VMWareCloudInstance>() {
      public void check(final VMWareCloudInstance data) {
        assertTrue(data.getInstanceId().startsWith("image_template"));
        assertTrue(data.getInstanceId().contains("clone"));
        //TODO add check for this
      }
    }, false);
  }

  private void startAndCheckCloneDeletedAfterTermination(String imageName) throws MalformedURLException, RemoteException {
    startAndCheckCloneDeletedAfterTermination(imageName, VMWareImageStartType.START);
  }

  private void startAndCheckCloneDeletedAfterTermination(String imageName, VMWareImageStartType cloneBehaviour) throws MalformedURLException, RemoteException {
    startAndCheckCloneDeletedAfterTermination(imageName, cloneBehaviour, null, true);
  }


  private void startAndCheckCloneDeletedAfterTermination(String imageName,
                                                         VMWareImageStartType cloneBehaviour,
                                                         Checker<VMWareCloudInstance> instanceChecker,
                                                         boolean shouldBeDeleted)
    throws MalformedURLException, RemoteException {
    myClientParameters.setParameter("cloneBehaviour", cloneBehaviour.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());

    final VMWareCloudInstance instance = startNewInstance(imageName);
    final VirtualMachine vm = myFakeApi.getVirtualMachines().get(instance.getName());
    assertNotNull("instance must exists", vm);
    assertEquals("Must be running", InstanceStatus.RUNNING, myFakeApi.getInstanceStatus(vm));
    if (instanceChecker != null) {
      instanceChecker.check(instance);
    }
    myClient.terminateInstance(instance);
    assertEquals("template clone should be deleted after execution", shouldBeDeleted, myFakeApi.getVirtualMachines().get(instance.getName()) == null);

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
    void check(T data);
  }
}
