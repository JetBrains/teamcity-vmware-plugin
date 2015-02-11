/*
 *
 *  * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.ManagedEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 2/5/2015
 *         Time: 7:56 PM
 */
public class VmwareManagedEntityImpl implements VmwareManagedEntity {

  private final String myName;
  private final String myId;
  private final String myDatacenterId;
  private final String myDatacenterName;

  public VmwareManagedEntityImpl(@NotNull final ManagedEntity entity) {
    myName = entity.getName();
    myId = entity.getMOR().getVal();
    final Datacenter datacenter = VmwareUtils.getDatacenter(entity);
    if (datacenter != null) {
      myDatacenterName = datacenter.getName();
      myDatacenterId = datacenter.getMOR().getVal();
    } else {
      myDatacenterName = null;
      myDatacenterId = null;
    }
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getDatacenterName() {
    return myDatacenterName;
  }

  @Nullable
  public String getDatacenterId() {
    return myDatacenterId;
  }

}
