/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
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
  private final String mySourceName;
  private final String myFolderId;
  private final String myResourcePoolId;
  @NotNull private final String mySnapshotName;
  private final CloneBehaviour myCloneBehaviour;
  private final int myMaxInstances;
  private final String myCustomizationSpec;
  private final Integer myAgentPoolId;

  public VmwareCloudImageDetails(@NotNull final CloudImageParameters imageParameters){
    myCustomizationSpec = imageParameters.getParameter("customizationSpec");
    myMaxInstances = StringUtil.parseInt(StringUtil.emptyIfNull(imageParameters.getParameter("maxInstances")), 0);
    mySourceName = imageParameters.getId();
    myFolderId = imageParameters.getParameter("folder");
    myResourcePoolId = imageParameters.getParameter("pool");
    myCloneBehaviour = CloneBehaviour.valueOf(imageParameters.getParameter("behaviour"));
    mySnapshotName = StringUtil.emptyIfNull(imageParameters.getParameter("snapshot"));
    myNickname = StringUtil.nullIfEmpty(imageParameters.getParameter("nickname"));
    myAgentPoolId = imageParameters.getAgentPoolId();
  }


  @NotNull
  public String getNickname() {
    return myNickname == null ? mySourceName : myNickname;
  }

  public String getSourceId() {
    return mySourceName;
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
