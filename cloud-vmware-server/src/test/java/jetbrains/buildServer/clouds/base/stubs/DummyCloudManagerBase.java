package jetbrains.buildServer.clouds.base.stubs;

import java.util.Collection;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.CloudType;
import jetbrains.buildServer.clouds.server.impl.CloudManagerBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Sergey.Pak on 6/9/2016.
 */
public class DummyCloudManagerBase implements CloudManagerBase {
  @Override
  public boolean isIntegrationEnabled() {
    throw new UnsupportedOperationException("DummyCloudManagerBase.isIntegrationEnabled");

    //return false;
  }

  @Override
  public void updateAllProfiles(final Collection<CloudProfile> cloudProfiles, final boolean enabled) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.updateAllProfiles");

    //
  }

  @Override
  public void updateProfile(final CloudProfile cloudProfile) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.updateProfile");

    //
  }

  @Override
  public void disposeClient(final CloudClientEx cloudClient) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.disposeClient");

    //
  }

  @NotNull
  @Override
  public Collection<CloudProfile> listProfiles() {
    throw new UnsupportedOperationException("DummyCloudManagerBase.listProfiles");

    //return null;
  }

  @NotNull
  @Override
  public Collection<? extends CloudType> getCloudTypes() {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getCloudTypes");

    //return null;
  }

  @Override
  public CloudType findCloudType(@Nullable final String cloudName) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.findCloudType");

    //return null;
  }

  @Nullable
  @Override
  public CloudProfile findProfileById(@NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.findProfileById");

    //return null;
  }

  @Nullable
  @Override
  public CloudClientEx getClientIfExists(@NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getClientIfExists");

    //return null;
  }

  @NotNull
  @Override
  public CloudClientEx getClient(@NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getClient");

    //return null;
  }

  @Override
  public void setIntegrationEnabled(final boolean isEnabled) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.setIntegrationEnabled");

    //
  }
}
