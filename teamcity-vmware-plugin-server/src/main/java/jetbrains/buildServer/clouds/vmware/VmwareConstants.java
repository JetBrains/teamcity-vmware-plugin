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

import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 4/16/2014
 *         Time: 6:33 PM
 */
public class VmwareConstants {
  @NotNull public static final String TYPE = "vmw";
  @NotNull public static final String SHOW_PRESERVE_CLONE = "teamcity.clouds.vmware.show.preserve.clone"; // false by default
  @NotNull public static final String USE_LINKED_CLONE = "teamcity.clouds.vmware.use.linked.clone"; // true by default
  @NotNull public static final String ENABLE_LATEST_SNAPSHOT = "teamcity.clouds.vmware.enable.latest.snapshot"; // false by default
  @NotNull public static final String LATEST_SNAPSHOT = "*"; // true by default
  @NotNull public static final String CURRENT_STATE = "__CURRENT_STATE__";

  @NotNull
  public String getShowPreserveClone() {
    return SHOW_PRESERVE_CLONE;
  }
}
