package jetbrains.buildServer.clouds.vmware.web;

import com.intellij.openapi.diagnostic.Logger;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.clouds.vmware.VmwareConstants;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareApiConnectorsPool;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by sergeypak on 23/05/2017.
 */
public class ConfigurationHelperController extends BaseFormXmlController {

  private static final Logger LOG = Logger.getInstance(ConfigurationHelperController.class.getName());

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
    final String fieldId = props.get("helperFieldId");
    final String fieldValue = props.get("helperFieldValue");
    if (StringUtil.isEmpty(fieldId) || StringUtil.isEmpty(fieldValue))
      return;

    try {
      final VMWareApiConnector myApiConnector = VmwareApiConnectorsPool.getOrCreateConnector(new URL(serverUrl), username, password, null, null, null);

      switch (fieldId){
        case "respool":
          final Element canAddPoolElement = new Element("fieldValid");
          final boolean canAddVM2Pool = myApiConnector.isCanAddVM2Pool(fieldValue);
          canAddPoolElement.setText(String.valueOf(canAddVM2Pool));
          xmlResponse.addContent((Content)canAddPoolElement);
          if (!canAddVM2Pool){
            final Element errorCodeElement = new Element("errorCode");
            errorCodeElement.setText("noAccessPool");
            xmlResponse.addContent((Content)errorCodeElement);
          }
          break;
        default:
          // do nothing
          break;
      }
    } catch (VmwareCheckedCloudException e) {
      LOG.warn("Unable to get snapshot list: " + e.toString());
      LOG.debug("Unable to get snapshot list: " + e.toString(), e);
    } catch (MalformedURLException e) {
      LOG.warn("Unable to get snapshot list: " + e.toString());
    }

  }
}
