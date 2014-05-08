package jetbrains.buildServer.clouds.vmware;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 4/16/2014
 *         Time: 6:28 PM
 */
public class VSphereCloudClientFactory implements CloudClientFactory {

  @NotNull private final String myJspPath;

  public VSphereCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                   @NotNull final PluginDescriptor pluginDescriptor) {
    myJspPath = pluginDescriptor.getPluginResourcesPath("profile-settings.jsp");
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public CloudClientEx createNewClient(@NotNull CloudState cloudState, @NotNull CloudClientParameters cloudClientParameters) {
    try {
      return new VSphereCloudClient(cloudClientParameters);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    } catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public String getCloudCode() {
    return VSphereCloudConstants.TYPE;
  }

  @NotNull
  public String getDisplayName() {
    return "VMWare VSphere";
  }

  @Nullable
  public String getEditProfileUrl() {
    return myJspPath;
  }

  @NotNull
  public Map<String, String> getInitialParameterValues() {
    return Collections.emptyMap();
  }

  @NotNull
  public PropertiesProcessor getPropertiesProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> stringStringMap) {
        return Collections.emptyList();
      }
    };
  }

  public boolean canBeAgentOfType(@NotNull AgentDescription agentDescription) {
    return true;
  }
}
