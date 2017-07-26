package jetbrains.buildServer.clouds.vmware;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileDataImpl;
import jetbrains.buildServer.clouds.server.impl.profile.CloudProfileImpl;

public class VmwareTestUtils {

  protected static final String PROJECT_ID = "project123";
  protected static final String PROFILE_ID = "cp1";


  public static CloudProfile createProfileFromProps(CloudClientParameters params){
    final CloudProfileDataImpl data = new CloudProfileDataImpl();
    data.setEnabled(true);
    data.setParameters(params);
    data.setName("Profile name");
    data.setDescription("Description");
    data.setCloudCode(VmwareConstants.TYPE);
    final CloudProfile profile = new CloudProfileImpl(PROJECT_ID, PROFILE_ID, data);
    return profile;
  }


}
