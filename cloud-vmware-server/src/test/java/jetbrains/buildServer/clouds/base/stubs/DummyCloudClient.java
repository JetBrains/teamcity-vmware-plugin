package jetbrains.buildServer.clouds.base.stubs;

import java.util.Collection;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Sergey.Pak on 6/9/2016.
 */
public class DummyCloudClient implements CloudClientEx {
  @NotNull
  @Override
  public CloudInstance startNewInstance(@NotNull final CloudImage image, @NotNull final CloudInstanceUserData tag) throws QuotaException {
    throw new UnsupportedOperationException("DummyCloudClient.startNewInstance");

    //return null;
  }

  @Override
  public void restartInstance(@NotNull final CloudInstance instance) {
    throw new UnsupportedOperationException("DummyCloudClient.restartInstance");

    //
  }

  @Override
  public void terminateInstance(@NotNull final CloudInstance instance) {
    throw new UnsupportedOperationException("DummyCloudClient.terminateInstance");

    //
  }

  @Override
  public void dispose() {
    throw new UnsupportedOperationException("DummyCloudClient.dispose");

    //
  }

  @Override
  public boolean isInitialized() {
    throw new UnsupportedOperationException("DummyCloudClient.isInitialized");

    //return false;
  }

  @Nullable
  @Override
  public CloudImage findImageById(@NotNull final String imageId) throws CloudException {
    throw new UnsupportedOperationException("DummyCloudClient.findImageById");

    //return null;
  }

  @Nullable
  @Override
  public CloudInstance findInstanceByAgent(@NotNull final AgentDescription agent) {
    throw new UnsupportedOperationException("DummyCloudClient.findInstanceByAgent");

    //return null;
  }

  @NotNull
  @Override
  public Collection<? extends CloudImage> getImages() throws CloudException {
    throw new UnsupportedOperationException("DummyCloudClient.getImages");

    //return null;
  }

  @Nullable
  @Override
  public CloudErrorInfo getErrorInfo() {
    throw new UnsupportedOperationException("DummyCloudClient.getErrorInfo");

    //return null;
  }

  @Override
  public boolean canStartNewInstance(@NotNull final CloudImage image) {
    throw new UnsupportedOperationException("DummyCloudClient.canStartNewInstance");

    //return false;
  }

  @Nullable
  @Override
  public String generateAgentName(@NotNull final AgentDescription agent) {
    throw new UnsupportedOperationException("DummyCloudClient.generateAgentName");

    //return null;
  }
}
