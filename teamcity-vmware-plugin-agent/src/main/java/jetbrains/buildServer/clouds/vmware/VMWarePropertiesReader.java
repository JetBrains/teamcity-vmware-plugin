package jetbrains.buildServer.clouds.vmware;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.util.StringUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.*;

/**
 * @author Sergey.Pak
 *         Date: 4/23/2014
 *         Time: 6:40 PM
 */
public class VMWarePropertiesReader {

  private static final Logger LOG = Logger.getInstance(VMWarePropertiesReader.class.getName());

  private static final String WINDOWS_COMMAND = "C:\\Program Files\\VMware\\VMware Tools\\rpctool.exe";
  private static final String LINUX_COMMAND="/usr/sbin/vmware-rpctool";
  private static final String MAC_COMMAND="/usr/sbin/vmware-rpctool";

  private static final String VMWARE_RPCTOOL_PATH;

  private final BuildAgentConfigurationEx myAgentConfiguration;


  public VMWarePropertiesReader(final BuildAgentConfigurationEx agentConfiguration,
                                @NotNull EventDispatcher<AgentLifeCycleListener> events) {
    LOG.info("VSphere plugin initializing...");
    myAgentConfiguration = agentConfiguration;
    events.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void afterAgentConfigurationLoaded(@NotNull final BuildAgent agent) {
        if (!checkVmwareToolsInstalled()){
          LOG.info("Unable to locate " + VMWARE_RPCTOOL_PATH + ". Looks like not a VMWare VM or VWWare tools are not installed");
          return;
        } else {
          LOG.info("VMWare tools installed. Will attempt to authorize agent as VMWare cloud agent");
        }

        final String serverUrl = getPropertyValue(SERVER_URL);
        if (StringUtil.isEmpty(serverUrl)){
          LOG.info("Unable to read property " + SERVER_URL + ". VMWare integration is disabled");
          return;
        } else {
          LOG.info("Server URL: " + serverUrl);
        }
        final String instanceName = getPropertyValue(INSTANCE_NAME);
        if (StringUtil.isEmpty(instanceName)){
          LOG.info("Unable to read property " + INSTANCE_NAME + ". VMWare integration is disabled");
          return;
        } else {
          LOG.info("Instance name: " + instanceName);
        }

        myAgentConfiguration.setName(instanceName);
        myAgentConfiguration.setServerUrl(serverUrl);
        myAgentConfiguration.addConfigurationParameter(INSTANCE_NAME, instanceName);

        String imageName = getPropertyValue(IMAGE_NAME);
        if (!StringUtil.isEmpty(imageName)){
          LOG.info("Image name: " + imageName);
          myAgentConfiguration.addConfigurationParameter(IMAGE_NAME, imageName);
        }

        String userData = getPropertyValue(USER_DATA);
        if (!StringUtil.isEmpty(userData)){
          LOG.info("UserData: " + userData);
          final CloudInstanceUserData cloudUserData = CloudInstanceUserData.deserialize(userData);
          if (cloudUserData != null) {
            final Map<String, String> customParameters = cloudUserData.getCustomAgentConfigurationParameters();
            for (Map.Entry<String, String> entry : customParameters.entrySet()) {
              myAgentConfiguration.addConfigurationParameter(entry.getKey(), entry.getValue());
            }
          }
        }
      }
    });
  }

  private String getPropertyValue(String propName){
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(VMWARE_RPCTOOL_PATH);
    final String param = String.format("info-get %s", propName);
    commandLine.addParameter(param);
    try {
      LOG.info(Arrays.toString(new String[]{VMWARE_RPCTOOL_PATH, param}));
      final Process exec = Runtime.getRuntime().exec(new String[]{VMWARE_RPCTOOL_PATH, param});
      exec.waitFor();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    final ExecResult execResult = SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0]);
    return StringUtil.trim(execResult.getStdout());
  }

  private static boolean checkVmwareToolsInstalled(){
    return new File(VMWARE_RPCTOOL_PATH).exists();
  }

  static {
    if (SystemInfo.isLinux){
      VMWARE_RPCTOOL_PATH = LINUX_COMMAND;
    } else if (SystemInfo.isWindows){
      VMWARE_RPCTOOL_PATH = WINDOWS_COMMAND;
    } else {
      VMWARE_RPCTOOL_PATH = LINUX_COMMAND; //todo: update for other OS'es
    }
  }
}
