/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.base.stubs;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

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

  /**
   * A special key of the cloud connector. Used to determine whether this cloud connector can be used in several cloud profiles.
   * <br/>
   * <p>
   * It is supposed to represent the same username and server url/region/instance
   *
   * @return see above.
   */
  @NotNull
  @Override
  public String getKey() {
    return "dummy_connector";
  }

  @NotNull
  @Override
  public <R extends AbstractInstance> Map<DummyCloudImage, Map<String, R>> fetchInstances(@NotNull final Collection<DummyCloudImage> images) throws CheckedCloudException {
    Map<DummyCloudImage, Map<String, R>> result = new HashMap<>();
    for (DummyCloudImage image: images) {
      if (fetchInstances(image).size() > 0)
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
  public Map<DummyCloudImage, TypedCloudErrorInfo[]> checkImages(@NotNull final Collection<DummyCloudImage> images) {
    checkLatch("checkImages");
    return images.stream().collect(Collectors.toMap(Function.identity(), img->new TypedCloudErrorInfo[0]));
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
