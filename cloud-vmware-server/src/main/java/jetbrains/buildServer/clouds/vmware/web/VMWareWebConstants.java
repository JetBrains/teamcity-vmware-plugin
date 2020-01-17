/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware.web;

/**
 * @author Sergey.Pak
 *         Date: 5/28/2014
 *         Time: 3:05 PM
 */
public class VMWareWebConstants {

  public static final String SERVER_URL="vmware_server_url";
  public static final String USERNAME="vmware_username";
  public static final String PASSWORD="vmware_password";
  public static final String FORCE_TRUST_MANAGER="force_trust_manager";
  public static final String PROFILE_INSTANCE_LIMIT="vmware_profile_instance_limit";

  public static final String SECURE_PASSWORD = "secure:"+PASSWORD;


  public String getServerUrl() {
    return SERVER_URL;
  }

  public String getUsername() {
    return USERNAME;
  }

  public String getPassword() {
    return PASSWORD;
  }

  public String getForceTrustManager(){
    return FORCE_TRUST_MANAGER;
  }

  public String getProfileInstanceLimit() {
    return PROFILE_INSTANCE_LIMIT;
  }
}
