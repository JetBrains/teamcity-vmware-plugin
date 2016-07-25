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

package jetbrains.buildServer.clouds.base.errors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 4:51 PM
 */
public class CloudErrorMap implements UpdatableCloudErrorProvider {
  protected final Map<String, TypedCloudErrorInfo> myErrors = new HashMap<String, TypedCloudErrorInfo>();
  private final ErrorMessageUpdater myMessageUpdater;
  private final AtomicReference<CloudErrorInfo> myErrorInfo = new AtomicReference<>();

  public CloudErrorMap(final ErrorMessageUpdater messageUpdater) {
    myMessageUpdater = messageUpdater;
  }

  public void updateErrors(@Nullable final TypedCloudErrorInfo... errors){
    if (errors != null) {
      final Map<String, TypedCloudErrorInfo> errorInfoMap = mapFromArray(errors);
      myErrors.clear();
      myErrors.putAll(errorInfoMap);
      if (myErrors.size() == 1) {
        final TypedCloudErrorInfo err = myErrors.values().iterator().next();
        final String message = err.getMessage();
        final String friendlyErrorMessage = myMessageUpdater.getFriendlyErrorMessage(message);
        final String details;
        if (!friendlyErrorMessage.equals(message)){
          details = message + "\n" + err.getDetails();
        } else {
          details = err.getDetails();
        }
        if (err.getThrowable() != null) {
          myErrorInfo.set(new CloudErrorInfo(friendlyErrorMessage, details, err.getThrowable()));
        } else {
          myErrorInfo.set(new CloudErrorInfo(friendlyErrorMessage, details));
        }
      } else {
        final StringBuilder msgBuilder = new StringBuilder();
        final StringBuilder detailsBuilder = new StringBuilder();
        for (TypedCloudErrorInfo errorInfo : myErrors.values()) {
          msgBuilder.append(",").append(myMessageUpdater.getFriendlyErrorMessage(errorInfo.getMessage()));
          detailsBuilder.append(",\n[").append(errorInfo.getDetails()).append("]");
        }
        myErrorInfo.set(new CloudErrorInfo(msgBuilder.substring(1), detailsBuilder.substring(2)));
      }
    } else {
      myErrorInfo.set(null);
    }

  }

  private static Map<String, TypedCloudErrorInfo> mapFromArray(final TypedCloudErrorInfo[] array){
    final Map<String, TypedCloudErrorInfo> map = new HashMap<String, TypedCloudErrorInfo>();
    for (TypedCloudErrorInfo errorInfo : array) {
      map.put(errorInfo.getType(), errorInfo);
    }
    return map;
  }

  public CloudErrorInfo getErrorInfo(){
    return myErrorInfo.get();
  }

}
