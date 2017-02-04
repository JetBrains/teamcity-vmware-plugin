package jetbrains.buildServer.clouds.vmware.connector.beans;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.ResourcePool;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.vmware.connector.VmwareManagedEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by sergeypak on 02/02/2017.
 */
public class ResourcePoolBean implements VmwareManagedEntity {
  private final String myName;
  private final String myId;
  private final ManagedObjectReference myParentRef;
  private final String myDatacenterId;


  @Used("Tests")
  public ResourcePoolBean(final ResourcePool rp){
    this(rp.getMOR().getVal(), rp.getName(), rp.getParent().getMOR(), "dc");
  }

  public ResourcePoolBean(final String id,
                          final String name,
                          final ManagedObjectReference parentRef,
                          final String datacenterId) {
    myName = name;
    myId = id;
    myParentRef = parentRef;
    myDatacenterId = datacenterId;
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getDatacenterId() {
    return myDatacenterId;
  }

  public ManagedObjectReference getParentRef() {
    return myParentRef;
  }

}
