package jetbrains.buildServer.clouds.vmware.connector.beans;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.ResourcePool;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.vmware.connector.VmwareManagedEntity;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by sergeypak on 02/02/2017.
 */
public class ResourcePoolBean implements VmwareManagedEntity, Comparable<ResourcePoolBean> {
  private final String myName;
  private final ManagedObjectReference myMOR;
  private final String myPath;
  private final ManagedObjectReference myParentRef;
  private final String myDatacenterId;


  @Used("Tests")
  public ResourcePoolBean(final ResourcePool rp){
    this(rp.getMOR(), rp.getName(), null, rp.getParent().getMOR(), "dc");
  }

  public ResourcePoolBean(@NotNull final ManagedObjectReference mor,
                          @NotNull final String name,
                          @NotNull final String path,
                          @Nullable final ManagedObjectReference parentRef,
                          @Nullable final String datacenterId) {
    myName = name;
    myMOR = mor;
    myPath = path;
    myParentRef = parentRef;
    myDatacenterId = datacenterId;
  }

  @NotNull
  @Override
  public String getId() {
    return myMOR.getVal();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getPath() {
    return myPath;
  }

  @Nullable
  @Override
  public String getDatacenterId() {
    return myDatacenterId;
  }

  @NotNull
  @Override
  public ManagedObjectReference getMOR() {
    return myMOR;
  }

  @Nullable
  @Override
  public ManagedObjectReference getParentMOR() {
    return myParentRef;
  }

  @Override
  public int compareTo(@NotNull final ResourcePoolBean o) {
    if (myPath == null && o.myPath == null){
      return StringUtil.compare(StringUtil.toLowerCase(myName), StringUtil.toLowerCase(o.myName));
    }
    return StringUtil.compare(StringUtil.toLowerCase(myPath), StringUtil.toLowerCase(o.myPath));
  }
}
