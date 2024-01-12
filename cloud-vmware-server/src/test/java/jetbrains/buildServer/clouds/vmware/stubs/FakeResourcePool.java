

package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServerConnection;

/**
 * @author Sergey.Pak
 *         Date: 2/10/2015
 *         Time: 3:00 PM
 */
public class FakeResourcePool extends ResourcePool {

  private final String myName;
  private ManagedEntity myParent;

  public FakeResourcePool(String name) {
    super(null, createMor(name));
    myName = name;
    myParent = null;
  }


  public void setParentFolder(String folderName){
    final FakeFolder folder = FakeModel.instance().getFolder(folderName);
    myParent = folder;
  }

  @Override
  public ManagedEntity getParent() {
    return myParent;
  }


  private static ManagedObjectReference createMor(final String name){
    return new ManagedObjectReference(){
      @Override
      public String getVal() {
        return "resgroup-v" + name.hashCode();
      }

      @Override
      public String get_value() {
        return getVal();
      }

      @Override
      public String getType() {
        return "ResourcePool";
      }
    };
  }

  @Override
  public int[] getEffectiveRole() {
    return null;
  }
}