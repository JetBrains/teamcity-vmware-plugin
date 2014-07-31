package jetbrains.buildServer.clouds.base.connector;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 6:41 PM
 */
public class CloudTaskResult {
  private final boolean myHasErrors;
  private final String myDescription;
  private final Throwable myThrowable;



  public CloudTaskResult() {
    this(false, null, null);
  }

  public CloudTaskResult(final String description) {
    this(false, description, null);
  }
  public CloudTaskResult(final boolean hasErrors, final String description, final Throwable throwable) {
    myHasErrors = hasErrors;
    myDescription = description;
    myThrowable = throwable;
  }

  public boolean isHasErrors() {
    return myHasErrors;
  }

  public String getDescription() {
    return myDescription;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }
}
