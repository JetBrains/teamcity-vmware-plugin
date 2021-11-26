/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.vmware.web;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.clouds.vmware.connector.VmwareApiConnectorsPool;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.clouds.vmware.VmwareConstants;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Sergey.Pak
 *         Date: 5/30/2014
 *         Time: 4:07 PM
 */
public class GetSnapshotsListController extends BaseFormXmlController {

  private static final Logger LOG = Logger.getInstance(GetSnapshotsListController.class.getName());
  private SSLTrustStoreProvider myStoreProvider;

  public GetSnapshotsListController(@NotNull final SSLTrustStoreProvider storeProvider) {

    myStoreProvider = storeProvider;
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    return null;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
    final BasePropertiesBean propsBean = new BasePropertiesBean(null);
    PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

    final Map<String, String> props = propsBean.getProperties();
    @NotNull final String serverUrl = props.get(VMWareWebConstants.SERVER_URL);
    @NotNull final String username = props.get(VMWareWebConstants.USERNAME);
    @NotNull final String password = props.get(VMWareWebConstants.SECURE_PASSWORD);
    @NotNull final String imageName = props.get("image");
    if (StringUtil.isEmpty(serverUrl) ||
        StringUtil.isEmpty(imageName))
      return;
    try {
      final VMWareApiConnector myApiConnector = VmwareApiConnectorsPool.getOrCreateConnector(
        new URL(serverUrl), username, password, null, null, null, myStoreProvider);
      final Map<String, VirtualMachineSnapshotTree> snapshotList = myApiConnector.getSnapshotList(imageName);
      Element snapshots = new Element("Snapshots");
      snapshots.setAttribute("vmName", imageName);
      final Element currentVersion = new Element("Snapshot");
      currentVersion.setAttribute("name", "<Current State>");
      currentVersion.setAttribute("value", VmwareConstants.CURRENT_STATE);
      snapshots.addContent((Content) currentVersion);
      if (snapshotList.size() > 0){
        final Element latestSnapshot = new Element("Snapshot");
        latestSnapshot.setAttribute("name", "<Latest snapshot>");
        latestSnapshot.setAttribute("value", VmwareConstants.LATEST_SNAPSHOT);
        snapshots.addContent((Content) latestSnapshot);
      }
      for (String snapshotName : snapshotList.keySet()) {
        Element snap = new Element("Snapshot");
        snap.setAttribute("name", snapshotName);
        snap.setAttribute("value", snapshotName);
        snapshots.addContent((Content) snap);
      }
      xmlResponse.addContent((Content) snapshots);

    } catch (VmwareCheckedCloudException e) {
      LOG.warnAndDebugDetails("Unable to get snapshot list", e);
    } catch (MalformedURLException e) {
      LOG.warnAndDebugDetails("Unable to get snapshot list", e);
    }

  }
}
