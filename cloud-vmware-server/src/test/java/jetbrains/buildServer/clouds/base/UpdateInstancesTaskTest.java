package jetbrains.buildServer.clouds.base;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.stubs.*;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.WaitFor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
@Test
public class UpdateInstancesTaskTest extends BaseTestCase {

  private UpdateInstancesTask myUpdateInstancesTask;
  private DummyApiConnector myApiConnector;
  private MyClient myClient;
  private Map<String, DummyRealInstance> myRealInstanceMap;

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRealInstanceMap = new HashMap<>();
    myApiConnector = new DummyApiConnector(myRealInstanceMap);
    myClient = new MyClient(myApiConnector);
    myUpdateInstancesTask = new UpdateInstancesTask(myApiConnector, myClient, 2*1000, true);
  }

  public void no_instances(){
    myClient.getImageMap().put("image1", new DummyCloudImage("image1"));
    myUpdateInstancesTask.run();
    assertEquals(0, myClient.getInstances().size());
  }

  public void catch_new_instances(){
    final String imageName = "image1";
    final String instanceName = "instance1";
    myClient.getImageMap().put(imageName, new DummyCloudImage(imageName));
    myRealInstanceMap.put(instanceName, new DummyRealInstance(instanceName, imageName, InstanceStatus.RUNNING));
    myUpdateInstancesTask.run();
    final DummyCloudInstance dummyCloudInstance = myClient.getInstances().get(instanceName);
    assertNotNull(dummyCloudInstance);
    assertEquals(imageName, dummyCloudInstance.getImageId());
    assertEquals(instanceName, dummyCloudInstance.getName());
  }

  public void detect_removed_instance(){
    final String imageName = "image1";
    final String instanceName = "instance1";
    myClient.getImageMap().put(imageName, new DummyCloudImage(imageName));
    myRealInstanceMap.put(instanceName, new DummyRealInstance(instanceName, imageName, InstanceStatus.RUNNING));
    myUpdateInstancesTask.run();
    assertEquals(1, myClient.getInstances().size());
    myRealInstanceMap.clear();
    myUpdateInstancesTask.run();
    assertEquals(0, myClient.getInstances().size());
  }

  public void handle_instances_started_after_list_image(){
    final String imageName = "image1";
    final String instanceName = "instance1";
    myClient.getImageMap().put(imageName, new DummyCloudImage(imageName));
    myRealInstanceMap.put(instanceName, new DummyRealInstance(instanceName, imageName, InstanceStatus.RUNNING));
    myUpdateInstancesTask.run();
    assertEquals(1, myClient.getInstances().size());
    myRealInstanceMap.clear();
    final CountDownLatch innerLatch = new CountDownLatch(1);
    myApiConnector.getLatchMap().put("listImageInstances", innerLatch);
    final AtomicBoolean complete = new AtomicBoolean(false);
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        myUpdateInstancesTask.run();
        complete.set(true);
      }
    });
    thread.start();
    myRealInstanceMap.put(instanceName, new DummyRealInstance(instanceName, imageName, InstanceStatus.RUNNING));
    innerLatch.countDown();
    new WaitFor(300){
      @Override
      protected boolean condition() {
        return complete.get();
      }
    };
    assertTrue(complete.get());
    final DummyCloudInstance dummyCloudInstance = myClient.getInstances().get(instanceName);
    assertNotNull(dummyCloudInstance);
    assertEquals(imageName, dummyCloudInstance.getImageId());
    assertEquals(instanceName, dummyCloudInstance.getName());
  }


  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private class MyClient extends AbstractCloudClient<DummyCloudInstance, DummyCloudImage, DummyImageDetails>{

    public MyClient(@NotNull final CloudApiConnector apiConnector) {
      super(new CloudClientParameters(), apiConnector);
    }

    @Override
    protected DummyCloudImage checkAndCreateImage(@NotNull final DummyImageDetails imageDetails) {
      return new DummyCloudImage(imageDetails.getSourceName());
    }

    @Override
    protected UpdateInstancesTask<DummyCloudInstance, DummyCloudImage, ?> createUpdateInstancesTask() {
      return null;
    }

    @Nullable
    @Override
    public DummyCloudInstance findInstanceByAgent(@NotNull final AgentDescription agent) {
      final String name = generateAgentName(agent);
      return getInstances().get(name);
    }

    @Nullable
    @Override
    public String generateAgentName(@NotNull final AgentDescription agent) {
      return agent.getAvailableParameters().get("name");
    }

    public Map<String, DummyCloudImage> getImageMap(){
      return myImageMap;
    }

    public Map<String, DummyCloudInstance> getInstances(){
      final Map<String, DummyCloudInstance> retval = new HashMap<>();
      myImageMap.forEach((n, i)->{
        retval.putAll(i.getInstances().stream().collect(Collectors.toMap(DummyCloudInstance::getName, Function.identity())));
      });
      return retval;
    }
  }
}
