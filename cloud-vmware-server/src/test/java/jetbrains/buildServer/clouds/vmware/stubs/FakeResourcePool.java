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
