/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.base;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.errors.VmwareErrorMessages;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.NamedThreadUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:49 PM
 */
public abstract class AbstractCloudClient<G extends AbstractCloudInstance<T>, T extends AbstractCloudImage<G,D>, D extends CloudImageDetails>
  implements CloudClientEx, UpdatableCloudErrorProvider {

  protected final Map<String, T> myImageMap;
  protected final UpdatableCloudErrorProvider myErrorProvider;
  protected final CloudAsyncTaskExecutor myAsyncTaskExecutor;
  @NotNull protected CloudApiConnector myApiConnector;
  protected final CloudClientParameters myParameters;
  private AtomicBoolean myIsInitialized = new AtomicBoolean(false);

  public AbstractCloudClient(@NotNull final CloudClientParameters params, @NotNull final CloudApiConnector apiConnector) {
    myParameters = params;
    myAsyncTaskExecutor = new CloudAsyncTaskExecutor(params.getProfileDescription());
    myImageMap = new HashMap<String, T>();
    myErrorProvider = new CloudErrorMap(VmwareErrorMessages.getInstance(), "Unable to initialize cloud client. See details");
    myApiConnector = apiConnector;
  }

  public boolean isInitialized() {
    return myIsInitialized.get();
  }


  public void dispose() {
    myAsyncTaskExecutor.dispose();
  }

  @NotNull
  public G startNewInstance(@NotNull final CloudImage baseImage, @NotNull final CloudInstanceUserData tag) throws QuotaException {
    final T image = (T)baseImage;
    return image.startNewInstance(tag);
  }

  public void restartInstance(@NotNull final CloudInstance baseInstance) {
    final G instance = (G)baseInstance;
    instance.getImage().restartInstance(instance);
  }

  public void terminateInstance(@NotNull final CloudInstance baseInstance) {
    final G instance = (G)baseInstance;
    instance.getImage().terminateInstance(instance);
  }

  public boolean canStartNewInstance(@NotNull final CloudImage baseImage) {
    final T image = (T)baseImage;
    return image.canStartNewInstance();
  }

  public Future<?> populateImagesDataAsync(@NotNull final Collection<D> imageDetails){
    return myAsyncTaskExecutor.submit("Populate images data", new Runnable() {
      public void run() {
        try {
          populateImagesData(imageDetails, 60, 60);
        } finally {
          myIsInitialized.set(true);
        }
      }
    });
  }

  protected void populateImagesData(@NotNull final Collection<D> imageDetails, long initialDelaySec, long delaySec){
    for (D details : imageDetails) {
      T image = checkAndCreateImage(details);
      myImageMap.put(image.getName(), image);
    }
    final UpdateInstancesTask<G, T, ?> updateInstancesTask = createUpdateInstancesTask();
    updateInstancesTask.run();
    myAsyncTaskExecutor.scheduleWithFixedDelay("Update instances", updateInstancesTask, initialDelaySec, delaySec, TimeUnit.SECONDS);
  }

  protected abstract T checkAndCreateImage(@NotNull final D imageDetails);

  protected abstract UpdateInstancesTask<G,T,?> createUpdateInstancesTask();

  @Nullable
  public abstract G findInstanceByAgent(@NotNull final AgentDescription agent);

  @Nullable
  public T findImageById(@NotNull final String imageId) throws CloudException {
    return myImageMap.get(imageId);
  }

  @NotNull
  public Collection<T> getImages() throws CloudException {
    return Collections.unmodifiableCollection(myImageMap.values());
  }

  public void updateErrors(final TypedCloudErrorInfo... errors) {
    myErrorProvider.updateErrors(errors);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }
}
