package jetbrains.buildServer.clouds.base.connector;

import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 3:26 PM
 */
public interface CloudApiConnector {

  InstanceStatus getInstanceStatus(@NotNull final String instanceName);

  Map<String, AbstractInstance> listImageInstances(@NotNull final String imageName);

  Collection<TypedCloudErrorInfo> checkImage(@NotNull final String imageName);

  Collection<TypedCloudErrorInfo> checkInstance(@NotNull final String instanceName);
}
