

package jetbrains.buildServer.clouds.base;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import jetbrains.buildServer.clouds.base.errors.SimpleErrorMessages;
import jetbrains.buildServer.log.LogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudInstance<T extends AbstractCloudImage> implements CloudInstance, UpdatableCloudErrorProvider {
  private static final Logger LOG = Logger.getInstance(AbstractCloudInstance.class.getName());
  private static final AtomicLong STARTING_INSTANCE_IDX = new AtomicLong(System.currentTimeMillis());

  private final UpdatableCloudErrorProvider myErrorProvider;
  private final AtomicReference<InstanceStatus> myStatus = new AtomicReference<>(InstanceStatus.UNKNOWN);

  @NotNull
  private final T myImage;
  private final AtomicReference<Date> myStartDate = new AtomicReference<Date>(new Date());
  private final AtomicReference<Date> myStatusUpdateTime = new AtomicReference<>(new Date());
  private final AtomicReference<String> myNetworkIdentify = new AtomicReference<String>();

  private final AtomicReference<String> myNameRef = new AtomicReference<>();
  private final AtomicReference<String> myInstanceIdRef = new AtomicReference<>();

  protected AbstractCloudInstance(@NotNull final T image) {
    this(image, "Initializing...", String.format("%s-temp-%d", image.getName(), STARTING_INSTANCE_IDX.incrementAndGet()));
  }

  protected AbstractCloudInstance(@NotNull final T image, @NotNull final String name, @NotNull final String instanceId) {
    myImage = image;
    myNameRef.set(name);
    myInstanceIdRef.set(instanceId);
    myErrorProvider = new CloudErrorMap(SimpleErrorMessages.getInstance());
  }

  public void setName(@NotNull final String name) {
    myNameRef.set(name);
  }

  public void setInstanceId(@NotNull final String instanceId) {
    final String oldInstanceId = myInstanceIdRef.get();
    myInstanceIdRef.set(instanceId);
    myImage.addInstance(this);
    myImage.removeInstance(oldInstanceId);
  }

  @NotNull
  public String getName() {
    return myNameRef.get();
  }

  @NotNull
  public String getInstanceId() {
    return myInstanceIdRef.get();
  }


  public void updateErrors(TypedCloudErrorInfo... errors) {
    myErrorProvider.updateErrors(errors);
  }

  @NotNull
  public T getImage() {
    return myImage;
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus.get();
  }

  public void setStatus(@NotNull final InstanceStatus status) {
    if (myStatus.get() == status){
      return;
    }
    LOG.info(String.format("Changing %s(%x) status from %s to %s ", getName(), hashCode(), myStatus, status));
    myStatus.set(status);
    myStatusUpdateTime.set(new Date());
  }

  @NotNull
  public Date getStartedTime() {
    return myStartDate.get();
  }

  public void setStartDate(@NotNull final Date startDate) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Setting start date to %s from %s", LogUtil.describe(startDate), LogUtil.describe(myStartDate.get())));
    }
    myStartDate.set(startDate);
  }

  @NotNull
  public Date getStatusUpdateTime() {
    return myStatusUpdateTime.get();
  }

  public void setNetworkIdentify(@NotNull final String networkIdentify) {
    myNetworkIdentify.set(networkIdentify);
  }

  @Nullable
  public String getNetworkIdentity() {
    return myNetworkIdentify.get();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() +"{" +"myName='" + getInstanceId() + '\'' +'}';
  }
}