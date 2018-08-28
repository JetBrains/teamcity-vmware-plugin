/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.vmware.vim25.mo.Task;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.connector.TaskCallbackHandler;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.connector.VmwareInstance;
import jetbrains.buildServer.clouds.vmware.connector.VmwareTaskWrapper;
import jetbrains.buildServer.clouds.vmware.errors.VmwareCheckedCloudException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.IdentifiersGenerator;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:58 PM
 */
public class VmwareCloudImage extends AbstractCloudImage<VmwareCloudInstance, VmwareCloudImageDetails>{

  private static final Logger LOG = Logger.getInstance(VmwareCloudImage.class.getName());

  // consider <Clone;Delete> instances orphaned (won't be deleted), if they were stopped more than 5 minutes ago
  private static final long STOPPED_ORPHANED_TIMEOUT = 5*60*1000l;

  private final VMWareApiConnector myApiConnector;
  @NotNull private final CloudAsyncTaskExecutor myAsyncTaskExecutor;
  private final VmwareCloudImageDetails myImageDetails;
  private final AtomicReference<VmwareSourceState> myActualSourceState;
  private final File myIdxFile;
  private final CloudProfile myProfile;
  private final AtomicInteger myIdxCounter = new AtomicInteger(0);
  private final AtomicBoolean myIdxTouched = new AtomicBoolean(false);

  public VmwareCloudImage(@NotNull final VMWareApiConnector apiConnector,
                          @NotNull final VmwareCloudImageDetails imageDetails,
                          @NotNull final CloudAsyncTaskExecutor asyncTaskExecutor,
                          @NotNull final File idxStorage,
                          @NotNull final CloudProfile profile) {
    super(imageDetails.getSourceId(), imageDetails.getSourceId());
    myImageDetails = imageDetails;
    myApiConnector = apiConnector;
    myAsyncTaskExecutor = asyncTaskExecutor;
    myProfile = profile;
    myActualSourceState = new AtomicReference<>();
    myIdxFile = new File(idxStorage, imageDetails.getSourceId() + ".idx");
    try {
      if (!myIdxFile.exists()) {
        myIdxCounter.set(1);
        myIdxTouched.set(true);
        storeIdx();
      } else {
        myIdxCounter.set(Integer.parseInt(FileUtil.readText(myIdxFile)));
      }
    } catch (Exception e) {
      LOG.warnAndDebugDetails(String.format("Unable to process idx file '%s'. Will reset the index for " + imageDetails.getSourceId(), myIdxFile.getAbsolutePath()), e);
      myIdxCounter.set(1);
    }

    asyncTaskExecutor.scheduleWithFixedDelay("Store idx", ()->{
      storeIdx();
    }, 5000, 5000, TimeUnit.MILLISECONDS);
  }

  @NotNull
  public String getSnapshotName() {
    return myImageDetails.getSnapshotName();
  }

  @Nullable
  private VmwareCloudInstance getExistingInstanceToStart(@NotNull final VmwareSourceState currentSourceState) throws VmwareCheckedCloudException {
    final VmwareInstance imageVm = myApiConnector.getInstanceDetails(myImageDetails.getSourceVmName());
    final AtomicReference<VmwareCloudInstance> candidate = new AtomicReference<VmwareCloudInstance>();
    processStoppedInstances(new Function<VmwareInstance, Boolean>() {
      public Boolean fun(final VmwareInstance vmInstance) {
        final String vmName = vmInstance.getName();
        final VmwareCloudInstance instance = findInstanceById(vmName);

        if (instance != null) {
          if (myImageDetails.useCurrentVersion()) {
            if (imageVm.getChangeVersion() == null || !imageVm.getChangeVersion().equals(vmInstance.getChangeVersion())) {
              LOG.info(String.format("Change version for %s is outdated: '%s' vs '%s'", vmName, vmInstance.getChangeVersion(), imageVm.getChangeVersion()));
              deleteInstance(instance);
              return false;
            }
          } else {
            final VmwareSourceState vmSourceState = vmInstance.getVmSourceState();
            if (!vmSourceState.equals(currentSourceState)){
              LOG.info(String.format("Source for VM %s has been changed: %s", vmName, currentSourceState.getDiffMessage(vmSourceState)));
              deleteInstance(instance);
              return false;
            }
          }
        }

        LOG.info("Will use existing VM with name " + vmName);
        candidate.set(instance);
        return true;
      }
    });
    if (candidate.get() != null){
      return candidate.get();
    }
    return null;
  }


