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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Sergey.Pak
 *         Date: 11/12/2014
 *         Time: 5:03 PM
 */
public class VmwareErrorMessages {

  private static final Pattern INVALID_LOGIN_PATTERN =
    Pattern.compile("VI SDK invoke exception:com\\.vmware\\.vim25\\.InvalidLogin");

  private static final Pattern INVALID_HOST_PATTERN =
    Pattern.compile("VI SDK invoke exception:java\\.net\\.UnknownHostException: (.+)");

  private static final VmwareErrorMessages instance = new VmwareErrorMessages();

  public static VmwareErrorMessages getInstance() {
    return instance;
  }

  private static String getUserFriendlyMessage(final String message, final String defaultMessage) {
    if (StringUtil.isEmpty(message)){
      return "No details available";
    }

    if (INVALID_LOGIN_PATTERN.matcher(message).matches()) {
      return "Cannot authorize. Please check your username and password";
    }
    final Matcher invalidHostMatcher = INVALID_HOST_PATTERN.matcher(message);
    if (invalidHostMatcher.matches()){
      return String.format("Cannot connect to %s", invalidHostMatcher.group(1));
    }

    return defaultMessage;
  }

  public String getFriendlyErrorMessage(final String message) {
    return getUserFriendlyMessage(message, message);
  }

  public String getFriendlyErrorMessage(final String message, final String defaultMessage) {
    return getUserFriendlyMessage(message, defaultMessage);
  }

  public String getFriendlyErrorMessage(final Throwable th) {
    return getUserFriendlyMessage(th.getMessage(), th.getMessage());
  }

  public String getFriendlyErrorMessage(final Throwable th, final String defaultMessage) {
    return getUserFriendlyMessage(th.getMessage(), defaultMessage);
  }
}
