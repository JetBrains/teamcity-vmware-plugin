package jetbrains.buildServer.clouds.vmware;

import com.vmware.vim25.OptionValue;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeVirtualMachine;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:04 PM
 */
@Test
public class VMWareCloudClientTest {

  private VMWareCloudClient myClient;
  private FakeApiConnector myFakeApi;
  private CloudClientParameters myClientParameters;

  @BeforeMethod
  public void setUp() throws MalformedURLException, RemoteException {
    myClientParameters = new CloudClientParameters();
    myClientParameters.setParameter("serverUrl", "http://localhost:8080");
    myClientParameters.setParameter("username", "un");
    myClientParameters.setParameter("password", "pw");
    myClientParameters.setParameter("cloneFolder", "cf");
    myClientParameters.setParameter("resourcePool", "rp");
    myClientParameters.setParameter("images", "image1\nimage2@snap\nimage_template");
    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.START.name());

    myFakeApi = new FakeApiConnector();
    myFakeApi.addFolder("cf");
    myFakeApi.addResourcePool("rp");
    myFakeApi.addVM("image1");
    myFakeApi.addVM("image2");
    myFakeApi.addVM("image_template");
    myFakeApi.addVMSnapshot("image2", "snap");
  }

  public void validate_objects_on_client_creation() throws MalformedURLException, RemoteException {
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());

    myFakeApi.removeFolder("cf");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(myClient.getErrorInfo().getMessage(), VMWareCloudErrorInfoFactory.noSuchFolder("cf").getMessage());
    myFakeApi.addFolder("cf");

    myFakeApi.removeResourcePool("rp");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(myClient.getErrorInfo().getMessage(), VMWareCloudErrorInfoFactory.noSuchResourcePool("rp").getMessage());
    myFakeApi.addResourcePool("rp");

    myFakeApi.removeVM("image1");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(myClient.getErrorInfo().getMessage(), VMWareCloudErrorInfoFactory.noSuchImages(Collections.singletonList("image1")).getMessage());
    myFakeApi.addVM("image1");

    myFakeApi.removeVM("image2");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(myClient.getErrorInfo().getMessage(), VMWareCloudErrorInfoFactory.noSuchImages(Collections.singletonList("image2")).getMessage());
    myFakeApi.addVM("image2");

    myFakeApi.removeVmSnaphot("image2", "snap");
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNotNull(myClient.getErrorInfo());
    assertEquals(myClient.getErrorInfo().getMessage(), VMWareCloudErrorInfoFactory.noSuchSnapshot("snap", "image2").getMessage());
  }

  public void check_start_type() throws MalformedURLException, RemoteException {
    myClientParameters.setParameter("images", "image1\nimage_template");

    myFakeApi = new FakeApiConnector(){
      @Override
      public Map<String, VirtualMachine> getInstances() throws RemoteException {
        final Map<String, VirtualMachine> instances = super.getInstances();
        instances.put("image_template", new FakeVirtualMachine("image_template", true, false));
        instances.put("image1", new FakeVirtualMachine("image1", false, false));
        return instances;
      }
    };

    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.START.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertEquals(getImageByName("image_template").getStartType(), VMWareImageStartType.CLONE);
    assertEquals(getImageByName("image1").getStartType(), VMWareImageStartType.START);

    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.CLONE.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertEquals(getImageByName("image_template").getStartType(), VMWareImageStartType.CLONE);
    assertEquals(getImageByName("image1").getStartType(), VMWareImageStartType.CLONE);

    myClientParameters.setParameter("cloneBehaviour", VMWareImageStartType.LINKED_CLONE.name());
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    // doesn't depend on snapshot existence
    assertEquals(getImageByName("image_template").getStartType(), VMWareImageStartType.LINKED_CLONE);
    assertEquals(getImageByName("image1").getStartType(), VMWareImageStartType.LINKED_CLONE);
  }

  public void check_startup_parameters() throws MalformedURLException, RemoteException {
    myClient = new VMWareCloudClient(myClientParameters, myFakeApi);
    assertNull(myClient.getErrorInfo());

    final HashMap<String, String> myCustomParams = new HashMap<String, String>();
    myCustomParams.put("customParam1", "customValue1");
    final CloudInstanceUserData userData = new CloudInstanceUserData(
      "image1_agent", "authToken", "http://localhost:8080", 30*60*1000l, "My profile", myCustomParams
    );
    final CloudInstance instance = myClient.startNewInstance(getImageByName("image1"), userData);
    final VirtualMachine vm = myFakeApi.getInstances().get("image1");
    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    final String userDataEncoded = getExtraConfigValue(extraConfig, VMWarePropertiesNames.USER_DATA);
    final CloudInstanceUserData cloudInstanceUserData = CloudInstanceUserData.deserialize(userDataEncoded);
    assertEquals(cloudInstanceUserData.getCustomAgentConfigurationParameters().get("customParam1"), "customValue1");
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
  public void tearDown(){

  }
}
