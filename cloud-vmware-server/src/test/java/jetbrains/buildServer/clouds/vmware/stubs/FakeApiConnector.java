package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.mo.*;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.*;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnectorImpl;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 2:40 PM
 */
public class FakeApiConnector extends VMWareApiConnectorImpl {

  public FakeApiConnector() {
    super(null, null, null);
  }

  @Override
  public void test() throws VmwareCheckedCloudException {

  }

  @Override
  protected <T extends ManagedEntity> T findEntityByIdNameNullable(final String name, final Class<T> instanceType, Datacenter dc) throws VmwareCheckedCloudException {
    test();
    final T t;
    if (instanceType == Folder.class){
      t = (T)FakeModel.instance().getFolder(name);
    } else if (instanceType == ResourcePool.class){
      t =  (T)FakeModel.instance().getResourcePool(name);
    } else if (instanceType == VirtualMachine.class){
      t =  (T)FakeModel.instance().getVirtualMachine(name);
    } else {
      throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
    }
    return  (dc == null || dc == getParentDC(t)) ? t : null;
  }

  @Override
  protected <T extends ManagedEntity> Collection<T> findAllEntities(final Class<T> instanceType) throws VmwareCheckedCloudException {
    test();
    if (instanceType == Folder.class){
      return (Collection<T>)FakeModel.instance().getFolders().values();
    } else if (instanceType == ResourcePool.class){
      return (Collection<T>)FakeModel.instance().getResourcePools().values();
    } else if (instanceType == VirtualMachine.class){
      return (Collection<T>)FakeModel.instance().getVms().values();
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  @Override
  protected <T extends ManagedEntity> Map<String, T> findAllEntitiesAsMap(final Class<T> instanceType) throws VmwareCheckedCloudException {
    test();
    if (instanceType == Folder.class){
      return (Map<String, T>)FakeModel.instance().getFolders();
    } else if (instanceType == ResourcePool.class){
      return (Map<String, T>)FakeModel.instance().getResourcePools();
    } else if (instanceType == VirtualMachine.class){
      return (Map<String, T>)FakeModel.instance().getVms();
    }
    throw new IllegalArgumentException("Unknown entity type: " + instanceType.getCanonicalName());
  }

  private static FakeDatacenter getParentDC(ManagedEntity me){
    while (!(me ==null || me instanceof Datacenter)){
      me = me.getParent();
    }
    return (FakeDatacenter) me;
  }
}
