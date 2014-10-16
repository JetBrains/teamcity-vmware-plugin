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

import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloudCloneType;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 10/16/2014
 *         Time: 5:37 PM
 */
public class VmwareCloudImageDetails extends AbstractCloudImageDetails {
  private final String myVmName;
  private final String myFolderName;
  private final String myResourcePoolName;
  private final String mySnapshotName;
  private final CloudCloneType myCloneType;
  private final int myMaxInstancesCount;

  public static VmwareCloudImageDetails fromString(@NotNull final String s){
    final String[] split = s.split(";");
    String vmName = split[0];
    String snapshotName = split[1];
    String cloneFolder = split[2];
    String resourcePool = split[3];
    String behaviourStr = split[4];
    String maxInstancesStr = split[5];

    return new VmwareCloudImageDetails(vmName, snapshotName, cloneFolder, resourcePool, CloudCloneType.valueOf(behaviourStr), Integer.parseInt(maxInstancesStr));
  }

  private VmwareCloudImageDetails(final String vmName,
                                  final String snapshotName,
                                  final String folderName,
                                 final String resourcePoolName,
                                 final CloudCloneType cloneType,
                                 final int maxInstancesCount) {
    myVmName = vmName;
    myFolderName = folderName;
    myResourcePoolName = resourcePoolName;
    mySnapshotName = StringUtil.isEmpty(snapshotName) ? null : snapshotName;
    myCloneType = cloneType;
    myMaxInstancesCount = maxInstancesCount;
  }

  public String getVmName() {
    return myVmName;
  }

  public String getFolderName() {
    return myFolderName;
  }

  public String getResourcePoolName() {
    return myResourcePoolName;
  }

  public String getSnapshotName() {
    return mySnapshotName;
  }

  public CloudCloneType getCloneType() {
    return myCloneType;
  }

  public int getMaxInstancesCount() {
    return myMaxInstancesCount;
  }
}
