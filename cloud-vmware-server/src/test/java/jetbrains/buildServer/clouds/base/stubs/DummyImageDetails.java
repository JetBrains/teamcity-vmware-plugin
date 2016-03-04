package jetbrains.buildServer.clouds.base.stubs;

import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
public class DummyImageDetails implements CloudImageDetails {

  private final String myName;

  public DummyImageDetails(final String name) {
    myName = name;
  }

  @Override
  public CloneBehaviour getBehaviour() {
    return null;
  }

  @Override
  public int getMaxInstances() {
    return 0;
  }

  @Override
  public String getSourceName() {
    return myName;
  }
}
