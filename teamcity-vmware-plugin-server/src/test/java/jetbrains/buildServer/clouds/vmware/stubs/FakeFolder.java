/*
 *
 *  * Copyright 2000-2015 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.ManagedEntity;
import junit.framework.Assert;

/**
 * @author Sergey.Pak
 *         Date: 2/10/2015
 *         Time: 2:01 PM
 */
public class FakeFolder extends Folder {

  private final String myName;
  private ManagedEntity myParent;

  public FakeFolder(String name) {
    this(name, createVMMor(name));
  }

  protected FakeFolder(String name, ManagedObjectReference mor){
    super(null, mor);
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

  public <T extends ManagedEntity> void setParent(final String parentName, Class<T> parentType) {
    if (parentType.isAssignableFrom(FakeDatacenter.class)){
      setParent(FakeModel.instance().getDatacenter(parentName));
    } else if (parentType.isAssignableFrom(Folder.class)){
      setParent(FakeModel.instance().getFolder(parentName));
    }
    if (myParent == null) {
      Assert.fail(String.format("Unable to set parent %s of type %s", parentName, parentType.getSimpleName()));
    }
  }

  public void setParent(final ManagedEntity parent) {
    myParent = parent;
  }

  private static ManagedObjectReference createVMMor(final String name){
    return new ManagedObjectReference(){
      @Override
      public String getVal() {
        return "group-" + name.hashCode();
      }

      @Override
      public String get_value() {
        return getVal();
      }

      @Override
      public String getType() {
        return "Folder";
      }
    };
  }
}
