package jetbrains.buildServer.clouds.vmware;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileDataImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileImpl;

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
    final CloudProfileDataImpl data = new CloudProfileDataImpl(VmwareConstants.TYPE, name, "Description", null, true, params);
    return new CloudProfileImpl(projectId, profileId, data);
  }


}
