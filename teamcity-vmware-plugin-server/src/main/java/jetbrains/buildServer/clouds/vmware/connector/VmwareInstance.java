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

package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.OptionValue;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Callable;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.AsyncCloudTask;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/25/2014
 *         Time: 6:45 PM
 */
public class VmwareInstance extends AbstractInstance {

  @NotNull private final VirtualMachine myVm;
  private final Map<String, String> myProperties;

  public VmwareInstance(@NotNull final VirtualMachine vm) {
    super(vm.getName());
    myVm = vm;
    myProperties = extractProperties(myVm);
  }


  @Nullable
  private static Map<String, String> extractProperties(@NotNull final VirtualMachine vm) {
    if (vm.getConfig() == null) {
      return null;
    }

    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    Map<String, String> retval = new HashMap<String, String>();
    for (OptionValue optionValue : extraConfig) {
      retval.put(optionValue.getKey(), String.valueOf(optionValue.getValue()));
    }
    return retval;
  }

  public boolean isPoweredOn() {
    final VirtualMachineRuntimeInfo runtime = myVm.getRuntime();
    if (runtime == null) {
      return false;
    }
    return runtime.getPowerState() == VirtualMachinePowerState.poweredOn;
  }

  @Override
  public boolean isInitialized() {
    return myProperties != null;
  }

  @Nullable
  public String getProperty(@NotNull final String propertyName) {
    return myProperties == null ? null : myProperties.get(propertyName);
  }

  @Nullable
  public Date getStartDate() {
    final VirtualMachineRuntimeInfo runtime = myVm.getRuntime();
    if (runtime == null) {
      return null;
    }
    final Calendar bootTime = runtime.getBootTime();
    return bootTime == null ? null : bootTime.getTime();
  }

  @Nullable
  public String getIpAddress() {
    return myVm.getGuest() == null ? null : myVm.getGuest().getIpAddress();
  }

  @Override
  public InstanceStatus getInstanceStatus() {
    if (myVm.getRuntime() == null || myVm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff) {
      return InstanceStatus.STOPPED;
    }
    if (myVm.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn) {
      return InstanceStatus.RUNNING;
    }
    return InstanceStatus.UNKNOWN;
  }

  public boolean isReadonly() {
    if (myVm.getConfig() == null) {
      return true;
    }

    return myVm.getConfig().isTemplate();
  }

  @Nullable
  public String getChangeVersion() {
    final VirtualMachineConfigInfo config = myVm.getConfig();
    return config == null ? null : config.getChangeVersion();
  }

  public AsyncCloudTask deleteInstance() throws RemoteException {
    return new VmwareTaskWrapper(new Callable<Task>() {
      public Task call() throws Exception {
        return myVm.destroy_Task();
      }
    });
  }

  public String getSnapshotName(){
    return StringUtil.nullIfEmpty(getProperty(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT));
  }
}
