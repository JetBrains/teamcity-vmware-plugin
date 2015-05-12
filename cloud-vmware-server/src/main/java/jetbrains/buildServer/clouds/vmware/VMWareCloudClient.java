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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.server.tasks.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:23 PM
 */
public class VMWareCloudClient implements CloudClient<VmwareCloudImage, VmwareCloudInstance>{
  private static final Logger LOG = Logger.getInstance(VMWareCloudClient.class.getName());

  @NotNull private final CloudClientParameters myCloudClientParameters;
  @NotNull private final VMWareApiConnector myApiConnector;
  @NotNull private final File myIdxStorage;
  private CloudErrorInfo myErrorInfo;
  private final Map<String, VmwareCloudImage> myImages = new HashMap<String, VmwareCloudImage>();
  private boolean myInitialized = false;
  private final CloudAsyncTaskExecutor myAsyncTaskExecutor;


  public VMWareCloudClient(@NotNull final CloudClientParameters cloudClientParameters,
                           @NotNull final File idxStorage) {
    myCloudClientParameters = cloudClientParameters;
    myApiConnector = createApiConnector(cloudClientParameters);
    myIdxStorage = idxStorage;
    myAsyncTaskExecutor = new CloudAsyncTaskExecutor(cloudClientParameters.getProfileDescription());
  }

  public void initializeImages(final Collection<CloudImageParameters> imagesData){
    for (CloudImageParameters imageParams : imagesData) {
      VmwareCloudImageDetails imageDetails = new VmwareCloudImageDetails(imageParams);
      final String sourceName = imageParams.getParameter("sourceName");
      myImages.put(sourceName, new VmwareCloudImage(myApiConnector, imageDetails, myAsyncTaskExecutor, myIdxStorage));
    }
    myInitialized = true;
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  @Nullable
  public VmwareCloudImage findImageById(@NotNull final String imageId) throws CloudException {
    return myImages.get(imageId);
  }

  @Nullable
  public VmwareCloudInstance findInstanceByAgent(@NotNull AgentDescription agentDescription) {
    final String imageName = agentDescription.getAvailableParameters().get(VMWarePropertiesNames.IMAGE_NAME);
    if (imageName != null) {
      final VmwareCloudImage cloudImage = findImageById(imageName);
      if (cloudImage != null) {
        return cloudImage.findInstanceById(agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME));
      }
    }
    return null;
  }

  @NotNull
  public Collection<VmwareCloudImage> getImages() throws CloudException {
    return myImages.values();
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public void setErrorInfo(@Nullable final CloudErrorInfo errorInfo) {
    myErrorInfo = errorInfo;
  }

  public boolean canStartNewInstance(@NotNull final VmwareCloudImage image) {
    return false;
  }

  @Nullable
  public String generateAgentName(@NotNull AgentDescription agentDescription) {
    return agentDescription.getAvailableParameters().get(VMWarePropertiesNames.INSTANCE_NAME);
  }

  @NotNull
  public VMWareApiConnector getApiConnector() {
    return myApiConnector;
  }

  private VMWareApiConnector createApiConnector(CloudClientParameters cloudClientParameters) {
    String serverUrl = cloudClientParameters.getParameter(VMWareWebConstants.SERVER_URL);
    String username = cloudClientParameters.getParameter(VMWareWebConstants.USERNAME);
    String password = cloudClientParameters.getParameter(VMWareWebConstants.SECURE_PASSWORD);
    if (serverUrl != null && username != null) {
      try {
        return new VMWareApiConnectorImpl(new URL(serverUrl), username, password);
      } catch (MalformedURLException e) {
        LOG.warn(e.toString(), e);
      }
    }
    throw new RuntimeException("Unable to create connector");
  }

  public void dispose() {
    myAsyncTaskExecutor.dispose();
    myApiConnector.dispose();
  }

}
