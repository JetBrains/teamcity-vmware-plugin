package jetbrains.buildServer.clouds.base.stubs;

import java.util.Collection;
import jetbrains.buildServer.clouds.CloudClientEx;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.CloudType;
import jetbrains.buildServer.clouds.server.CloudManagerBase;
import jetbrains.buildServer.clouds.server.ProjectCloudIntegrationStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Sergey.Pak on 6/9/2016.
 */
public class DummyCloudManagerBase implements CloudManagerBase {
  @Override
  public boolean isIntegrationEnabled(@NotNull final String projectId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getIntegrationStatus");

    //return false;
  }

  /**
   * Returns true if user can enable/disable configuration in the current project's tree
   *
   * @param projectId project internal id
   * @return see above
   */
  @Override
  public boolean isConfigurable(@NotNull final String projectId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.isConfigurable");

    //return false;
  }

  @Override
  public void updateProfile(final String projectId, final CloudProfile cloudProfile) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.updateProfile");

    //
  }

  @NotNull
  @Override
  public Collection<CloudProfile> listProfilesByProjectExtId(final String projectExtId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.listProfilesByProjectExtId");

    //return null;
  }

  @Override
  public Collection<CloudProfile> listAllProfiles() {
    throw new UnsupportedOperationException("DummyCloudManagerBase.listAllProfiles");

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
  public CloudProfile findProfileById(final String projectId, @NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.findProfileById");

    //return null;
  }

  @Nullable
  @Override
  public CloudProfile findProfileGloballyById(@NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.findProfileGloballyById");

    //return null;
  }

  @Nullable
  @Override
  public CloudClientEx getClientIfExists(final String projectId, @NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getClientIfExists");

    //return null;
  }

  @Override
  public CloudClientEx getClientIfExistsByProjectExtId(final String projectExtId, @NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getClientIfExistsByProjectExtId");

    //return null;
  }

  @NotNull
  @Override
  public CloudClientEx getClient(final String projectId, @NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getClient");

    //return null;
  }

  @Override
  public void updateStatus(final String projectId, @NotNull final ProjectCloudIntegrationStatus newStatus) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.updateStatus");


  }

  @Nullable
  @Override
  public ProjectCloudIntegrationStatus getProjectIntegrationStatus(final String projectId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getProjectIntegrationStatus");

    //return null;
  }
}
