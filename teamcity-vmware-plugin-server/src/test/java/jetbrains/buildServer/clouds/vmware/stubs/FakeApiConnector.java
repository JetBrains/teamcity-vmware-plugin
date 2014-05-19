package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.vmware.VMWareCloudImage;
import jetbrains.buildServer.clouds.vmware.VMWareCloudInstance;
import jetbrains.buildServer.clouds.vmware.VMWareImageStartType;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 2:40 PM
 */
public class FakeApiConnector extends VMWareApiConnectorImpl {

  public FakeApiConnector() throws MalformedURLException, RemoteException {
    super(null, null, null);
  }

  @Override
  protected <T extends ManagedEntity> T findEntityByName(final String name, final Class<T> instanceType) throws RemoteException {
    if (instanceType == Folder.class){
      return (T)FakeModel.instance().getFolder(name);
    } else if (instanceType == ResourcePool.class){
      return (T)FakeModel.instance().getResourcePool(name);
    } else if (instanceType == VirtualMachine.class){
      return (T)FakeModel.instance().getVirtualMachine(name);
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  @Override
  protected <T extends ManagedEntity> Collection<T> findAllEntities(final Class<T> instanceType) throws RemoteException {
    if (instanceType == Folder.class){
      return (Collection<T>)FakeModel.instance().getFolders().values();
    } else if (instanceType == ResourcePool.class){
      return (Collection<T>)FakeModel.instance().getResourcePools().values();
    } else if (instanceType == VirtualMachine.class){
      return (Collection<T>)FakeModel.instance().getVms().values();
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }
}
