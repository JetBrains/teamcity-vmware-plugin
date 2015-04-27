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

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.*;
import jetbrains.buildServer.clouds.*;
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
public class VMWareCloudClientFactory implements CloudClientFactory<VmwareCloudImage, VmwareCloudInstance> {

  private static final Logger LOG = Logger.getInstance(VMWareCloudClientFactory.class.getName());
  @NotNull private final String myHtmlPath;
  @NotNull private final File myIdxStorage;

  public VMWareCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                  @NotNull final PluginDescriptor pluginDescriptor,
                                  @NotNull final ServerPaths serverPaths) {
    myIdxStorage = new File(serverPaths.getPluginDataDirectory(), "vmwareIdx");
    if (!myIdxStorage.exists()){
      myIdxStorage.mkdirs();
    }
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
    cloudRegistrar.registerCloudFactory(this);
  }


  public CloudClient<VmwareCloudImage, VmwareCloudInstance> createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
    final VMWareCloudClient client = new VMWareCloudClient(params, myIdxStorage);
    try {
      client.getApiConnector().test();
    } catch (CloudException e) {
      client.setErrorInfo(CloudErrorInfo.fromException(e));
    }
    return client;
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

}
