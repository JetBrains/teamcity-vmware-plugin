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
import com.intellij.openapi.util.text.StringUtil;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.errors.VMWareCloudErrorInfoFactory;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/16/2014
 *         Time: 6:28 PM
 */
public class VMWareCloudClientFactory extends AbstractCloudClientFactory<VmwareCloudImageDetails, VmwareCloudImage, VMWareCloudClient> {

  private static final Logger LOG = Logger.getInstance(VMWareCloudClientFactory.class.getName());
  @NotNull private final String myHtmlPath;

  public VMWareCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                  @NotNull final PluginDescriptor pluginDescriptor) {
    super(cloudRegistrar);
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
  }

  @NotNull
  @Override
  public VMWareCloudClient createNewClient(@NotNull final CloudState state,
                                           @NotNull final Collection<VmwareCloudImageDetails> images,
                                           @NotNull final CloudClientParameters params) {
    return new VMWareCloudClient(params, images, createConnectorFromParams(params));
  }

  @Override
  public VMWareCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] profileErrors) {
    return new VMWareCloudClient(params, Collections.<VmwareCloudImageDetails>emptyList(), createConnectorFromParams(params));
  }

  @Override
  public Collection<VmwareCloudImageDetails> parseImageData(@NotNull final CloudClientParameters params) {
    return parseImageDataInternal(params);
  }

  static Collection<VmwareCloudImageDetails> parseImageDataInternal(final CloudClientParameters params) {
    final String imagesData = params.getParameter("vmware_images_data");
    final String[] split = imagesData.split(";X;:");
    List<VmwareCloudImageDetails> images = new ArrayList<VmwareCloudImageDetails>();
    for (String s : split) {
      final VmwareCloudImageDetails e = VmwareCloudImageDetails.fromString(s.trim());
      if (e != null)
        images.add(e);
    }
    return images;
  }

  @Nullable
  @Override
  protected TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params) {
    return new TypedCloudErrorInfo[0];
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

  @NotNull
  private VMWareApiConnector createConnectorFromParams(CloudClientParameters params){
    String serverUrl = params.getParameter(VMWareWebConstants.SERVER_URL);
    String username = params.getParameter(VMWareWebConstants.USERNAME);
    String password = params.getParameter(VMWareWebConstants.SECURE_PASSWORD);
    if (serverUrl != null && username != null) {
      try {
        return new VMWareApiConnectorImpl(new URL(serverUrl), username, password);
      } catch (MalformedURLException e) {
        LOG.warn(e.toString(), e);
      } catch (RemoteException e) {
        LOG.warn(e.toString(), e);
      }
    }
    throw new RuntimeException("Unable to create connector");
  }
}
