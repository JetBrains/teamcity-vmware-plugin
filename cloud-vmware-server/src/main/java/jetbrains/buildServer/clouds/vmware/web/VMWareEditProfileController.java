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

import com.intellij.openapi.diagnostic.Logger;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.clouds.vmware.VmwareConstants;
import jetbrains.buildServer.clouds.vmware.connector.*;
import jetbrains.buildServer.clouds.vmware.errors.VmwareErrorMessages;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolUtil;
import jetbrains.buildServer.web.openapi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Sergey.Pak
 *         Date: 5/28/2014
 *         Time: 2:58 PM
 */
public class VMWareEditProfileController extends BaseFormXmlController {

  private static final Logger LOG = Logger.getInstance(VMWareEditProfileController.class.getName());

  @NotNull private final String myJspPath;
  @NotNull private final String myHtmlPath;
  @NotNull private final String mySnapshotsPath;
  @NotNull private final PluginDescriptor myPluginDescriptor;
  @NotNull private final AgentPoolManager myAgentPoolManager;

  public VMWareEditProfileController(@NotNull final SBuildServer server,
                                     @NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager,
                                     @NotNull final AgentPoolManager agentPoolManager) {
    super(server);
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
    myPluginDescriptor = pluginDescriptor;
    myAgentPoolManager = agentPoolManager;
    myJspPath = myPluginDescriptor.getPluginResourcesPath("vmware-settings.jsp");
    mySnapshotsPath = pluginDescriptor.getPluginResourcesPath("vmware-getsnapshotlist.html");
    manager.registerController(myHtmlPath, this);
    manager.registerController(pluginDescriptor.getPluginResourcesPath("vmware-getsnapshotlist.html"), new GetSnapshotsListController());
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    final ModelAndView mv = new ModelAndView(myJspPath);
    final String projectId = request.getParameter("projectId");
    mv.getModel().put("refreshablePath", myHtmlPath);
    mv.getModel().put("refreshSnapshotsPath", mySnapshotsPath);
    mv.getModel().put("resPath", myPluginDescriptor.getPluginResourcesPath());

    final List<AgentPool> pools = new ArrayList<>();
    // TODO improve
    if (!BuildProject.ROOT_PROJECT_ID.equals(projectId))
      pools.add(AgentPoolUtil.DUMMY_PROJECT_POOL);
    pools.addAll(myAgentPoolManager.getProjectOwnedAgentPools(projectId));
    mv.getModel().put("agentPools", pools);

    return mv;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
    final ActionErrors errors = new ActionErrors();

    final BasePropertiesBean propsBean = new BasePropertiesBean(null);
    PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

    final Map<String, String> props = propsBean.getProperties();
    final String serverUrl = props.get(VMWareWebConstants.SERVER_URL);
    final String username = props.get(VMWareWebConstants.USERNAME);
    final String password = props.get(VMWareWebConstants.SECURE_PASSWORD);

    try {
      final VMWareApiConnector myApiConnector = VmwareApiConnectorsPool.getOrCreateConnector(new URL(serverUrl), username, password, null, null, null);
      myApiConnector.test();
      xmlResponse.addContent(getVirtualMachinesAsElement(myApiConnector.getVirtualMachines(true)));
      xmlResponse.addContent(getFoldersAsElement(myApiConnector.getFolders()));
      xmlResponse.addContent(getResourcePoolsAsElement(myApiConnector.getResourcePools()));
      xmlResponse.addContent(getCustomizationSpecsAsElement(myApiConnector.getCustomizationSpecs()));
    } catch (Exception ex) {
      LOG.warnAndDebugDetails("Unable to get vCenter details: " + ex.toString(), ex);
      errors.addError(
        "errorFetchResults",
        VmwareErrorMessages.getInstance().getFriendlyErrorMessage(
          ex, "Please check the connection parameters. See the teamcity-clouds.log for details")
      );
      writeErrors(xmlResponse, errors);
    }
  }

  private Element getVirtualMachinesAsElement(@NotNull final Map<String, VmwareInstance> vmMap){
    final Element element = new Element("VirtualMachines");
    final List<VmwareInstance> values = new ArrayList<VmwareInstance>(vmMap.values());
    Collections.sort(values, new Comparator<VmwareInstance>() {
      public int compare(@NotNull final VmwareInstance o1, @NotNull final VmwareInstance o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (VmwareInstance vm : values) {
      Element vmElement = new Element("VirtualMachine");
      vmElement.setAttribute("name", vm.getName());
      vmElement.setAttribute("template", String.valueOf(vm.isReadonly()));
      vmElement.setAttribute("datacenterId", vm.getDatacenterId());
      element.addContent(vmElement);
    }
    return element;
  }

  private Element getFoldersAsElement(Map<String, ? extends VmwareManagedEntity> folders){
    final Element element = new Element("Folders");
    final Set<String> folderNames = folders.keySet();
    final List<String> sortedList = getIgnoreCaseSortedList(folderNames);
    for (String folderName : sortedList) {
      Element folderElement = new Element("Folder");
      folderElement.setAttribute("name", folderName);
      final VmwareManagedEntity entity = folders.get(folderName);
      folderElement.setAttribute("value", entity.getId());
      folderElement.setAttribute("datacenterId", entity.getDatacenterId());
      element.addContent(folderElement);
    }
    return element;
  }

  private Element getResourcePoolsAsElement(Map<String, ? extends VmwareManagedEntity> resourcePools){
    final Element element = new Element("ResourcePools");
    final List<String> sortedList = getIgnoreCaseSortedList(resourcePools.keySet());
    final Element defaultPoolElem = new Element("ResourcePool");
    defaultPoolElem.setAttribute("name", "<Default>");
    defaultPoolElem.setAttribute("value", VmwareConstants.DEFAULT_RESOURCE_POOL);
    element.addContent(defaultPoolElem);
    for (String poolName : sortedList) {
      Element poolElement = new Element("ResourcePool");
      poolElement.setAttribute("name", poolName);
      final VmwareManagedEntity entity = resourcePools.get(poolName);
      poolElement.setAttribute("value", entity.getId());
      poolElement.setAttribute("datacenterId", entity.getDatacenterId());
      element.addContent(poolElement);
    }
    return element;
  }

  private Element getCustomizationSpecsAsElement(Map<String, String> customizationSpecs){
    final Element element = new Element("CustomizationSpecs");
    final List<String> sortedList = getIgnoreCaseSortedList(customizationSpecs.keySet());
    for (String specName : sortedList) {
      Element specElement = new Element("CustomizationSpec");
      specElement.setAttribute("name", specName);
      specElement.setAttribute("type", customizationSpecs.get(specName));
      element.addContent(specElement);
    }
    return element;
  }

  private List<String> getIgnoreCaseSortedList(Collection<String> src){
    final List<String> sortedList = new ArrayList<String>(src);
    Collections.sort(sortedList, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareToIgnoreCase(o2);
      }
    });
    return sortedList;
  }
}
