/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware;

import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 10/16/2014
 *         Time: 5:37 PM
 */
public class VmwareCloudImageDetails implements CloudImageDetails {
  @Nullable private final String myNickname;
  @NotNull private final String mySourceVmName;
  private final String myFolderId;
  private final String myResourcePoolId;
  @NotNull private final String mySnapshotName;
  private final CloneBehaviour myCloneBehaviour;
  private final int myMaxInstances;
  private final String myCustomizationSpec;
  private final Integer myAgentPoolId;
  @NotNull private final String mySourceId;

  public VmwareCloudImageDetails(@NotNull final CloudImageParameters imageParameters){
    myCustomizationSpec = imageParameters.getParameter(VmwareConstants.CUSTOMIZATION_SPEC);
    myMaxInstances = StringUtil.parseInt(StringUtil.emptyIfNull(imageParameters.getParameter(VmwareConstants.MAX_INSTANCES)), 0);
    mySourceVmName = imageParameters.getParameter(VmwareConstants.SOURCE_VM_NAME);
    myFolderId = imageParameters.getParameter(VmwareConstants.FOLDER);
    myResourcePoolId = imageParameters.getParameter(VmwareConstants.RESOURCE_POOL);
    myCloneBehaviour = CloneBehaviour.valueOf(imageParameters.getParameter(VmwareConstants.BEHAVIOUR));
    mySnapshotName = StringUtil.emptyIfNull(imageParameters.getParameter(VmwareConstants.SNAPSHOT));
    myNickname = StringUtil.nullIfEmpty(imageParameters.getParameter(VmwareConstants.NICKNAME));
    myAgentPoolId = imageParameters.getAgentPoolId();
    if (myCloneBehaviour.isUseOriginal()){
      mySourceId = mySourceVmName;
    } else {
      mySourceId = myNickname == null ? mySourceVmName : myNickname;
    }
  }

  @NotNull
  public String getSourceId() {
    return mySourceId;
  }

  @NotNull
  public String getSourceVmName() {
    return mySourceVmName;
  }

  public String getFolderId() {
    return myFolderId;
  }

  public String getResourcePoolId() {
    return myResourcePoolId;
  }

  @NotNull
  public String getSnapshotName() {
    return mySnapshotName;
  }

  public CloneBehaviour getBehaviour() {
    return myCloneBehaviour;
  }

  public int getMaxInstances() {
    return myMaxInstances;
  }

  public String getCustomizationSpec() {
    return myCustomizationSpec;
  }

  public boolean useCurrentVersion(){
    return VmwareConstants.CURRENT_STATE.equals(mySnapshotName);
  }

  public Integer getAgentPoolId() {
    return myAgentPoolId;
  }
}
