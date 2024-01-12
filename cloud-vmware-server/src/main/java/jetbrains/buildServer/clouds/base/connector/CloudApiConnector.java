

package jetbrains.buildServer.clouds.base.connector;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 3:26 PM
 */
public interface CloudApiConnector<T extends AbstractCloudImage, G extends AbstractCloudInstance> {

  void test() throws CheckedCloudException;

  /**
   * A special key of the cloud connector. Used to determine whether this cloud connector can be used in several cloud profiles.
   * <br/>
   *
   * It is supposed to represent the same username and server url/region/instance
   * @return see above.
   */
  @NotNull
  String getKey();

  @NotNull
  <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final T image) throws CheckedCloudException;

  @NotNull
  <R extends AbstractInstance> Map<T, Map<String, R>> fetchInstances(@NotNull final Collection<T> images) throws CheckedCloudException;

  @NotNull
  @Deprecated
  TypedCloudErrorInfo[] checkImage(@NotNull final T image);

  @NotNull
  Map<T, TypedCloudErrorInfo[]> checkImages(@NotNull final Collection<T> images);

  @NotNull
  TypedCloudErrorInfo[] checkInstance(@NotNull final G instance);
}