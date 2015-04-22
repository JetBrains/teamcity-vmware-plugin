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
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 2/6/2015
 *         Time: 5:37 PM
 */
public class VmwareUtils {

  @Nullable
  static Datacenter getDatacenter(ManagedEntity entity){
    try {
      while (entity != null) {
        entity = entity.getParent();
        if (entity != null && entity instanceof Datacenter) {
          return (Datacenter)entity;
        }
      }
    } catch (Exception ex){}
    return null;
  }
}
