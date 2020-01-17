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

import jetbrains.buildServer.clouds.CanStartNewInstanceResult;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
public class DummyCloudImage extends AbstractCloudImage<DummyCloudInstance, DummyImageDetails> {
  public DummyCloudImage(final String name) {
    super(name, name);
  }

  @NotNull
  @Override
  public CanStartNewInstanceResult canStartNewInstanceWithDetails() {
    throw new UnsupportedOperationException("DummyCloudImage.canStartNewInstanceWithDetails");
  }

  @Override
  public void terminateInstance(@NotNull final DummyCloudInstance instance) {
    throw new UnsupportedOperationException("DummyCloudImage.terminateInstance");

    //
  }

  @Override
  public void restartInstance(@NotNull final DummyCloudInstance instance) {
    throw new UnsupportedOperationException("DummyCloudImage.restartInstance");

    //
  }

  @Override
  public DummyCloudInstance startNewInstance(@NotNull final CloudInstanceUserData tag) {
    throw new UnsupportedOperationException("DummyCloudImage.startNewInstance");

    //return null;
  }

  @Override
  public DummyImageDetails getImageDetails() {
    throw new UnsupportedOperationException("DummyCloudImage.getImageDetails");

    //return null;
  }

  @Override
  protected DummyCloudInstance createInstanceFromReal(final AbstractInstance realInstance) {
    return new DummyCloudInstance(this, realInstance.getName(), realInstance.getName());
  }

  @Nullable
  @Override
  public Integer getAgentPoolId() {
    return null;
  }
}
