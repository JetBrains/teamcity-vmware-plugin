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
