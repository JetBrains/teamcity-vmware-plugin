

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.ManagedObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 2/6/2015
 *         Time: 4:43 PM
 */
public interface VmwareManagedEntity {
  @NotNull
  String getId();

  @NotNull
  String getName();

  /**
   *
   * @return full path
   */
  @NotNull
  String getPath();

  @Nullable
  String getDatacenterId();

  @NotNull
  ManagedObjectReference getMOR();

  @Nullable
  ManagedObjectReference getParentMOR();

  /*

  @NotNull
  default String getPathOrUniqueName() {
    return getPath() == null ? String.format("%s(%s)", getName(), getId()) : getPath();
  }
  */
}