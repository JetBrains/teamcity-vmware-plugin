package jetbrains.buildServer.clouds.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudClientFactory implements CloudClientFactory {

  public AbstractCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar) {
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public CloudClientEx createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
    final String imagesData = params.getParameter("images_data");
    final TypedCloudErrorInfo[] profileErrors = checkClientParams(params);
    if (profileErrors != null && profileErrors.length > 0){
      return createNewClient(state, params, profileErrors);
    }
    final AbstractCloudImageDetails[] imageDetailsList = parseImageData(imagesData);

    final List<AbstractCloudImage> images = new ArrayList<AbstractCloudImage>();
    for (AbstractCloudImageDetails imageDetails : imageDetailsList) {
      images.add(checkAndCreateImage(imageDetails));
    }
    return createNewClient(state, images, params);
  }

  public abstract  <T extends AbstractCloudClient, G extends AbstractCloudImage> T createNewClient(
    @NotNull final CloudState state, @NotNull final Collection<G> images, @NotNull final CloudClientParameters params);

  public abstract <T extends AbstractCloudClient> T createNewClient(
    @NotNull final CloudState state, @NotNull final CloudClientParameters params, TypedCloudErrorInfo[] profileErrors);

  public abstract <T extends AbstractCloudImageDetails> T[] parseImageData(String imageData);

  @Nullable
  protected abstract <T extends AbstractCloudImageDetails, G extends AbstractCloudImage> G checkAndCreateImage(@NotNull final T imageDetails);

  @Nullable
  protected abstract TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params);


}
