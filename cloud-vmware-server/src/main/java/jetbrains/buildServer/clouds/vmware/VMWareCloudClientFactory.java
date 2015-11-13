/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.vmware;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
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
  @NotNull private final CloudInstancesProvider myInstancesProvider;

  public VMWareCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                  @NotNull final PluginDescriptor pluginDescriptor,
                                  @NotNull final ServerPaths serverPaths,
                                  @NotNull final CloudInstancesProvider instancesProvider
                                  ) {
    super(cloudRegistrar);
    myInstancesProvider = instancesProvider;
    myIdxStorage = new File(serverPaths.getPluginDataDirectory(), "vmwareIdx");
    if (!myIdxStorage.exists()){
      myIdxStorage.mkdirs();
    }
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
  }

  @NotNull
  @Override
  public VMWareCloudClient createNewClient(@NotNull final CloudState state,
                                           @NotNull final Collection<VmwareCloudImageDetails> images,
                                           @NotNull final CloudClientParameters params) {
    final VMWareApiConnector apiConnector = createConnectorFromParams(params);
    final VMWareCloudClient vmWareCloudClient = new VMWareCloudClient(params, apiConnector, myIdxStorage);
    try {
      apiConnector.test();
    } catch (CheckedCloudException e) {
      vmWareCloudClient.updateErrors(TypedCloudErrorInfo.fromException(e));
    }
    return vmWareCloudClient;
  }

  @Override
  public VMWareCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] profileErrors) {
    final VMWareCloudClient client = new VMWareCloudClient(params, createConnectorFromParams(params), myIdxStorage);
    client.updateErrors(profileErrors);
    return client;
  }

  @Override
  public Collection<VmwareCloudImageDetails> parseImageData(@NotNull final CloudClientParameters params) {
    return parseImageDataInternal(params);
  }

  static Collection<VmwareCloudImageDetails> parseImageDataInternal(final CloudClientParameters params) {
    final String imagesData = params.getParameter("vmware_images_data");
    Gson gson = new Gson();
    final VmwareCloudImageDetails[] details = gson.fromJson(imagesData, VmwareCloudImageDetails[].class);
    if (details==null){
      return Collections.emptyList();
    }
    return Arrays.asList(details);
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

  @NotNull
  protected VMWareApiConnector createConnectorFromParams(CloudClientParameters params){
    String serverUrl = params.getParameter(VMWareWebConstants.SERVER_URL);
    String username = params.getParameter(VMWareWebConstants.USERNAME);
    String password = params.getParameter(VMWareWebConstants.SECURE_PASSWORD);
    if (serverUrl != null && username != null) {
      try {
        return new VMWareApiConnectorImpl(new URL(serverUrl), username, password, myInstancesProvider);
      } catch (MalformedURLException e) {
        LOG.warn(e.toString(), e);
      }
    }
    throw new CloudException("Unable to create connector");
  }
}
