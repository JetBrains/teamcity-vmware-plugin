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

package jetbrains.buildServer.clouds.vmware.connector.beans;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.Folder;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.vmware.connector.VmwareManagedEntity;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by sergeypak on 02/02/2017.
 */
public class FolderBean implements VmwareManagedEntity, Comparable<FolderBean> {
  private final String myPath;
  private final String[] myChildType;
  private final String myName;
  private final ManagedObjectReference myMOR;
  private final ManagedObjectReference myParentRef;
  private final String myDatacenterId;

  @Used("Tests")
  public FolderBean(final Folder folder){
    this(folder.getMOR(), folder.getName(), null, folder.getChildType(), folder.getParent().getMOR(), "dc");
  }

  public FolderBean(@NotNull final ManagedObjectReference mor,
                    @NotNull final String name,
                    @NotNull final String path,
                    @NotNull final String[] childType,
                    @Nullable final ManagedObjectReference parentRef,
                    @Nullable final String datacenterId) {
    myPath = path;
    myChildType = childType;
    myName = name;
    myMOR = mor;
    myParentRef = parentRef;
    myDatacenterId = datacenterId;
  }

  public String[] getChildType() {
    return myChildType;
  }

  @NotNull
  @Override
  public String getId() {
    return myMOR.getVal();
  }

  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getPath() {
    return myPath == null ? myName : myPath;
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

  public ManagedObjectReference getParentMOR() {
    return myParentRef;
  }

  @Override
  public int compareTo(@NotNull final FolderBean o) {
    if (myPath == null && o.myPath == null) {
      return StringUtil.compare(StringUtil.toLowerCase(myName), StringUtil.toLowerCase(o.myName));
    }
    return StringUtil.compare(StringUtil.toLowerCase(myPath), StringUtil.toLowerCase(o.myPath));
  }
}
