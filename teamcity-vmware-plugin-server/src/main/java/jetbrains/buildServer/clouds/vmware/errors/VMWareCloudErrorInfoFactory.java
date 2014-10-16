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

package jetbrains.buildServer.clouds.vmware.errors;

import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/14/2014
 *         Time: 3:43 PM
 */
public class VMWareCloudErrorInfoFactory {

  public static CloudErrorInfo noSuchFolder(String folderName){
    return noSuchItem(folderName, Folder.class);
  }

  public static CloudErrorInfo noSuchVM(String vm){
    return noSuchItem(vm, VirtualMachine.class);
  }

  public static CloudErrorInfo noSuchResourcePool(String resourcePoolName){
    return noSuchItem(resourcePoolName, ResourcePool.class);
  }

  public static CloudErrorInfo noSuchSnapshot(String snapshotName, String vmName){
    return error("No such snapshot %s for VM %s", snapshotName, vmName);
  }

  public static CloudErrorInfo noSuchImages(@NotNull final List<String> images){
    return error("No such images: %s", Arrays.toString(images.toArray()));
  }

  private static CloudErrorInfo noSuchItem(String itemName, Class itemClass){
    return error("No such %s: %s", itemClass.getSimpleName(), itemName);
  }

  public static CloudErrorInfo error(String message, Object... args){
    return new CloudErrorInfo(String.format(message, args));
  }

  public static CloudErrorInfo error(String message, String detailedMessage, Throwable th){
    return new CloudErrorInfo(message, detailedMessage, th);
  }
}
