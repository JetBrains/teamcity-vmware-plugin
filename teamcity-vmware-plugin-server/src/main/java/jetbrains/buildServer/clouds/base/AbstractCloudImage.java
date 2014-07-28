package jetbrains.buildServer.clouds.base;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:50 PM
 */
public abstract class AbstractCloudImage implements CloudImage, UpdatableCloudErrorProvider {
  private final UpdatableCloudErrorProvider myErrorProvider;
  private final Map<String, AbstractCloudInstance> myInstances;

  protected AbstractCloudImage() {
    myErrorProvider = new CloudErrorMap();
    myInstances = new HashMap<String, AbstractCloudInstance>();
  }


  public void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors) {
    myErrorProvider.updateErrors(errors);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }

  @NotNull
  public Collection<? extends AbstractCloudInstance> getInstances() {
    return Collections.unmodifiableCollection(myInstances.values());
  }

  @Nullable
  public AbstractCloudInstance findInstanceById(@NotNull final String id) {
    return myInstances.get(id);
  }

  public abstract void createInstance(@NotNull final String instanceName);

  public abstract void removeInstance(@NotNull final String instanceName);
}
