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

import com.vmware.vim25.ManagedObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 2/6/2015
 *         Time: 4:43 PM
 */
public interface VmwareManagedEntity {
  @NotNull
  String getId();

  @NotNull
  String getName();

  /**
   *
   * @return full path
   */
  @Nullable
  String getPath();

  @Nullable
  String getDatacenterId();

  @NotNull
  ManagedObjectReference getMOR();

  @Nullable
  ManagedObjectReference getParentMOR();

  default String getUniqueName() {
    return String.format("%s(%s)", getName(), getId());
  }
}