  private VmwareCloudInstance getStartableInstanceFast(){
    if (!canStartNewInstance()){
      throw new QuotaException("Unable to start more instances of image " + getName());
    }

    if (myImageDetails.getBehaviour().isUseOriginal()) {
      LOG.info("Won't create a new instance - using original");
      return findInstanceById(myImageDetails.getSourceId());
    }

    if (myImageDetails.getBehaviour().isDeleteAfterStop()){ // will clone into new instance
      final String newVmName = generateNewVmName();

      final StartingVmwareCloudInstance instance = new StartingVmwareCloudInstance(this, newVmName);
      addInstance(instance);
      return instance;
    }

    final UnresolvedVmwareCloudInstance instance = new UnresolvedVmwareCloudInstance(this);
    addInstance(instance);
    return instance;
  }

  @Override
  public synchronized VmwareCloudInstance startNewInstance(@NotNull final CloudInstanceUserData cloudInstanceUserData) throws QuotaException{
    final VmwareCloudInstance instanceCandidate = getStartableInstanceFast();
    instanceCandidate.setStatus(InstanceStatus.SCHEDULED_TO_START);
    myAsyncTaskExecutor.submit("Preparing to start new instance...", () -> {
      VmwareInstance sourceVm = null;
      try {
        boolean willClone = true;
        VmwareCloudInstance instance = instanceCandidate;
        if (myImageDetails.getBehaviour().isUseOriginal()){
          willClone = false;
        } else {
          sourceVm = myApiConnector.getInstanceDetails(myImageDetails.getSourceVmName());

          final VmwareSourceState sourceState;
          if (instanceCandidate.getSourceState().getSnapshotName() == null) {
            String latestSnapshotName;
            latestSnapshotName = myApiConnector.getLatestSnapshot(sourceVm.getId(), myImageDetails.getSnapshotName());
            if (latestSnapshotName == null){
              if (!myImageDetails.useCurrentVersion()) {
                updateErrors(new TypedCloudErrorInfo("No such snapshot: " + getSnapshotName()));
                LOG.warn("Unable to find snapshot: " + myImageDetails.getSnapshotName() + ". Won't start " + instanceCandidate.getInstanceId());
                return;
              }
            }
            sourceState = VmwareSourceState.from(latestSnapshotName, sourceVm.getId());
            instanceCandidate.setSourceState(sourceState);
          } else {
            sourceState = sourceVm.getVmSourceState();
          }

          if (!instance.isReady()) {
            assert sourceState != null;
            // need to resolve the real instance
            VmwareCloudInstance existingInstanceToStart = getExistingInstanceToStart(sourceState);
            if (existingInstanceToStart != null) {
              removeInstance(instance.getInstanceId());
              instance = existingInstanceToStart;
              willClone = false;
            } else {
              final String newVmName = generateNewVmName();
              instance.setName(newVmName);
              instance.setInstanceId(newVmName);
              instance.setSourceState(sourceState);
              instance.setReady(true);
            }
          }

          final int instancesCount = getInstances().size();
          LOG.info("Should clone into " + instance.getName() + ": " + willClone + ". Already have instances: " + instancesCount);
          if (willClone && myImageDetails.getMaxInstances() < instancesCount) {
            LOG.info("Cannot clone - instances limit exceeded. Will try to clean up some old instances");
            cleanupOldInstances();
            // don't attempt to start so far
            removeInstance(instance.getInstanceId());
            return;
          }
        }

        if (willClone) {

          final VmwareCloudInstance finalInstance = instance;
          myAsyncTaskExecutor.executeAsync(
            new VmwareTaskWrapper(() -> myApiConnector.cloneAndStartVm(finalInstance), "Clone and start instance " + instance.getName()),
            new ImageStatusTaskWrapper(instance) {
              @Override
              public void onSuccess() {
                reconfigureVmTask(finalInstance, cloudInstanceUserData);
              }

              @Override
              public void onError(final Throwable th) {
                super.onError(th);
              }
            });
        } else {
          startVM(instance, cloudInstanceUserData);
        }
      } catch (Exception ex) {
        ex.printStackTrace();
        LOG.warnAndDebugDetails("Unexpected error while trying to start vSphere cloud instance", ex);
      }
    });

    return instanceCandidate;
  }

