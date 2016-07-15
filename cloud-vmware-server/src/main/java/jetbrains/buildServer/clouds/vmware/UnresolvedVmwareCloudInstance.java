package jetbrains.buildServer.clouds.vmware;

import org.jetbrains.annotations.NotNull;

/**
 * Created by Sergey.Pak on 7/14/2016.
 */
public class UnresolvedVmwareCloudInstance extends VmwareCloudInstance {
  public UnresolvedVmwareCloudInstance(@NotNull final VmwareCloudImage image) {
    super(image);
  }

}
