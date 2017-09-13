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
  @NotNull public static final String USE_LINKED_CLONE = "teamcity.clouds.vmware.use.linked.clone"; // true by default
  @NotNull public static final String DISABLE_OS_CUSTOMIZATION = "teamcity.clouds.vmware.disable.os.customization"; // false by default
  @NotNull public static final String CONSIDER_STOPPED_VMS_LIMIT = "teamcity.clouds.vmware.consider.stopped.vms.limit"; // true by default
  @NotNull public static final String LATEST_SNAPSHOT = "*"; // true by default
  @NotNull public static final String CURRENT_STATE = "__CURRENT_STATE__";
  @NotNull public static final String DEFAULT_RESOURCE_POOL = "__DEFAULT_RESOURCE_POOL__";

  public static final String CUSTOMIZATION_SPEC = "customizationSpec";
  public static final String MAX_INSTANCES = "maxInstances";
  public static final String FOLDER = "folder";
  public static final String RESOURCE_POOL = "pool";
  public static final String BEHAVIOUR = "behaviour";
  public static final String SNAPSHOT = "snapshot";
  public static final String NICKNAME = "nickname";
  public static final String SOURCE_VM_NAME = "sourceVmName";

}
