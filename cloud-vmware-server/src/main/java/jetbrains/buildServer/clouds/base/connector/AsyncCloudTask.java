

package jetbrains.buildServer.clouds.base.connector;

import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 3:27 PM
 */
public interface AsyncCloudTask {

  /**
   * Consecutive execution of this method will makes no effect. Only first call of this method starts the executing.
   * All next calls just return the result
   * @return result
   */
  CloudTaskResult executeOrGetResult();

  @NotNull
  String getName();

  long getStartTime();

  boolean isDone();
}