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

package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.DummyApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 27/10/2016.
 */
public class VmwareUpdateInstanceTask
  extends UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient>
  implements VMWareCloudClient.DisposeHandler {

  private static final Logger LOG = Logger.getInstance(VmwareUpdateInstanceTask.class.getName());


  @NotNull private final VMWareCloudClient myClient;
  @NotNull private final VmwarePooledUpdateInstanceTask myPoolTask;

  VmwareUpdateInstanceTask(@NotNull final String key,
                           @NotNull final VMWareCloudClient client,
                           @NotNull final VmwarePooledUpdateInstanceTask poolTask) {
    super(new DummyApiConnector(key), client);
    myClient = client;
    myPoolTask = poolTask;
  }

  void register(){
    myPoolTask.addClient(myClient);
    myClient.addDisposeHandler(this);
  }

  @Override
  public void run() {
    LOG.debug("Run inside...");
    myPoolTask.runIfNecessary(myClient);
  }

  @Override
  public void clientDisposing(@NotNull final VMWareCloudClient client) {
    myPoolTask.removeClient(myClient);
  }
}
