package jetbrains.buildServer.clouds.base.stubs;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Sergey.Pak on 3/4/2016.
 */
public class DummyRealInstance extends AbstractInstance {

  @NotNull private final String myDummyImageName;
  private boolean myInitialized;
  private Date myStartDate;
  private String myIpAddress;
  private InstanceStatus myInstanceStatus;
  private final Map<String, String> myProperties = new HashMap<>();

  public DummyRealInstance(@NotNull final String name, @NotNull final String dummyImageName,@NotNull InstanceStatus status) {
    super(name);
    myDummyImageName = dummyImageName;
    myInstanceStatus = status;
  }

  public String getDummyImageName(){
    return myDummyImageName;
  }

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public Date getStartDate() {
    return myStartDate;
  }

  @Override
  public String getIpAddress() {
    return myIpAddress;
  }

  @Override
  public InstanceStatus getInstanceStatus() {
    return myInstanceStatus;
  }

  @Nullable
  @Override
  public String getProperty(final String name) {
    return myProperties.get(name);
  }

  public void setInitialized(final boolean initialized) {
    myInitialized = initialized;
  }

  public void setInstanceStatus(final InstanceStatus instanceStatus) {
    myInstanceStatus = instanceStatus;
  }

  public void setIpAddress(final String ipAddress) {
    myIpAddress = ipAddress;
  }

  public void setStartDate(final Date startDate) {
    myStartDate = startDate;
  }

  public Map<String, String> getProperties() {
    return myProperties;
  }
}
