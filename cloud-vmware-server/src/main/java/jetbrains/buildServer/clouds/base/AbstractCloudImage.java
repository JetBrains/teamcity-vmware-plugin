

package jetbrains.buildServer.clouds.base;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jetbrains.buildServer.clouds.CanStartNewInstanceResult;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import jetbrains.buildServer.clouds.base.errors.SimpleErrorMessages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:50 PM
 */
public abstract class AbstractCloudImage<T extends AbstractCloudInstance, G extends CloudImageDetails> implements CloudImage, UpdatableCloudErrorProvider {
  protected final UpdatableCloudErrorProvider myErrorProvider = new CloudErrorMap(SimpleErrorMessages.getInstance());
  private final Map<String, T> myInstances = new ConcurrentHashMap<String, T>();
  private final String myName;
  private final String myId;

  protected AbstractCloudImage(String name, String id) {
    myName = name;
    myId = id;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void updateErrors(TypedCloudErrorInfo... errors) {
    myErrorProvider.updateErrors(errors);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }

  @NotNull
  public Collection<T> getInstances() {
    return Collections.unmodifiableCollection(myInstances.values());
  }

  @Nullable
  public T findInstanceById(@NotNull final String instanceId) {
    return myInstances.get(instanceId);
  }

  public void removeInstance(@NotNull final String instanceId){
    myInstances.remove(instanceId);
  }

  public void addInstance(@NotNull final T instance){
    myInstances.put(instance.getInstanceId(), instance);
  }

  public boolean canStartNewInstance(){
    return canStartNewInstanceWithDetails().isPositive();
  }

  @NotNull
  public abstract CanStartNewInstanceResult canStartNewInstanceWithDetails();

  public abstract void terminateInstance(@NotNull final T instance);

  public abstract void restartInstance(@NotNull final T instance);

  public abstract T startNewInstance(@NotNull final CloudInstanceUserData tag);

  public abstract G getImageDetails();

  protected abstract T createInstanceFromReal(final AbstractInstance realInstance);

  public void detectNewInstances(final Map<String,? extends AbstractInstance> realInstances){
    for (String instanceName : realInstances.keySet()) {
      if (myInstances.get(instanceName) == null) {
        final AbstractInstance realInstance = realInstances.get(instanceName);
        final T newInstance = createInstanceFromReal(realInstance);
        newInstance.setStatus(realInstance.getInstanceStatus());
        addInstance(newInstance);
      }
    }

  }

  protected Set<String> getInstanceIds(){
    return Collections.unmodifiableSet(myInstances.keySet());
  }

  public String toString() {
    return getClass().getSimpleName() +"{" +"myName='" + getId() + '\'' +'}';
  }
}