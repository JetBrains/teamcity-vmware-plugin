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

  public static final String SECURE_PASSWORD = "secure:"+PASSWORD;

  public static final String IMAGES_DATA="vmware_images_data";


  public String getServerUrl() {
    return SERVER_URL;
  }

  public String getUsername() {
    return USERNAME;
  }

  public String getPassword() {
    return PASSWORD;
  }

  public String getImagesData() {
    return IMAGES_DATA;
  }
}
