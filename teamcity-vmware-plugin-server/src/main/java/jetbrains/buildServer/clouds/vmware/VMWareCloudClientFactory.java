package jetbrains.buildServer.clouds.vmware;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
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
public class VMWareCloudClientFactory implements CloudClientFactory {

  @NotNull private final String myHtmlPath;

  public VMWareCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                  @NotNull final PluginDescriptor pluginDescriptor) {
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public CloudClientEx createNewClient(@NotNull CloudState cloudState, @NotNull CloudClientParameters cloudClientParameters) {
    try {
      return new VMWareCloudClient(cloudClientParameters);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    } catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public String getCloudCode() {
    return VMWareCloudConstants.TYPE;
  }

  @NotNull
  public String getDisplayName() {
    return "VMWare VSphere";
  }

  @Nullable
  public String getEditProfileUrl() {
    return myHtmlPath;
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
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return configParams.containsKey(VMWarePropertiesNames.IMAGE_NAME);
  }
}
