package jetbrains.buildServer.clouds.base.beans;

import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 2:03 PM
 */
public abstract class AbstractCloudImageDetails {

  protected final String myImageName;
  protected final String myImageId;

  public AbstractCloudImageDetails(final String imageName, final String imageId) {
    myImageName = imageName;
    myImageId = imageId;
  }

  public String getImageName() {
    return myImageName;
  }

  public String getImageId() {
    return myImageId;
  }

  public abstract <T extends AbstractCloudImageDetails> T parseDetails(@NotNull final String serializedImage);

  public abstract Class<?> getImageClass();
}
