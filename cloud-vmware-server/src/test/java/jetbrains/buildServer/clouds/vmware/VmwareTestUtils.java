

package jetbrains.buildServer.clouds.vmware;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.impl.profile.CloudClientParametersImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudImageParametersImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileUtil;
import org.jetbrains.annotations.NotNull;

public class VmwareTestUtils {

  protected static final String PROJECT_ID = "project123";
  protected static final String PROFILE_ID = "cp1";


  public static CloudProfile createProfileFromProps(CloudClientParameters params){
    return createProfileFromProps(PROJECT_ID, PROFILE_ID, params);
  }

  public static CloudProfile createProfileFromProps(String projectId, String profileId, CloudClientParameters params){
    return createProfileFromProps(projectId, profileId, params, "Vmware profile");
  }

  public static CloudProfile createProfileFromProps(String projectId, String profileId, CloudClientParameters params, String name){
    return new CloudProfileImpl(name, projectId, profileId, "Description", VmwareConstants.TYPE, null, true, params.getParameters(), params.getCloudImages());
  }

  @NotNull static CloudClientParameters getClientParameters(final String projectId, final String imagesJson) {
    return new CloudClientParametersImpl(Collections.emptyMap(), getImageParameters(projectId, imagesJson));
  }

  @NotNull
  static Collection<CloudImageParameters> getImageParameters(final String projectId, final String imagesJson) {
    return CloudProfileUtil.parseImagesData(imagesJson).stream().map(
      data -> new CloudImageParametersImpl(data, projectId, UUID.randomUUID().toString())).collect(Collectors.toList());
  }
}