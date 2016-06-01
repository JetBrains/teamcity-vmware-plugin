package jetbrains.buildServer.clouds.base.stubs;

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

  @Override
  public boolean canStartNewInstance() {
    throw new UnsupportedOperationException("DummyCloudImage.canStartNewInstance");

    //return false;
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
