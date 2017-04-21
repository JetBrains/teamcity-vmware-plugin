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

import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.clouds.base.errors.ErrorMessageUpdater;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Sergey.Pak
 *         Date: 11/12/2014
 *         Time: 5:03 PM
 */
public class VmwareErrorMessages implements ErrorMessageUpdater {

  private static final VmwareErrorMessages instance = new VmwareErrorMessages();

  public static VmwareErrorMessages getInstance() {
    return instance;
  }

  private static String getFriendlyMessageInternal(final String message, final String defaultMessage) {
    if (StringUtil.isEmpty(message)){
      return defaultMessage;
    }

    return message;
  }

  public String getFriendlyErrorMessage(final String message) {
    return getFriendlyMessageInternal(message, message);
  }

  public String getFriendlyErrorMessage(final String message, final String defaultMessage) {
    return getFriendlyMessageInternal(message, defaultMessage);
  }

  public String getFriendlyErrorMessage(final Throwable th) {
    return getFriendlyErrorMessage(th, th.getMessage());
  }

  public String getFriendlyErrorMessage(Throwable th, final String defaultMessage) {
    // showing only the root exception
    while (th.getCause() != null){
      th = th.getCause();
    }

    if (th instanceof UnknownHostException){
      return "Unknown host: " + th.getMessage();
    }

    return getFriendlyMessageInternal(th.getMessage(), defaultMessage);
  }
}
