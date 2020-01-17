/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public class VmwareSourceState {

  @Nullable
  private final String mySnapshotName;

  @Nullable
  private final String mySourceVmId;

  private VmwareSourceState(@Nullable final String snapshotName, @Nullable final String sourceVmId){
    mySnapshotName = snapshotName;
    mySourceVmId = sourceVmId;
  }

  public static VmwareSourceState from(@Nullable final String snapshot, @Nullable final String sourceVmId){
    return new VmwareSourceState(snapshot, sourceVmId);
  }

  @Nullable
  public String getDiffMessage(final VmwareSourceState vmState) {
    if (!Objects.equals(vmState.mySnapshotName, this.mySnapshotName)) {
      return String.format("Snapshot is outdated. VM: '%s' vs Actual: '%s'", mySnapshotName, vmState.mySnapshotName);
    } else if (!Objects.equals(vmState.mySourceVmId, this.mySourceVmId) && mySourceVmId != null && vmState.mySourceVmId != null) {
      return String.format("Source VM id is outdated. VM: '%s' vs Actual: '%s'", mySourceVmId, vmState.mySourceVmId);
    }
    return null;
  }

  @Nullable
  public String getSnapshotName() {
    return mySnapshotName;
  }

  @Nullable
  public String getSourceVmId() {
    return mySourceVmId;
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof VmwareSourceState))
      return false;
    final VmwareSourceState state = (VmwareSourceState)obj;

    if (!Objects.equals(state.mySnapshotName, this.mySnapshotName))
      return false;

    if (!Objects.equals(state.mySourceVmId, this.mySourceVmId) && mySourceVmId != null && state.mySourceVmId != null)
      return false;

    return true;
  }

  @Override
  public String toString() {
    return "VmwareSourceState{" +
           "mySnapshotName='" + mySnapshotName + '\'' +
           ", mySourceVmId='" + mySourceVmId + '\'' +
           '}';
  }
}