  private void cleanupOldInstances() {
    final long stoppedOrphanedTimeout = TeamCityProperties.getLong("teamcity.vmware.stopped.orphaned.timeout", STOPPED_ORPHANED_TIMEOUT);
    final Date considerTime = new Date(System.currentTimeMillis() - stoppedOrphanedTimeout);
    processStoppedInstances(new Function<VmwareInstance, Boolean>() {
      public Boolean fun(final VmwareInstance vmInstance) {
        final String vmName = vmInstance.getName();
        final VmwareCloudInstance instance = findInstanceById(vmName);
        if (instance != null && instance.getStatusUpdateTime().before(considerTime)){
          LOG.info(String.format("VM %s was orphaned and will be deleted", vmName));
          deleteInstance(instance);
          return true;
        }
        return false;
      }
    });
  }

  private synchronized void startVM(@NotNull final VmwareCloudInstance instance, @NotNull final CloudInstanceUserData cloudInstanceUserData) {
    instance.setStartDate(new Date());
    instance.setStatus(InstanceStatus.STARTING);
    myAsyncTaskExecutor.executeAsync(new VmwareTaskWrapper(new Callable<Task>() {
      public Task call() throws Exception {
        return myApiConnector.startInstance(instance, instance.getName(), cloudInstanceUserData);
      }
    }, "Start instance " + instance.getName())
      , new ImageStatusTaskWrapper(instance) {
      @Override
      public void onSuccess() {
        reconfigureVmTask(instance, cloudInstanceUserData);
      }
    });
  }

  private synchronized void reconfigureVmTask(@NotNull final VmwareCloudInstance instance, @NotNull final CloudInstanceUserData cloudInstanceUserData) {
    myAsyncTaskExecutor.executeAsync(new VmwareTaskWrapper(new Callable<Task>() {
                                       public Task call() throws Exception {
                                         return myApiConnector.reconfigureInstance(instance, instance.getName(), cloudInstanceUserData);
                                       }
                                     }, "Reconfigure " + instance.getName())
      , new ImageStatusTaskWrapper(instance) {
        @Override
        public void onSuccess() {
          instance.setStatus(InstanceStatus.RUNNING);
          instance.setStartDate(new Date());
          instance.updateErrors();
          LOG.info("Reconfiguration of '" + instance.getInstanceId() + "' is finished. Instance started successfully");
        }

        @Override
        public void onError(final Throwable th) {
          LOG.warnAndDebugDetails("Can't reconfigure '" + instance.getInstanceId() +"'. Instance will be terminated", th);
          terminateInstance(instance);
        }
      });
  }


