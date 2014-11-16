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
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 10/16/2014
 *         Time: 5:37 PM
 */
public class VmwareCloudImageDetails implements CloudImageDetails {
  @SerializedName("sourceName")
  private final String mySourceName;
  @SerializedName("folder")
  private final String myFolderName;
  @SerializedName("pool")
  private final String myResourcePoolName;
  @SerializedName("snapshot")
  @NotNull
  private final String mySnapshotName;
  @SerializedName("behaviour")
  private final CloneBehaviour myCloneBehaviour;
  @SerializedName("maxInstances")
  private final int myMaxInstances;

  public VmwareCloudImageDetails(@NotNull final String sourceName,
                                 @NotNull final String snapshotName,
                                 @NotNull final String folderName,
                                 @NotNull final String resourcePoolName,
                                 @NotNull final CloneBehaviour cloneBehaviour,
                                 final int maxInstances) {
    mySourceName = sourceName;
    myFolderName = folderName;
    myResourcePoolName = resourcePoolName;
    mySnapshotName = StringUtil.isEmpty(snapshotName) ? null : snapshotName;
    myCloneBehaviour = cloneBehaviour;
    myMaxInstances = maxInstances;
  }

  public String getSourceName() {
    return mySourceName;
  }

  public String getFolderName() {
    return myFolderName;
  }

  public String getResourcePoolName() {
    return myResourcePoolName;
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
    return VmwareConstants.CURRENT_VERSION.equals(mySnapshotName);
  }
}
