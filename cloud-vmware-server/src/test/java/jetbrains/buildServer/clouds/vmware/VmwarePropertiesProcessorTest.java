package jetbrains.buildServer.clouds.vmware;

import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.stubs.DummyCloudManagerBase;
import jetbrains.buildServer.clouds.server.CloudManagerBase;
import jetbrains.buildServer.clouds.server.impl.profile.CloudClientParametersImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileImpl;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.clouds.vmware.VmwareCloudIntegrationTest.TEST_SERVER_UUID;

/**
 * Created by Sergey.Pak on 6/9/2016.
 */
@Test
public class VmwarePropertiesProcessorTest extends BaseTestCase {
  protected static final String PROFILE_ID = "cp1";

  private VmwarePropertiesProcessor myProcessor;
  private Map<String, String> myProperties;
  private Collection<CloudProfile> myProfiles;
  private Map<String, CloudClientEx> myClients;
  private static final String PROJECT_ID = "project4";

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CloudManagerBase cloudManager = new DummyCloudManagerBase(){
      @NotNull
      @Override
      public Collection<CloudProfile> listProfilesByProject(final String projectId, final boolean includeFromSubprojects) {
        return myProfiles;
      }

      @Override
      public Collection<CloudProfile> listAllProfiles() {
        return myProfiles;
      }

      @NotNull
      @Override
      public CloudClientEx getClient(final String projectId, @NotNull final String profileId) {
        return myClients.get(profileId);
      }
    };
    myProcessor = new VmwarePropertiesProcessor(cloudManager);
    myProfiles = new ArrayList<>();
    myClients = new HashMap<>();

