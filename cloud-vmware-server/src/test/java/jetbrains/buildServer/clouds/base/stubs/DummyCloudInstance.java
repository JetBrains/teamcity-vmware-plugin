package jetbrains.buildServer.clouds.base.stubs;

import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
public class DummyCloudInstance extends AbstractCloudInstance<DummyCloudImage> {

  protected DummyCloudInstance(@NotNull final DummyCloudImage image, @NotNull final String name, @NotNull final String instanceId) {
    super(image, name, instanceId);
  }

  @Override
  public boolean containsAgent(@NotNull final AgentDescription agent) {
    return getName().equals(agent.getAvailableParameters().get("name"));
  }
}
