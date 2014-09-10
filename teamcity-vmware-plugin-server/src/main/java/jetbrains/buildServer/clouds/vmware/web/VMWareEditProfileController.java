package jetbrains.buildServer.clouds.vmware.web;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.*;
import org.apache.commons.lang.ObjectUtils;
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
    private final PluginDescriptor myPluginDescriptor;

  public VMWareEditProfileController(@NotNull final SBuildServer server,
                                      @NotNull final PluginDescriptor pluginDescriptor,
                                     @NotNull final WebControllerManager manager) {
    super(server);
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("vmware-settings.html");
    myPluginDescriptor = pluginDescriptor;
    myJspPath = myPluginDescriptor.getPluginResourcesPath("vmware-settings.jsp");
    mySnapshotsPath = pluginDescriptor.getPluginResourcesPath("vmware-getsnapshotlist.html");
    manager.registerController(myHtmlPath, this);
    manager.registerController(pluginDescriptor.getPluginResourcesPath("vmware-getsnapshotlist.html"), new GetSnapshotsListController());
  }

  @Override
  protected ModelAndView doGet(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) {
    ModelAndView mv = new ModelAndView(myJspPath);
    mv.getModel().put("refreshablePath", myHtmlPath);
    mv.getModel().put("refreshSnapshotsPath", mySnapshotsPath);
    mv.getModel().put("resPath", myPluginDescriptor.getPluginResourcesPath());
    return mv;
  }

  @Override
  protected void doPost(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response, @NotNull final Element xmlResponse) {
    ActionErrors errors = new ActionErrors();

    final BasePropertiesBean propsBean = new BasePropertiesBean(null);
    PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

    final Map<String, String> props = propsBean.getProperties();
    final String serverUrl = props.get(VMWareWebConstants.SERVER_URL);
    final String username = props.get(VMWareWebConstants.USERNAME);
    final String password = props.get(VMWareWebConstants.SECURE_PASSWORD);

    try {
      final VMWareApiConnector myApiConnector = new VMWareApiConnectorImpl(new URL(serverUrl), username, password);
      xmlResponse.addContent(getVirtualMachinesAsElement(myApiConnector.getVirtualMachines(true)));
      xmlResponse.addContent(getFoldersAsElement(myApiConnector.getFolders()));
      xmlResponse.addContent(getResourcePoolsAsElement(myApiConnector.getResourcePools()));
    } catch (Exception ex) {
      LOG.warn("Unable to get vCenter details: " + ex.toString());
      errors.addError("errorFetchResults", ex.toString());
      writeErrors(xmlResponse, errors);
    }
  }

  private Element getVirtualMachinesAsElement(@NotNull final Map<String, VmwareInstance> vmMap){
    Element element = new Element("VirtualMachines");
    final List<VmwareInstance> values = new ArrayList<VmwareInstance>(vmMap.values());
    Collections.sort(values, new Comparator<VmwareInstance>() {
      public int compare(@NotNull final VmwareInstance o1, @NotNull final VmwareInstance o2) {
        return StringUtil.compare(o1.getName(), o2.getName());
      }
    });
    for (VmwareInstance vm : values) {
      Element vmElement = new Element("VirtualMachine");
      vmElement.setAttribute("name", vm.getName());
      vmElement.setAttribute("template", String.valueOf(vm.isReadonly()));
      element.addContent(vmElement);
    }
    return element;
  }

  private Element getFoldersAsElement(Map<String, Folder> folders){
    Element element = new Element("Folders");
    for (String folderName : folders.keySet()) {
      Element folderElement = new Element("Folder");
      folderElement.setAttribute("name", folderName);
      element.addContent(folderElement);
    }
    return element;
  }

  private Element getResourcePoolsAsElement(Map<String, ResourcePool> resourcePools){
    Element element = new Element("ResourcePools");
    for (String poolName : resourcePools.keySet()) {
      Element poolElement = new Element("ResourcePool");
      poolElement.setAttribute("name", poolName);
      element.addContent(poolElement);
    }
    return element;
  }

}
