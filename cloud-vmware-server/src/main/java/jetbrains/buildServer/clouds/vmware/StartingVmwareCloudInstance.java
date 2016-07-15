package jetbrains.buildServer.clouds.vmware;

import org.jetbrains.annotations.NotNull;

/**
 * Created by Sergey.Pak on 7/14/2016.
 */
public class StartingVmwareCloudInstance extends VmwareCloudInstance {
  public StartingVmwareCloudInstance(@NotNull final VmwareCloudImage image, @NotNull final String instanceName) {
    super(image, instanceName);
  }
}
