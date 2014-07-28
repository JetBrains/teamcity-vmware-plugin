package jetbrains.buildServer.clouds.base.connector;

import java.util.Date;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/25/2014
 *         Time: 5:17 PM
 */
public class AbstractInstance {
  private final String myName;
  private Date myStartDate;
  private String myIpAddress;

  @Nullable
  private final Map<String, String> myProperties;


  public AbstractInstance(@NotNull final String name, @Nullable final Map<String, String> properties) {
    myName = name;
    myProperties = properties;
  }

  public String getName() {
    return myName;
  }

  public Date getStartDate() {
    return myStartDate;
  }

  public String getIpAddress() {
    return myIpAddress;
  }

  public boolean isInitialized(){
    return myProperties != null;
  }

  @Nullable
  public Map<String, String> getProperties() {
    return myProperties;
  }
}
