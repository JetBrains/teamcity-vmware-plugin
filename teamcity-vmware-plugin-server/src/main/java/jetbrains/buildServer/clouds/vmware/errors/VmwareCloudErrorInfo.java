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

import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.VmInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/4/2014
 *         Time: 3:31 PM
 */
public class VmwareCloudErrorInfo {

  private final Map<VMWareCloudErrorType, String> myErrorTypeSet;
  private CloudErrorInfo myErrorInfo = null;
  private final VmInfo myVmInfo;

  public VmwareCloudErrorInfo(@NotNull final VmInfo vmInfo) {
    this.myVmInfo = vmInfo;
    myErrorTypeSet = new HashMap<VMWareCloudErrorType, String>();
  }

  public void setErrorType(@NotNull final VMWareCloudErrorType errorType, @Nullable final String message){
    if (!myErrorTypeSet.containsKey(errorType)) {
      myErrorTypeSet.put(errorType, message);
      updateErrorInfo();
    }
  }

  public void clearErrorType(@NotNull final VMWareCloudErrorType errorType){
    if (myErrorTypeSet.containsKey(errorType)) {
      myErrorTypeSet.remove(errorType);
      updateErrorInfo();
    }
  }

  public void clearAllErrors(){
    myErrorTypeSet.clear();
    updateErrorInfo();
  }

  private void updateErrorInfo() {
    if (myErrorTypeSet.size() ==0 ){
      myErrorInfo = null;
      return;
    }
    Set<String> errorStrs = new HashSet<String>();
    for (VMWareCloudErrorType errorType : myErrorTypeSet.keySet()) {
      final String message = myErrorTypeSet.get(errorType);
      if (message == null) {
        errorStrs.add(errorType.getErrorMessage(myVmInfo));
      } else {
        errorStrs.add(message);
      }
    }
    myErrorInfo = new CloudErrorInfo(Arrays.toString(errorStrs.toArray()));
  }


  public CloudErrorInfo getErrorInfo(){
    return myErrorInfo;
  }

}
