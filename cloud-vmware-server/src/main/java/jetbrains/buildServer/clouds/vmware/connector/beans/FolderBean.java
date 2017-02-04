package jetbrains.buildServer.clouds.vmware.connector.beans;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.Folder;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.vmware.connector.VmwareManagedEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by sergeypak on 02/02/2017.
 */
public class FolderBean implements VmwareManagedEntity {
  private final String[] myChildType;
  private final String myName;
  private final String myId;
  private final ManagedObjectReference myParentRef;
  private final String myDatacenterId;

  @Used("Tests")
  public FolderBean(final Folder folder){
    this(folder.getMOR().getVal(), folder.getName(), folder.getChildType(), folder.getParent().getMOR(), "dc");
  }

  public FolderBean(final String id,
                    final String name,
                    final String[] childType,
                    final ManagedObjectReference parentRef,
                    final String datacenterId
  ) {
    myChildType = childType;
    myName = name;
    myId = id;
    myParentRef = parentRef;
    myDatacenterId = datacenterId;
  }

  public String[] getChildType() {
    return myChildType;
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
