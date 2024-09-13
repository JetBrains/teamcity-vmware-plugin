

package jetbrains.buildServer.clouds.base.stubs;

import java.util.Collection;
import jetbrains.buildServer.clouds.*;
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

  @NotNull
  @Override
  public CloudProfile updateProfile(@NotNull final String projectId, @NotNull final String profileId, @NotNull final CloudProfileData cloudProfileData) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.updateProfile");
  }

  @NotNull
  @Override
  public Collection<CloudProfile> listProfilesByProject(final String projectId, final boolean includeFromSubprojects) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.listProfilesByProject");

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

  @Override
  public void disposeClient(@NotNull String projectId, @NotNull String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.disposeClient");

  }

  @Nullable
  @Override
  public ProjectCloudIntegrationStatus getProjectIntegrationStatus(final String projectId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.getProjectIntegrationStatus");

    //return null;
  }

  @NotNull
  @Override
  public CloudProfile createProfile(@NotNull final String projectId, @NotNull final CloudProfileData profileData) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.createProfile");
  }

  @Override
  public boolean removeProfile(@NotNull final String projectId, @NotNull final String profileId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.removeProfile");
  }

  @Override
  public void setProfileEnabled(@NotNull final String projectId, @NotNull final String profileId, final boolean enabled) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.setProfileEnabled");
  }

  @NotNull
  @Override
  public Collection<CloudImageParameters> listImagesByProject(@NotNull final String projectId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.listImagesByProject");
  }

  @NotNull
  @Override
  public CloudImageParameters createImage(@NotNull final String projectId, @NotNull final CloudImageData imageData) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.createImage");
  }

  @Override
  public void deleteImage(@NotNull String projectId, @NotNull String profileId, @NotNull String imageId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.deleteImage");
  }

  @Nullable
  @Override
  public CloudImageParameters findImageByInternalId(@NotNull final String projectId, @NotNull final String imageInternalId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.findImageByInternalId");
  }

  @Nullable
  @Override
  public CloudProfile findProfileByImageId(@NotNull String imageId) {
    throw new UnsupportedOperationException("DummyCloudManagerBase.findProfileByImageId");
  }
}