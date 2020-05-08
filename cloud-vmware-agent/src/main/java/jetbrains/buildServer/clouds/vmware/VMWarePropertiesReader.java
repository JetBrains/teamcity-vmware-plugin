/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.CommandLineExecutor;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.BuildAgentImpl;
import jetbrains.buildServer.agent.impl.config.BuildAgentConfigurationImpl;
import jetbrains.buildServer.agent.impl.config.BuildAgentConfigurationPersister;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.clouds.vmware.VMWarePropertiesNames.*;

/**
 * @author Sergey.Pak
 *         Date: 4/23/2014
 *         Time: 6:40 PM
 */
public class VMWarePropertiesReader {

  private static final Logger LOG = Logger.getInstance(VMWarePropertiesReader.class.getName());
  private static final String RPC_TOOL_PARAMETER = "teamcity.vmware.rpctool.path";

  private static final String[] WINDOWS_COMMANDS = {"C:\\Program Files\\VMware\\VMware Tools\\rpctool.exe"};
  private static final String[] LINUX_COMMANDS = {"/usr/sbin/vmware-rpctool", "/usr/bin/vmware-rpctool"};
  private static final String[] MAC_COMMANDS = {"/usr/sbin/vmware-rpctool", "/sbin/rpctool", "/Library/Application Support/VMware Tools/vmware-tools-daemon"};

  private static final String VMWARE_RPCTOOL_NAME = "vmware-rpctool";
  private final String myVMWareRPCToolPath;

  private final BuildAgentConfigurationEx myAgentConfiguration;
  private static final Map<String, String> SPECIAL_COMMAND_FORMATS = Collections.unmodifiableMap(new HashMap<String, String>(){{
    put("/Library/Application Support/VMware Tools/vmware-tools-daemon", "--cmd='info-get %s'");
  }});


  public VMWarePropertiesReader(final BuildAgentConfigurationEx agentConfiguration,
                                @NotNull EventDispatcher<AgentLifeCycleListener> events) {
    LOG.info("VSphere plugin initializing...");
    myAgentConfiguration = agentConfiguration;
    myVMWareRPCToolPath = getToolPath(myAgentConfiguration);
    if (myVMWareRPCToolPath == null) {
      LOG.info("Unable to locate " + VMWARE_RPCTOOL_NAME + ". Looks like not a VMWare VM or VWWare tools are not installed");
      return;
    } else {
      LOG.info("Detected vmware-tools or open-vm-tools. Found required vmware-rpctool at " + myVMWareRPCToolPath + ". " +
          "Will attempt to authorize agent as VMWare cloud agent. ");
    }
    events.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void afterAgentConfigurationLoaded(@NotNull final BuildAgent agent) {
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
          LOG.debug("UserData: " + userData);
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
    if (myVMWareRPCToolPath.contains(" ") && SystemInfo.isMac){
      commandLine.setExePath("/bin/sh");
      commandLine.addParameter("-c");
      final String specialCommand = SPECIAL_COMMAND_FORMATS.get(myVMWareRPCToolPath);
      final String param = String.format(specialCommand != null ? specialCommand : "info-get %s", propName);
      commandLine.addParameter(String.format("\"%s\" %s", myVMWareRPCToolPath, param));
    } else {
      commandLine.setExePath(myVMWareRPCToolPath);
      final String specialCommand = SPECIAL_COMMAND_FORMATS.get(myVMWareRPCToolPath);
      final String param = String.format(specialCommand != null ? specialCommand : "info-get %s", propName);
      commandLine.addParameter(param);
    }
    final CommandLineExecutor executor = new CommandLineExecutor(commandLine);
    try {
      final int executionTimeoutSeconds = TeamCityProperties.getInteger("teamcity.vsphere.properties.readTimeout.sec", 60);
      final ExecResult result = executor.runProcess(executionTimeoutSeconds);
      if (result != null) {
        final String executionResult = StringUtil.trim(result.getStdout());
        final StringBuilder commandOutput = new StringBuilder();
        commandOutput.append("Stdout: ").append(executionResult).append('\n');
        commandOutput.append("Stderr: ").append(StringUtil.trim(result.getStderr())).append('\n');
        commandOutput.append("Exit code:").append(result.getExitCode());
        if (result.getExitCode() != 0){
          LOG.debug("Got non-zero exit code for '" + commandLine.toString() + "':\n" + commandOutput.toString());
        }
        return executionResult;
      } else {
        LOG.debug("Didn't get response for " + commandLine.toString() + " in " + executionTimeoutSeconds + " seconds.\n" +
                  "Consider setting agent property 'teamcity.guest.props.read.timeout.sec' to a higher value (default=5)");
      }
    } catch (ExecutionException e) {
      LOG.info("Error getting property " + propName + ": " + e.toString());
    }
    return null;
  }

  @Nullable
  private static String getToolPath(@NotNull final BuildAgentConfiguration configuration) {
    final String rpctoolPath = TeamCityProperties.getProperty(RPC_TOOL_PARAMETER);
    if (StringUtil.isNotEmpty(rpctoolPath)){
      return rpctoolPath;
    }

    if (SystemInfo.isUnix) { // Linux, MacOSX, FreeBSD
      final Map<String, String> envs = configuration.getBuildParameters().getEnvironmentVariables();
      final String path = envs.get("PATH");
      if (path != null) for (String p : StringUtil.splitHonorQuotes(path, File.pathSeparatorChar)) {
        final File file = new File(p, VMWARE_RPCTOOL_NAME);
        if (file.exists()) {
          return file.getAbsolutePath();
        }
      }
    }
    if (SystemInfo.isLinux) {
      return getExistingCommandPath(LINUX_COMMANDS);
    } else if (SystemInfo.isWindows) {
      return getExistingCommandPath(WINDOWS_COMMANDS);
    } else if (SystemInfo.isMac) {
      return getExistingCommandPath(MAC_COMMANDS);
    } else {
      return getExistingCommandPath(LINUX_COMMANDS); //todo: update for other OS'es
    }
  }

  @Nullable
  private static String getExistingCommandPath(String[] fileNames){
    for (String fileName : fileNames) {
      if (new File(fileName).exists())
        return fileName;
    }
    return null;
  }
}
