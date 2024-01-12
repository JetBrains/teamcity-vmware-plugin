

package jetbrains.buildServer.clouds.base.errors;

import java.net.UnknownHostException;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Sergey.Pak
 *         Date: 11/12/2014
 *         Time: 5:03 PM
 */
public class SimpleErrorMessages implements ErrorMessageUpdater {

  private static final SimpleErrorMessages instance = new SimpleErrorMessages();

  public static SimpleErrorMessages getInstance() {
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