    myProperties = new HashMap<>();
    myProperties.put(VMWareWebConstants.USERNAME, "un");
    myProperties.put(VMWareWebConstants.SECURE_PASSWORD, "pw");
    myProperties.put(VMWareWebConstants.SERVER_URL, "http://localhost:9099");
  }

  public void check_base_properties(){
    check_no_errors();
    myProperties.remove(VMWareWebConstants.USERNAME);
    expect_error(VMWareWebConstants.USERNAME, "Value should be set");
    myProperties.put(VMWareWebConstants.USERNAME, "un");
    myProperties.remove(VMWareWebConstants.SECURE_PASSWORD);
    expect_error(VMWareWebConstants.SECURE_PASSWORD, "Value should be set");
    myProperties.put(VMWareWebConstants.SECURE_PASSWORD, "pw");
    myProperties.remove(VMWareWebConstants.SERVER_URL);
    expect_error(VMWareWebConstants.SERVER_URL, "Value should be set");

  }

  public void check_profile_instance_limit(){
    myProperties.put(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "2");
    check_no_errors();
    myProperties.put(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "0");
    expect_error("vmware_profile_instance_limit", "Must be a positive integer or empty");

    myProperties.put(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "aaaa");
    expect_error("vmware_profile_instance_limit", "Must be a positive integer or empty");
  }

  public void check_same_source_ids_new_profile() throws IOException {
    final CloudClientParameters cloudClientParameters = new CloudClientParametersImpl(
      Collections.singletonMap(VMWareWebConstants.SERVER_URL, "http://localhost:8080"),
      VmwareTestUtils.getImageParameters(PROJECT_ID,
        "[{sourceVmName:'image1', behaviour:'START_STOP'}," +
        "{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
        "customizationSpec:'someCustomization'}," +
        "{sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
        "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"));
    final String profileId = "vmw-1";
    myProfiles.add(VmwareTestUtils.createProfileFromProps(PROJECT_ID, profileId, cloudClientParameters));
    myProperties.put(VMWareWebConstants.SERVER_URL, "http://localhost:8080");
    myProperties.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                     "[{'source-id':'image2',sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                                                              "customizationSpec:'someCustomization'}]");

    myClients.put(profileId, createClient(cloudClientParameters));

    expect_error("source_images_json", "The cloud profile 'Vmware profile' already contains an image named 'IMAGE2'. Select a different VM or change the custom name.");
  }

  public void check_same_source_ids_edit_profile() throws IOException {
    {
      final CloudClientParameters cloudClientParameters = new CloudClientParametersImpl(
        Collections.singletonMap(VMWareWebConstants.SERVER_URL, "http://localhost:8080"),
        VmwareTestUtils.getImageParameters(PROJECT_ID,
          "[{'source-id':'image1',sourceVmName:'image1', behaviour:'START_STOP'}," +
          "{'source-id':'image2',sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
          "customizationSpec:'someCustomization'}," +
          "{'source-id':'image_template',sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
          "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"));
      final String profileId = "vmw-1";
      myProfiles.add(VmwareTestUtils.createProfileFromProps(PROJECT_ID, profileId, cloudClientParameters));

      myClients.put(profileId, createClient(cloudClientParameters));
    }
    {
      final String profileId = "vmw-2";
      final CloudProfile profile = new CloudProfileImpl("profile name 2", PROJECT_ID, profileId, "Description", VmwareConstants.TYPE, null, true,
                                                                Collections.singletonMap(VMWareWebConstants.SERVER_URL, "http://localhost:8080"),
                                                                VmwareTestUtils.getImageParameters(PROJECT_ID,
                                                                                                   "[{'source-id':'image3',sourceVmName:'image3', behaviour:'START_STOP'}," +
                                                                                                   "{'source-id':'image4',sourceVmName:'image4',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                                                                                                   "customizationSpec:'someCustomization'}," +
                                                                                                   "{'source-id':'image_template2',sourceVmName:'image_template2', snapshot:'" +
                                                                                                   VmwareConstants.CURRENT_STATE +
                                                                                                   "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"));
      myProfiles.add(profile);

      myClients.put(profileId, createClient(profile.getParameters()));
    }

    myProperties.put(CloudConstants.PROFILE_ID, "vmw-2");
    myProperties.put(VMWareWebConstants.SERVER_URL, "http://localhost:8080");
    myProperties.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                     "[{'source-id':'image2',sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                                                              "customizationSpec:'someCustomization'}]");

    expect_error("source_images_json", "The cloud profile 'Vmware profile' already contains an image named 'IMAGE2'. Select a different VM or change the custom name.");

    myProperties.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                     "[{'source-id':'image3',sourceVmName:'image2',nickname:'image3',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                     "customizationSpec:'someCustomization'}]");

    check_no_errors();
  }

  public void check_same_source_same_server_url() throws IOException {
    {
      final CloudClientParameters cloudClientParameters = new CloudClientParametersImpl(
        Collections.singletonMap(VMWareWebConstants.SERVER_URL, "http://localhost:8081"),
        VmwareTestUtils.getImageParameters(PROJECT_ID,
          "[{'source-id':'image1',sourceVmName:'image1', behaviour:'START_STOP'}," +
          "{'source-id':'image2',sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
          "customizationSpec:'someCustomization'}," +
          "{'source-id':'image_template',sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
          "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"));
      final String profileId = "vmw-1";
      myProfiles.add(VmwareTestUtils.createProfileFromProps(PROJECT_ID, profileId, cloudClientParameters));

      myClients.put(profileId, createClient(cloudClientParameters));
    }
    {
      final String profileId = "vmw-2";
      final CloudProfile profile = new CloudProfileImpl("profile name 2", PROJECT_ID, profileId, "Description", VmwareConstants.TYPE, null, true, Collections.singletonMap(VMWareWebConstants.SERVER_URL, "http://localhost:8082"), VmwareTestUtils.getImageParameters(PROJECT_ID,
                                                                                                                                                                                                                                                   "[{'source-id':'image3',sourceVmName:'image3', behaviour:'START_STOP'}," +
                                                                                                                                                                                                                                                   "{'source-id':'image4',sourceVmName:'image4',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                                                                                                                                                                                                                                                   "customizationSpec:'someCustomization'}," +
                                                                                                                                                                                                                                                   "{'source-id':'image_template2',sourceVmName:'image_template2', snapshot:'" + VmwareConstants.CURRENT_STATE +
                                                                                                                                                                                                                                                   "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"));
      myProfiles.add(profile);

      myClients.put(profileId, createClient(profile.getParameters()));
    }
    myProperties.put(VMWareWebConstants.SERVER_URL, "http://localhost:8081");
    myProperties.put(CloudImageParameters.SOURCE_IMAGES_JSON,
                     "[{'source-id':'image2',sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp',maxInstances:3,behaviour:'ON_DEMAND_CLONE', " +
                     "customizationSpec:'someCustomization'}]");

    expect_error("source_images_json", "The cloud profile 'Vmware profile' already contains an image named 'IMAGE2'. Select a different VM or change the custom name.");

    myProperties.put(VMWareWebConstants.SERVER_URL, "http://localhost:8082");

    check_no_errors();

  }

  private void expect_error(String fieldName, String reasonText){
    final Collection<InvalidProperty> process = myProcessor.process(myProperties);
    if (process.size() == 0){
      fail("Expected 1 InvalidProperty: " + fieldName  + " - " + reasonText);
    }
    StringBuilder errors = new StringBuilder();
    process.stream()
           .filter(i-> !(reasonText.equals(i.getInvalidReason()) && i.getPropertyName().equals(fieldName)))
           .map(InvalidProperty::toString)
           .forEachOrdered(p -> {
      errors.append(p).append("\n");
    });
    if (errors.length() > 0)
      fail("Unexpected failures: \n" + errors.toString());
  }

  private void check_no_errors(){
    final Collection<InvalidProperty> process = myProcessor.process(myProperties);
    if (process.size() != 0){
      StringBuilder errors = new StringBuilder("Unexpected failures:\n");
      process.stream().map(InvalidProperty::toString).forEachOrdered(p->{
        errors.append(p).append("\n");
      });
      fail(errors.toString());
    }
  }

  private CloudClientEx createClient(CloudClientParameters clientParameters) throws IOException {
    final Collection<VmwareCloudImageDetails> images = VMWareCloudClientFactory.parseImageDataInternal(clientParameters);
    FakeApiConnector apiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID);
    VMWareCloudClient client = new VMWareCloudClient(VmwareTestUtils.createProfileFromProps(clientParameters),
                                                     apiConnector,
                                                     new VmwareUpdateTaskManager(),
                                                     createTempDir());
    client.populateImagesData(images, 1000, 1000);
    return client;
  }



  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }


}
