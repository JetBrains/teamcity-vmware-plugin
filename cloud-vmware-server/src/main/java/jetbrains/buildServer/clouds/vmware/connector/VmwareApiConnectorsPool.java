

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