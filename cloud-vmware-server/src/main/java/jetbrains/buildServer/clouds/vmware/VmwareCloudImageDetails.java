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

import com.google.gson.annotations.SerializedName;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.vmware.types.CloneBehaviour;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 10/16/2014
 *         Time: 5:37 PM
 */
public class VmwareCloudImageDetails {
  @SerializedName("nickname")
  @Nullable
  private final String myNickname;

  @SerializedName("sourceName")
  private final String mySourceName;

  @SerializedName("folder")
  private final String myFolderId;

  @SerializedName("pool")
  private final String myResourcePoolId;

  @SerializedName("snapshot")
  @NotNull
  private final String mySnapshotName;

  @SerializedName("behaviour")
  private final CloneBehaviour myCloneBehaviour;

  @SerializedName("maxInstances")
  private final int myMaxInstances;

  public VmwareCloudImageDetails(CloudImageParameters imageParameters){
    myMaxInstances = StringUtil.parseInt(StringUtil.emptyIfNull(imageParameters.getParameter("maxInstances")), 0);
    myCloneBehaviour = CloneBehaviour.valueOf(imageParameters.getParameter("behaviour"));
    myResourcePoolId = imageParameters.getParameter("pool");
    myFolderId = imageParameters.getParameter("folder");
    mySnapshotName = StringUtil.emptyIfNull(imageParameters.getParameter("snapshotName"));
    mySourceName = imageParameters.getParameter("sourceName");
    myNickname = imageParameters.getParameter("nickname");
  }

  protected VmwareCloudImageDetails(
    @Nullable final String nickname,
    @NotNull final String sourceName,
    @NotNull final String snapshotName,
    @NotNull final String folderId,
    @NotNull final String resourcePoolId,
    @NotNull final CloneBehaviour cloneBehaviour,
    final int maxInstances
  ) {
    myNickname = nickname;
    mySourceName = sourceName;
    myFolderId = folderId;
    myResourcePoolId = resourcePoolId;
    mySnapshotName = snapshotName;
    myCloneBehaviour = cloneBehaviour;
    myMaxInstances = maxInstances;
  }

  @NotNull
  public String getNickname() {
    return myNickname == null ? mySourceName : myNickname;
  }

  public String getSourceName() {
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

  public boolean useCurrentVersion(){
    return VmwareConstants.CURRENT_STATE.equals(mySnapshotName);
  }
}
