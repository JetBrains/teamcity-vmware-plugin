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

package jetbrains.buildServer.clouds.vmware.connector;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jetbrains.buildServer.clouds.server.CloudInstancesProvider;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by sergeypak on 26/10/2016.
 */
public class VmwareApiConnectorsPool {

  private static final ConcurrentMap<String, VMWareApiConnector> myConnectors =
    new ConcurrentHashMap<>();


  public static VMWareApiConnector getOrCreateConnector(@NotNull final URL instanceURL,
                                                        @NotNull final String username,
                                                        @NotNull final String password,
                                                        @Nullable final String serverUUID,
                                                        @Nullable final String profileId,
                                                        @Nullable final CloudInstancesProvider instancesProvider,
                                                        @Nullable final SSLTrustStoreProvider trustStoreProvider){
    if (serverUUID == null || profileId == null){ // this is just for fetching data
      return new VMWareApiConnectorImpl(
        instanceURL,
        username,
        password,
        serverUUID,
        profileId,
        instancesProvider,
        trustStoreProvider
      );
    }

    final String key = VMWareApiConnectorImpl.getKey(instanceURL, username, password);
    return myConnectors.computeIfAbsent(key, k->new VMWareApiConnectorImpl(
      instanceURL,
      username,
      password,
      serverUUID,
      profileId,
      instancesProvider,
      trustStoreProvider));
  }
}
