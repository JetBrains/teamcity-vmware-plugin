

package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.ManagedEntity;

/**
 * @author Sergey.Pak
 *         Date: 2/10/2015
 *         Time: 2:11 PM
 */
public class FakeDatacenter extends Datacenter {

  private final String myName;
  private ManagedEntity myParent;

  public FakeDatacenter(final String name) {
    super(null, createMor(name));
    myName = name;
    myParent = null;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public ManagedEntity getParent() {
    return myParent;
  }

  public void setParent(final String parentFolderName) {
    myParent = FakeModel.instance().getFolder(parentFolderName);
  }

  private static ManagedObjectReference createMor(final String name){
    return new ManagedObjectReference(){
      @Override
      public String getVal() {
        return "datacenter-10";
      }

      @Override
      public String get_value() {
        return getVal();
      }

      @Override
      public String getType() {
        return "Datacenter";
      }
    };
  }
}