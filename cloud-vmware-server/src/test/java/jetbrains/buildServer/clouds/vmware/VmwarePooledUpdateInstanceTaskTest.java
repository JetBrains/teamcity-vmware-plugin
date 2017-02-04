package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.util.Pair;
import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.ManagedEntity;
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
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeModel;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateInstanceTask;
import jetbrains.buildServer.clouds.vmware.tasks.VmwarePooledUpdateInstanceTask;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import org.jetbrains.annotations.NotNull;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by sergeypak on 27/10/2016.
 */
@Test
public class VmwarePooledUpdateInstanceTaskTest extends BaseTestCase {

  protected static final String PROFILE_ID = "cp1";
  protected static final String TEST_SERVER_UUID = "1234-5678-9012";
  private File myIdxStorage;
  private FakeApiConnector myFakeApiConnector;
  private VmwareUpdateTaskManager myTaskManager;


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
    myTaskManager = new VmwareUpdateTaskManager();
  }

  public void check_called_once() throws MalformedURLException {
    if (true)
      throw new SkipException("In progress");
    final AtomicBoolean listAllCanBeCalled = new AtomicBoolean();
    final AtomicBoolean listAllCalledOnce = new AtomicBoolean();

    final AtomicBoolean getByNameCanBeCalled = new AtomicBoolean();
    final AtomicBoolean getByNameCalledOnce = new AtomicBoolean();

    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID, null) {
      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        if (!listAllCanBeCalled.get()) {
          fail("Shouldn't be called");
        }
        assertFalse(listAllCalledOnce.get());
        listAllCalledOnce.set(true);
        return super.findAllEntitiesOld(instanceType);
      }

      @NotNull
      @Override
      protected <T extends ManagedEntity> Pair<T,Datacenter> findEntityByIdNameOld(final String idName, final Class<T> instanceType) throws VmwareCheckedCloudException {
        if (!getByNameCanBeCalled.get()) {
          fail("Shouldn't be called");
        }
        assertFalse(getByNameCalledOnce.get());
        getByNameCalledOnce.set(true);
        return super.findEntityByIdNameOld(idName, instanceType);
      }
    };

    final CloudClientParameters clientParameters1 = new CloudClientParameters();
    clientParameters1.setCloudImages(CloudImageParameters.collectionFromJson("[{sourceVmName:'image1', behaviour:'START_STOP'}]"));
    final VMWareCloudClient client1 = new MyClient(clientParameters1, null);


    final CloudClientParameters clientParameters2 = new CloudClientParameters();
    clientParameters2.setCloudImages(CloudImageParameters.collectionFromJson(
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));
    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);

    final CloudClientParameters clientParameters3 = new CloudClientParameters();
    clientParameters3.setCloudImages(CloudImageParameters.collectionFromJson(
      "[{'source-id':'image_template',sourceVmName:'image_template', snapshot:'" + VmwareConstants.CURRENT_STATE +
      "',folder:'cf',pool:'rp',maxInstances:3,behaviour:'FRESH_CLONE', customizationSpec: 'linux'}]"
    ));
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
    final CloudClientParameters clientParameters1 = new CloudClientParameters();
    clientParameters1.setCloudImages(CloudImageParameters.collectionFromJson("[{sourceVmName:'image1', behaviour:'START_STOP'}]"));
    final VMWareCloudClient client1 = new MyClient(clientParameters1, null);


    final CloudClientParameters clientParameters2 = new CloudClientParameters();
    clientParameters2.setCloudImages(CloudImageParameters.collectionFromJson(
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);

    final AtomicInteger expectedImagesCount = new AtomicInteger();
    final AtomicBoolean hasRun = new AtomicBoolean(false);
    final AtomicReference<Collection<VmwareCloudImage>> images2process = new AtomicReference<>();

    myTaskManager = new VmwareUpdateTaskManager(){
      @Override
      protected VmwarePooledUpdateInstanceTask createNewPooledTask(@NotNull final VMWareApiConnector connector, @NotNull final VMWareCloudClient client) {
        return new VmwarePooledUpdateInstanceTask(connector, client, this){
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
    final AtomicBoolean actuallCalled = new AtomicBoolean();

    myFakeApiConnector = new FakeApiConnector(TEST_SERVER_UUID, PROFILE_ID, null) {
      @Override
      protected <T extends ManagedEntity> Collection<T> findAllEntitiesOld(final Class<T> instanceType) throws VmwareCheckedCloudException {
        processChecks();
        return Collections.emptyList();
      }

      private void processChecks() {
        if (!canBeCalled.get()) {
          fail("Shouldn't be called");
        }
        assertFalse(actuallCalled.get());
        actuallCalled.set(true);
      }

      @Override
      protected <T extends ManagedEntity> T findEntityByIdNameNullableOld(final String name, final Class<T> instanceType, final Datacenter dc) throws VmwareCheckedCloudException {
        processChecks();
        return null;
      }
    };

    final CloudClientParameters clientParameters1 = new CloudClientParameters();
    clientParameters1.setCloudImages(CloudImageParameters.collectionFromJson("[{sourceVmName:'image1', behaviour:'START_STOP'}]"));
    final VMWareCloudClient client1 = new MyClient(clientParameters1, null);


    final CloudClientParameters clientParameters2 = new CloudClientParameters();
    clientParameters2.setCloudImages(CloudImageParameters.collectionFromJson(
      "[{sourceVmName:'image2',snapshot:'snap*',folder:'cf',pool:'rp'," +
      "maxInstances:3,behaviour:'ON_DEMAND_CLONE',customizationSpec:'someCustomization'}]"));

    final VMWareCloudClient client2 = new MyClient(clientParameters2, null);
    final VmwareUpdateInstanceTask task1 = myTaskManager.createUpdateTask(myFakeApiConnector, client1);


    canBeCalled.set(true);
    actuallCalled.set(false);
    task1.run();
    assertTrue(actuallCalled.get());

    client1.dispose();
    final VmwareUpdateInstanceTask task2 = myTaskManager.createUpdateTask(myFakeApiConnector, client2);
    canBeCalled.set(true);
    actuallCalled.set(false);
    task2.run();
    assertTrue(actuallCalled.get());


  }


  private class MyClient extends VMWareCloudClient{

    private final Map<VMWareCloudClient, Boolean> myGetImagesCalled;

    public MyClient(@NotNull final CloudClientParameters cloudClientParameters,
                    Map<VMWareCloudClient, Boolean> getImagesCalled) {
      super(cloudClientParameters, myFakeApiConnector, myTaskManager, myIdxStorage);
      myGetImagesCalled = getImagesCalled;
      populateImagesData(VMWareCloudClientFactory.parseImageDataInternal(cloudClientParameters));
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


  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    System.getProperties().remove("teamcity.vsphere.instance.status.update.delay.ms");
    FakeModel.instance().clear();
    super.tearDown();
  }
}
