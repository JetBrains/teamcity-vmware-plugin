package jetbrains.buildServer.clouds.base;

import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:49 PM
 */
public abstract class AbstractCloudClient implements CloudClientEx, UpdatableCloudErrorProvider {

  protected final CloudErrorMap myErrorHolder;
  protected Map<String, AbstractCloudImage> myImageMap;
  protected UpdatableCloudErrorProvider myErrorProvider;


  public AbstractCloudClient() {
    myErrorHolder = new CloudErrorMap();
  }

  public AbstractCloudClient(@NotNull final Collection<? extends AbstractCloudImage> images) {
    this();
    for (AbstractCloudImage image : images) {
      myImageMap.put(image.getName(), image);
    }
  }

  @NotNull
  public Collection<? extends AbstractCloudImage> getImages() throws CloudException {
    return Collections.unmodifiableCollection(myImageMap.values());
  }

  public void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors) {
    myErrorProvider.updateErrors(errors);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }
}
