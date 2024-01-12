

package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.ws.XmlGen;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.server.CloudManagerBase;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareApiConnectorsPool;
import jetbrains.buildServer.clouds.vmware.tasks.VmwareUpdateTaskManager;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.XmlUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/16/2014
 *         Time: 6:28 PM
 */
public class VMWareCloudClientFactory extends AbstractCloudClientFactory<VmwareCloudImageDetails, VMWareCloudClient> {

  private static final Logger LOG = Logger.getInstance(VMWareCloudClientFactory.class.getName());
  @NotNull private final String myHtmlPath;
  @NotNull private final File myIdxStorage;
  @NotNull private final CloudManagerBase myCloudManager;
  @NotNull private final VmwareUpdateTaskManager myUpdateTaskManager;
  @NotNull private final SSLTrustStoreProvider mySslTrustStoreProvider;
  @NotNull private final CloudInstancesProvider myInstancesProvider;
  @NotNull private final ServerSettings myServerSettings;

  public VMWareCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                  @NotNull final PluginDescriptor pluginDescriptor,
                                  @NotNull final ServerPaths serverPaths,
                                  @NotNull final CloudInstancesProvider instancesProvider,
                                  @NotNull final CloudManagerBase cloudManager,
                                  @NotNull final ServerSettings serverSettings,
                                  @NotNull final VmwareUpdateTaskManager updateTaskManager,
                                  @NotNull final SSLTrustStoreProvider sslTrustStoreProvider
                                  ) {
    super(cloudRegistrar);
    myInstancesProvider = instancesProvider;
    myIdxStorage = new File(serverPaths.getPluginDataDirectory(), "vmwareIdx");
    myCloudManager = cloudManager;
    myUpdateTaskManager = updateTaskManager;
    mySslTrustStoreProvider = sslTrustStoreProvider;
    if (!myIdxStorage.exists()){
      myIdxStorage.mkdirs();
    }
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
    myServerSettings = serverSettings;

    XmlGen.setXmlReaderSupplier(() -> {
      try {
        return XmlUtil.createXMLReader();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  @NotNull
  @Override
  public VMWareCloudClient createNewClient(@NotNull final CloudState state,
                                           @NotNull final Collection<VmwareCloudImageDetails> images,
                                           @NotNull final CloudClientParameters params) {
    final CloudProfile profile = myCloudManager.findProfileById(state.getProjectId(), state.getProfileId());
    if (profile == null){
      throw new CloudException(String.format("Unable to find profile by profileId '%s' and projectId '%s'", state.getProfileId(), state.getProjectId()));
    }
    final VMWareApiConnector apiConnector = createConnectorFromParams(state, params);
    final VMWareCloudClient vmWareCloudClient =
      new VMWareCloudClient(profile, apiConnector, myUpdateTaskManager, myIdxStorage);
    return vmWareCloudClient;
  }

  @Override
  public VMWareCloudClient createNewClient(@NotNull final CloudState state,
                                           @NotNull final CloudClientParameters params,
                                           final TypedCloudErrorInfo[] profileErrors) {
    final CloudProfile profile = myCloudManager.findProfileById(state.getProjectId(), state.getProfileId());
    if (profile == null){
      throw new CloudException(String.format("Unable to find profile by profileId '%s' and projectId '%s'", state.getProfileId(), state.getProjectId()));
    }
    final VMWareCloudClient client = new VMWareCloudClient(
      profile, createConnectorFromParams(state, params),
      myUpdateTaskManager,
      myIdxStorage
    );
    client.updateErrors(profileErrors);
    return client;
  }

  @Override
  public Collection<VmwareCloudImageDetails> parseImageData(@NotNull final CloudClientParameters params) {
    return parseImageDataInternal(params);
  }

  static Collection<VmwareCloudImageDetails> parseImageDataInternal(final CloudClientParameters params) {
    return params.getCloudImages().stream().map(VmwareCloudImageDetails::new).collect(Collectors.toList());
  }

  @Nullable
  @Override
  protected TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params) {
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  public String getCloudCode() {
    return VmwareConstants.TYPE;
  }

  @NotNull
  public String getDisplayName() {
    return "VMware vSphere";
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
    return new VmwarePropertiesProcessor(myCloudManager);
  }

  public boolean canBeAgentOfType(@NotNull AgentDescription agentDescription) {
    final Map<String, String> configParams = agentDescription.getConfigurationParameters();
    return configParams.containsKey(VMWarePropertiesNames.IMAGE_NAME);
  }

  @NotNull
  protected VMWareApiConnector createConnectorFromParams(@NotNull final CloudState state, CloudClientParameters params){
    String serverUrl = params.getParameter(VMWareWebConstants.SERVER_URL);
    String username = params.getParameter(VMWareWebConstants.USERNAME);
    String password = params.getParameter(VMWareWebConstants.SECURE_PASSWORD);
    if (serverUrl != null && username != null) {
      try {
        return VmwareApiConnectorsPool.getOrCreateConnector(
          new URL(serverUrl), username, password, myServerSettings.getServerUUID(), state.getProfileId(),
          myInstancesProvider, mySslTrustStoreProvider);
      } catch (MalformedURLException e) {
        LOG.warnAndDebugDetails(e.toString(), e);
        throw new CloudException("Unable to connect to vCenter: " + e.toString());
      }
    }
    throw new CloudException("Unable to connect to vCenter: please check connection parameters" );
  }
}