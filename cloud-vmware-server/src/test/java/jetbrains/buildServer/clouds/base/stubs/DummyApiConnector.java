package jetbrains.buildServer.clouds.base.stubs;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
public class DummyApiConnector implements CloudApiConnector<DummyCloudImage, DummyCloudInstance> {

  private final Map<String, DummyRealInstance> myRealInstanceMap;
  private final Map<String, CountDownLatch> myLatchMap;

  public DummyApiConnector(final Map<String, DummyRealInstance> realInstanceMap){
    myRealInstanceMap = realInstanceMap;
    myLatchMap = new HashMap<>();
  }

  @Override
  public void test() throws CheckedCloudException {
    checkLatch("test");
  }

  @Nullable
  @Override
  public InstanceStatus getInstanceStatusIfExists(@NotNull final String instanceName) {
    checkLatch("getInstanceStatusIfExists");
    final DummyRealInstance instance = myRealInstanceMap.get(instanceName);
    return instance == null ? null : instance.getInstanceStatus();
  }

  @NotNull
  @Override
  public <R extends AbstractInstance> Map<DummyCloudImage, Map<String, R>> fetchInstances(@NotNull final Collection<DummyCloudImage> images) throws CheckedCloudException {
    Map<DummyCloudImage, Map<String, R>> result = new HashMap<>();
    for (DummyCloudImage image: images) {
      result.put(image, fetchInstances(image));
    }
    return result;
  }

  @NotNull
  @Override
  public <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final DummyCloudImage image) throws CheckedCloudException {
    checkLatch("listImageInstances");
    return myRealInstanceMap.entrySet().stream()
                            .filter(e->e.getValue().getDummyImageName().equals(image.getId()))
                            .collect(Collectors.toMap(e->e.getKey(), e->(R)e.getValue()));
  }

  @NotNull
  @Override
  public TypedCloudErrorInfo[] checkImage(@NotNull final DummyCloudImage image) {
    checkLatch("checkImage");
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  @Override
  public TypedCloudErrorInfo[] checkInstance(@NotNull final DummyCloudInstance instance) {
    checkLatch("checkInstance");
    return new TypedCloudErrorInfo[0];
  }

  private void checkLatch(String methodName){
    final CountDownLatch methodLatch = myLatchMap.get(methodName);
    if (methodLatch != null){
      try {
        //avoid waiting for too long in tests
        final boolean result = methodLatch.await(2, TimeUnit.SECONDS);
        if (!result){
          throw new RuntimeException("Timeout (2 sec) on waiting for latch '" + methodName+"'");
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public Map<String, CountDownLatch> getLatchMap() {
    return myLatchMap;
  }
}