  public void terminateInstance(@NotNull final VmwareCloudInstance instance) {

    LOG.info("Stopping instance " + instance.getName());
    instance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);
    myAsyncTaskExecutor.executeAsync(new VmwareTaskWrapper(new Callable<Task>() {
      public Task call() throws Exception {
        return myApiConnector.stopInstance(instance);
      }
    }, "Stop " + instance.getName()), new ImageStatusTaskWrapper(instance){

      @Override
      public void onComplete() {
        instance.setStatus(InstanceStatus.STOPPED);
        if (myImageDetails.getBehaviour().isDeleteAfterStop()) { // we only destroy proper instances.
          deleteInstance(instance);
        }
      }
    });

  }

  private void deleteInstance(@NotNull final VmwareCloudInstance instance){
    if (instance.getErrorInfo() == null) {
      LOG.info("Will delete instance " + instance.getName());
      myAsyncTaskExecutor.executeAsync(new VmwareTaskWrapper(new Callable<Task>() {
        public Task call() throws Exception {
          return myApiConnector.deleteInstance(instance);
        }
      }, "Delete " + instance.getName()), new ImageStatusTaskWrapper(instance) {
        @Override
        public void onSuccess() {
          removeInstance(instance.getName());
        }
      });
    } else {
      LOG.warn(String.format("Won't delete instance %s with error: %s (%s)",
                             instance.getName(), instance.getErrorInfo().getMessage(), instance.getErrorInfo().getDetailedMessage()));
    }
  }

  @NotNull
  @Override
  public synchronized CanStartNewInstanceResult canStartNewInstanceWithDetails() {
    if (getErrorInfo() != null){
      LOG.debug("Can't start new instance, if image is erroneous");
      return CanStartNewInstanceResult.no("Image is erroneous.");
    }

    final String sourceId = myImageDetails.getSourceId();
    if (myImageDetails.getBehaviour().isUseOriginal()) {
      final VmwareCloudInstance myInstance = findInstanceById(sourceId);
      if (myInstance == null) {
        return CanStartNewInstanceResult.no("Can't find original instance by id " + sourceId);
      }
      if (myInstance.getStatus() == InstanceStatus.STOPPED) {
        return CanStartNewInstanceResult.yes();
      }
      return CanStartNewInstanceResult.no("Original instance with id " + sourceId + " is not being stopped");
    }

    final boolean countStoppedVmsInLimit = TeamCityProperties.getBoolean(VmwareConstants.CONSIDER_STOPPED_VMS_LIMIT)
                                           && myImageDetails.getBehaviour().isDeleteAfterStop();

    final List<String> consideredInstances = new ArrayList<String>();
    for (VmwareCloudInstance instance : getInstances()) {
      if (instance.getStatus() != InstanceStatus.STOPPED || countStoppedVmsInLimit)
        consideredInstances.add(instance.getInstanceId());
    }
    final boolean canStartMore =  consideredInstances.size() < myImageDetails.getMaxInstances();
    final String message = String.format("[%s] Instances count: %d %s, can start more: %s", sourceId,
                                         consideredInstances.size(), Arrays.toString(consideredInstances.toArray()), String.valueOf(canStartMore));
    LOG.debug(message);
    return canStartMore ? CanStartNewInstanceResult.yes() : CanStartNewInstanceResult.no("Image instance limit exceeded");
  }

  @Override
  public void restartInstance(@NotNull final VmwareCloudInstance instance) {
    throw new UnsupportedOperationException("Restart not implemented");
  }


  protected String generateNewVmName() {
    String newVmName;
    do {
      newVmName = String.format("%s-%d", getId(), myIdxCounter.getAndIncrement());
      myIdxTouched.set(true);
    } while (getInstanceIds().contains(newVmName));
    LOG.info("Will create a new VM with name " + newVmName);
    return newVmName;
  }

  public VmwareCloudImageDetails getImageDetails() {
    return myImageDetails;
  }

  @NotNull
  @Override
  protected VmwareCloudInstance createInstanceFromReal(final AbstractInstance realInstance) {
    final VmwareInstance vmwareInstance = (VmwareInstance) realInstance;
    return new VmwareCloudInstance(this, realInstance.getName(), vmwareInstance.getVmSourceState());
  }

  private void processStoppedInstances(final Function<VmwareInstance, Boolean> function)  {
    myApiConnector.processImageInstances(this, new VMWareApiConnector.VmwareInstanceProcessor() {
      public void process(final VmwareInstance vmInstance) {
        if (vmInstance.getInstanceStatus() == InstanceStatus.STOPPED) {

          final String vmName = vmInstance.getName();
          final VmwareCloudInstance instance = findInstanceById(vmName);

          if (instance == null) {
            LOG.warn("Unable to find instance " + vmName + " in myInstances.");
            return;
          }

          // checking if this instance is already starting.
          if (instance.getStatus() != InstanceStatus.STOPPED)
            return;

          // currently value is ignore
          function.fun(vmInstance);
        }
      }
    });
  }

  @Nullable
  @Override
  public Integer getAgentPoolId() {
    return myImageDetails.getAgentPoolId();
  }

  public CloudProfile getProfile() {
    return myProfile;
  }

  void storeIdx() {
    if (myIdxTouched.compareAndSet(true, false)){
      synchronized (myIdxFile) {
        try {
          FileUtil.writeViaTmpFile(myIdxFile, new ByteArrayInputStream(String.valueOf(myIdxCounter.get()).getBytes()), FileUtil.IOAction.DO_NOTHING);
        } catch (IOException ignored) {}
      }
    }
  }

  private static class ImageStatusTaskWrapper extends TaskCallbackHandler {

    @NotNull protected final VmwareCloudInstance myInstance;

    public ImageStatusTaskWrapper(@NotNull final VmwareCloudInstance instance) {
      myInstance = instance;
    }

    @Override
    public void onError(final Throwable th) {
      myInstance.setStatus(InstanceStatus.ERROR);
      if (th != null) {
        myInstance.updateErrors(TypedCloudErrorInfo.fromException(th));
        LOG.warnAndDebugDetails("An error occurred: " + th.getLocalizedMessage() + " during processing " + myInstance.getName(), th);
        th.printStackTrace();
      } else {
        myInstance.updateErrors(new TypedCloudErrorInfo("Unknown error during processing instance " + myInstance.getName()));
        LOG.warn("Unknown error during processing " + myInstance.getName());
      }
    }
  }

  public void updateActualSourceState(@NotNull final VmwareSourceState state){
    if (StringUtil.isNotEmpty(state.getSnapshotName()) && !state.equals(myActualSourceState.get())){
        myActualSourceState.set(state);
        LOG.info("Updated actual vm source state name for " + myImageDetails.getSourceId() + " to " + state);
    }
  }
}
