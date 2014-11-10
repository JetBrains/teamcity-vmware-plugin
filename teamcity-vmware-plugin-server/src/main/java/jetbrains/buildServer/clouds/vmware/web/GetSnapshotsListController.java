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

package jetbrains.buildServer.clouds.vmware.web;

import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.clouds.vmware.VMWareCloudConstants;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Sergey.Pak
 *         Date: 5/30/2014
 *         Time: 4:07 PM
 */
public class GetSnapshotsListController extends BaseFormXmlController {

  public GetSnapshotsListController() {

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
    final String serverUrl = props.get(VMWareWebConstants.SERVER_URL);
    final String username = props.get(VMWareWebConstants.USERNAME);
    final String password = props.get(VMWareWebConstants.SECURE_PASSWORD);
    final String imageName = props.get("image");
    try {
      final VMWareApiConnector myApiConnector = new VMWareApiConnectorImpl(new URL(serverUrl),username, password);
      final Map<String, VirtualMachineSnapshotTree> snapshotList = myApiConnector.getSnapshotList(imageName);
      Element snapshots = new Element("Snapshots");
      snapshots.setAttribute("vmName", imageName);
      if (TeamCityProperties.getBoolean(VMWareCloudConstants.SHOW_CURRENT_VERSION_SNAPSHOT)){
        Element currentVersion = new Element("Snapshot");
        currentVersion.setAttribute("name", "<Current Version>");
        currentVersion.setAttribute("value", "");
        snapshots.addContent(currentVersion);
      }
      for (String snapshotName : snapshotList.keySet()) {
        Element snap = new Element("Snapshot");
        snap.setAttribute("name", snapshotName);
        snap.setAttribute("value", snapshotName);
        snapshots.addContent(snap);
      }
      xmlResponse.addContent(snapshots);

    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (RemoteException e) {
      e.printStackTrace();
    }

  }
}
