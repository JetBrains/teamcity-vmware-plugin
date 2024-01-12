

package jetbrains.buildServer.clouds.vmware.errors;

import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;

/**
 * @author Sergey.Pak
 *         Date: 11/12/2014
 *         Time: 7:49 PM
 */
public class VmwareCheckedCloudException extends CheckedCloudException {

  public VmwareCheckedCloudException(final Throwable cause) {
    super(cause.getMessage(), cause);
  }

  public VmwareCheckedCloudException(final String message) {
    super(message);
  }

  public VmwareCheckedCloudException(final String message, final Throwable cause) {
    super(message, cause);
  }

}