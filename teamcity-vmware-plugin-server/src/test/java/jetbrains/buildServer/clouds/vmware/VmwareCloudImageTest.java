package jetbrains.buildServer.clouds.vmware;

import com.intellij.util.containers.ConcurrentHashSet;
import java.io.File;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.stubs.FakeApiConnector;
import jetbrains.buildServer.util.FileUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 5/20/2014
 *         Time: 3:16 PM
 */
@Test
public class VmwareCloudImageTest extends BaseTestCase {

  private CloudAsyncTaskExecutor myStatusTask;
  private VMWareApiConnector myApiConnector;
  private VmwareCloudImage myImage;
  private VmwareCloudImageDetails myImageDetails;
  private File myIdxStorage;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myStatusTask = new CloudAsyncTaskExecutor("Test-vmware");
    myApiConnector = new FakeApiConnector();
    myIdxStorage = createTempDir();
    myImageDetails = new VmwareCloudImageDetails("imageNickname", "sourceName", "snapshotName"
      , "folderId", "rpId", CloneBehaviour.FRESH_CLONE, 5);

    myImage = new VmwareCloudImage(myApiConnector, myImageDetails, myStatusTask, myIdxStorage);
  }

  public void check_clone_name_generation(){
    for (int i=0; i<10; i++){
      assertEquals(String.format("%s.%d", myImageDetails.getNickname(), i + 1), myImage.generateNewVmName());
    }
    FileUtil.delete(myIdxStorage);
    final String newName = myImage.generateNewVmName();
    assertTrue(newName.startsWith(myImage.getName()));
    final int i = Integer.parseInt(newName.substring(myImage.getName().length() + 1));
    assertTrue(i > 100000);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    super.tearDown();
  }

}
