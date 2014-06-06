package jetbrains.buildServer.clouds.vmware.web;

import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
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
      Element element = new Element("Snapshots");
      element.setAttribute("vmName", imageName);
      for (String snapshotName : snapshotList.keySet()) {
        Element vmElement = new Element("Snapshot");
        vmElement.setAttribute("name", snapshotName);
        element.addContent(vmElement);
      }
      xmlResponse.addContent(element);

    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (RemoteException e) {
      e.printStackTrace();
    }

  }
}
