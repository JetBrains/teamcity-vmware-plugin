/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.net.URL;
import java.util.*;
import javax.net.ssl.SSLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.BuildProject;
import jetbrains.buildServer.clouds.vmware.VmwareConstants;
import jetbrains.buildServer.clouds.vmware.connector.*;
import jetbrains.buildServer.clouds.vmware.connector.beans.FolderBean;
import jetbrains.buildServer.clouds.vmware.connector.beans.ResourcePoolBean;
import jetbrains.buildServer.clouds.base.errors.SimpleErrorMessages;
import jetbrains.buildServer.controllers.*;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolUtil;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
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
  @NotNull private final String myConfigHelperPath;
  @NotNull private final PluginDescriptor myPluginDescriptor;
  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final AgentPoolManager myAgentPoolManager;
  @NotNull private final SSLTrustStoreProvider mySslTrustStoreProvider;

  public VMWareEditProfileController(@NotNull final SBuildServer server,
                                     @NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager,
                                     @NotNull final ProjectManager projectManager,
                                     @NotNull final AuthorizationInterceptor authInterceptor,
                                     @NotNull final AgentPoolManager agentPoolManager,
                                     @NotNull final SSLTrustStoreProvider sslTrustStoreProvider
                                     ) {
    super(server);
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
    myPluginDescriptor = pluginDescriptor;
    myProjectManager = projectManager;
    myAgentPoolManager = agentPoolManager;
    mySslTrustStoreProvider = sslTrustStoreProvider;
    myJspPath = myPluginDescriptor.getPluginResourcesPath("vmware-settings.jsp");
    mySnapshotsPath = pluginDescriptor.getPluginResourcesPath("vmware-getsnapshotlist.html");
    myConfigHelperPath = pluginDescriptor.getPluginResourcesPath("vmware-config-helper.html");

    authInterceptor.addPathBasedPermissionsChecker(myHtmlPath, new VmwareEditProfilePermissionChecker());
    manager.registerController(myHtmlPath, this);

    authInterceptor.addPathBasedPermissionsChecker(mySnapshotsPath, new VmwareEditProfilePermissionChecker());
    manager.registerController(mySnapshotsPath, new GetSnapshotsListController(mySslTrustStoreProvider));

    authInterceptor.addPathBasedPermissionsChecker(myConfigHelperPath, new VmwareEditProfilePermissionChecker());
    manager.registerController(myConfigHelperPath, new ConfigurationHelperController(mySslTrustStoreProvider));
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    final ModelAndView mv = new ModelAndView(myJspPath);
    final String projectId = request.getParameter("projectId");
    mv.getModel().put("refreshablePath", myHtmlPath);
    mv.getModel().put("refreshSnapshotsPath", mySnapshotsPath);
    mv.getModel().put("configurationHelperPath", myConfigHelperPath);
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

    IOGuard.allowNetworkCall(() -> {
      try {
        final VMWareApiConnector myApiConnector = VmwareApiConnectorsPool.getOrCreateConnector(
          new URL(serverUrl), username, password, null, null, null, mySslTrustStoreProvider);
        myApiConnector.test();
        xmlResponse.addContent(getVirtualMachinesAsElement(myApiConnector.getVirtualMachines(true)));
        xmlResponse.addContent(getFoldersAsElement(myApiConnector.getFolders()));
        xmlResponse.addContent(getResourcePoolsAsElement(myApiConnector.getResourcePools()));
        xmlResponse.addContent(getCustomizationSpecsAsElement(myApiConnector.getCustomizationSpecs()));
      } catch (Exception ex) {
        LOG.warnAndDebugDetails("Unable to get vCenter details: " + ex.toString(), ex);
        if (ex.getCause() != null && ex.getCause() instanceof SSLException){
          errors.addError(
            "errorFetchResultsSSL",
            SimpleErrorMessages.getInstance().getFriendlyErrorMessage(
              ex, "Please check the connection parameters. See the teamcity-clouds.log for details"
            )
          );
        } else {
          errors.addError(
            "errorFetchResults",
            SimpleErrorMessages.getInstance().getFriendlyErrorMessage(
              ex, "Please check the connection parameters. See the teamcity-clouds.log for details")
          );
        }
        writeErrors(xmlResponse, errors);
      }
    });
  }

  private Element getVirtualMachinesAsElement(@NotNull final List<VmwareInstance> instances){
    final Element element = new Element("VirtualMachines");
    for (VmwareInstance vm : instances) {
      Element vmElement = new Element("VirtualMachine");
      vmElement.setAttribute("name", vm.getName());
      vmElement.setAttribute("template", String.valueOf(vm.isReadonly()));
      vmElement.setAttribute("datacenterId", vm.getDatacenterId());
      element.addContent(vmElement);
    }
    return element;
  }

  private Element getFoldersAsElement(List<FolderBean> folders){
    final Element element = new Element("Folders");
    for (FolderBean folder : folders) {
      Element folderElement = new Element("Folder");
      folderElement.setAttribute("name", folder.getPath());
      folderElement.setAttribute("value", folder.getId());
      folderElement.setAttribute("datacenterId", folder.getDatacenterId());
      element.addContent(folderElement);
    }
    return element;
  }

  private Element getResourcePoolsAsElement(List<ResourcePoolBean> resourcePools){
    final Element element = new Element("ResourcePools");
    final Element defaultPoolElem = new Element("ResourcePool");
    defaultPoolElem.setAttribute("name", "<Default>");
    defaultPoolElem.setAttribute("value", VmwareConstants.DEFAULT_RESOURCE_POOL);
    element.addContent(defaultPoolElem);
    for (ResourcePoolBean pool : resourcePools) {
      Element poolElement = new Element("ResourcePool");
      poolElement.setAttribute("name", pool.getPath());
      poolElement.setAttribute("value", pool.getId());
      poolElement.setAttribute("datacenterId", pool.getDatacenterId());
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

  private class VmwareEditProfilePermissionChecker implements RequestPermissionsCheckerEx{

    @Override
    public void checkPermissions(@NotNull SecurityContextEx securityContext, @NotNull HttpServletRequest request)
      throws AccessDeniedException{
      String projectId = request.getParameter("projectId");
      if (StringUtil.isEmpty(projectId)) {
        throw new AccessDeniedException(securityContext.getAuthorityHolder(), "Missing projectId");
      }
      SProject project = myProjectManager.findProjectByExternalId(projectId);
      if (project == null) {
        throw new AccessDeniedException(securityContext.getAuthorityHolder(), "Invalid projectId");
      }
      securityContext.getAccessChecker().checkHasPermissionsForProject(project.getProjectId(), Permission.MANAGE_AGENT_CLOUDS);
    }
  }
}
