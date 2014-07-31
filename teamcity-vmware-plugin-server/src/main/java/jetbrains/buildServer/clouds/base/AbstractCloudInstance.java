package jetbrains.buildServer.clouds.base;

import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudInstance implements CloudInstance, UpdatableCloudErrorProvider {

  private UpdatableCloudErrorProvider myErrorProvider;
  protected InstanceStatus myStatus;
  protected final AbstractCloudImage myImage;

  protected AbstractCloudInstance(@NotNull final AbstractCloudImage image) {
    myImage = image;
    myErrorProvider = new CloudErrorMap();
  }

  public void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors) {
    myErrorProvider.updateErrors(errors);
  }

  @NotNull
  public CloudImage getImage() {
    return myImage;
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  public void setStatus(final InstanceStatus status) {
    myStatus = status;
  }
}
