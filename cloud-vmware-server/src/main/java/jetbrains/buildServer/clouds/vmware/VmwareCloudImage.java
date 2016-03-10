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
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.QuotaException;
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
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

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
  private final AtomicReference<String> myActualSnapshotName;
  private final File myIdxFile;

  public VmwareCloudImage(@NotNull final VMWareApiConnector apiConnector,
                          @NotNull final VmwareCloudImageDetails imageDetails,
                          @NotNull final CloudAsyncTaskExecutor asyncTaskExecutor,
                          @NotNull final File idxStorage) {
    super(imageDetails.getNickname(), imageDetails.getNickname());
    myImageDetails = imageDetails;
    myApiConnector = apiConnector;
    myAsyncTaskExecutor = asyncTaskExecutor;
    myInstances.clear();
    myActualSnapshotName = new AtomicReference<String>("");
    myIdxFile = new File(idxStorage, imageDetails.getNickname() + ".idx");
    if (!myIdxFile.exists()){
      try {
        FileUtil.writeFileAndReportErrors(myIdxFile, "1");
      } catch (IOException e) {
        LOG.warn(String.format("Unable to write idx file '%s': %s", myIdxFile.getAbsolutePath(), e.toString()));
      }
    }

    final Map<String, VmwareInstance> realInstances;
    try {
      realInstances = myApiConnector.fetchInstances(this);
    } catch (VmwareCheckedCloudException e) {
      updateErrors(TypedCloudErrorInfo.fromException(e));
      return;
    }
    if (imageDetails.getBehaviour().isUseOriginal()) {
      final VmwareCloudInstance imageInstance = new VmwareCloudInstance(this, imageDetails.getSourceName(), VmwareConstants.CURRENT_STATE);
      myInstances.put(myImageDetails.getSourceName(), imageInstance);

      final VmwareInstance vmwareInstance = realInstances.get(imageDetails.getSourceName());
      if (vmwareInstance != null) {
        imageInstance.setStatus(vmwareInstance.getInstanceStatus());
      } else {
        imageInstance.setStatus(InstanceStatus.UNKNOWN);
        imageInstance.updateErrors(new TypedCloudErrorInfo("NoVM", "VM doesn't exist: " + imageDetails.getSourceName()));
      }
    } else {
      for (String instanceName : realInstances.keySet()) {
        final VmwareInstance instance = realInstances.get(instanceName);
        VmwareCloudInstance cloudInstance = createInstanceFromReal(instance);
        cloudInstance.setStatus(instance.getInstanceStatus());
        myInstances.put(instanceName, cloudInstance);
      }
    }
  }

  @NotNull
  public String getSnapshotName() {
    return myImageDetails.getSnapshotName();
  }

  @NotNull
  protected synchronized VmwareCloudInstance getOrCreateInstance() throws VmwareCheckedCloudException {
    if (!canStartNewInstance()){
      throw new QuotaException("Unable to start more instances of image " + getName());
    }

    if (myImageDetails.getBehaviour().isUseOriginal()) {
      LOG.info("Won't create a new instance - using original");
      return myInstances.get(myImageDetails.getSourceName());
    }

    final String latestSnapshotName = myApiConnector.getLatestSnapshot(myImageDetails.getSourceName(), myImageDetails.getSnapshotName());
    if (!myImageDetails.useCurrentVersion() && latestSnapshotName == null) {
      updateErrors(new TypedCloudErrorInfo("No such snapshot: " + getSnapshotName()));
      throw new VmwareCheckedCloudException("Unable to find snapshot: " + myImageDetails.getSnapshotName());
    }

    if (!myImageDetails.getBehaviour().isDeleteAfterStop()) {
      // on demand clone
      final VmwareInstance imageVm = myApiConnector.getInstanceDetails(myImageDetails.getSourceName());
      final AtomicReference<VmwareCloudInstance> candidate = new AtomicReference<VmwareCloudInstance>();
      processStoppedInstances(new Function<VmwareInstance, Boolean>() {
        public Boolean fun(final VmwareInstance vmInstance) {
          final String vmName = vmInstance.getName();
          final VmwareCloudInstance instance = myInstances.get(vmName);

          if (myImageDetails.useCurrentVersion()) {
            if (imageVm.getChangeVersion() == null || !imageVm.getChangeVersion().equals(vmInstance.getChangeVersion())) {
              LOG.info(String.format("Change version for %s is outdated: '%s' vs '%s'", vmName, vmInstance.getChangeVersion(), imageVm.getChangeVersion()));
              deleteInstance(instance);
              return false;
            }
          } else {
            final String snapshotName = vmInstance.getSnapshotName();
            if (latestSnapshotName != null && !latestSnapshotName.equals(snapshotName)) {
              LOG.info(String.format("VM %s Snapshot is not the latest one: '%s' vs '%s'", vmName, snapshotName, latestSnapshotName));
              deleteInstance(instance);
              return false;
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
    }
    // wasn't able to find an existing candidate, so will clone into a new VM
    final String newVmName = generateNewVmName();
    LOG.info("Will create a new VM with name " + newVmName);
    return new VmwareCloudInstance(this, newVmName, latestSnapshotName);
  }

  @Override
  public synchronized VmwareCloudInstance startNewInstance(@NotNull final CloudInstanceUserData cloudInstanceUserData) throws QuotaException {
    try {
      final VmwareCloudInstance instance = getOrCreateInstance();
      if (instance == null) {
        return null;
      }
      boolean willClone = !myApiConnector.checkVirtualMachineExists(instance.getName());

      final VmwareTaskWrapper startTask = new VmwareTaskWrapper(new Callable<Task>() {
        public Task call() throws Exception {
          return myApiConnector.cloneAndStartVm(instance, myImageDetails.getResourcePoolId(), myImageDetails.getFolderId());
        }
      }, "Clone and start instance " + instance.getName()
      );
      final ImageStatusTaskWrapper callbackHandler = new ImageStatusTaskWrapper(instance) {
        @Override
        public void onSuccess() {
          reconfigureVmTask(instance, cloudInstanceUserData);
        }

        @Override
        public void onError(final Throwable th) {
          super.onError(th);
          removeInstance(instance.getName());
        }
      };

      LOG.info("Will clone for " + instance.getName() + ": " + willClone);
      if (willClone && myImageDetails.getMaxInstances() <= myInstances.size()){
        final long stoppedOrphanedTimeout = TeamCityProperties.getLong("teamcity.vmware.stopped.orphaned.timeout", STOPPED_ORPHANED_TIMEOUT);
        final Date considerTime = new Date(System.currentTimeMillis() - stoppedOrphanedTimeout);
        System.out.println(considerTime);
        System.out.println(myInstances);
        processStoppedInstances(new Function<VmwareInstance, Boolean>() {
          public Boolean fun(final VmwareInstance vmInstance) {
            final String vmName = vmInstance.getName();
            final VmwareCloudInstance instance = myInstances.get(vmName);


            if (instance.getStatusUpdateTime().before(considerTime)){
              LOG.info(String.format("VM %s was orphaned and will be deleted", vmName));
              deleteInstance(instance);
              return true;
            }
            return false;
          }
        });

        throw new QuotaException(String.format("Cannot clone '%s' into '%s' - limit exceeded", myImageDetails.getSourceName(), instance.getName()));
      }
      instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
      if (!myInstances.containsKey(instance.getName())) {
        addInstance(instance);
      }
      if (willClone) {

        myAsyncTaskExecutor.executeAsync(
                startTask,
                callbackHandler);
      } else {
        startVM(instance, cloudInstanceUserData);
      }
      return instance;
    } catch (QuotaException e) {
      throw e;
    } catch (VmwareCheckedCloudException e) {
      throw new CloudException("Unable to start new instance: " + e.toString());
    }
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
    },"Reconfigure " + instance.getName())
      , new ImageStatusTaskWrapper(instance) {
      @Override
      public void onSuccess() {
        instance.setStatus(InstanceStatus.RUNNING);
        instance.setStartDate(new Date());
        instance.updateErrors();
        LOG.info("Instance started successfully");
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
      final VmwareInstance vmInstance;
      try {
        vmInstance = myApiConnector.getInstanceDetails(instance.getName());
        myAsyncTaskExecutor.executeAsync(vmInstance.deleteInstance(), new ImageStatusTaskWrapper(instance) {
          @Override
          public void onSuccess() {
            removeInstance(instance.getName());
          }
        });
      } catch (VmwareCheckedCloudException e) {
        LOG.warn("An exception during deleting instance " + instance.getName(), e);
        instance.updateErrors(TypedCloudErrorInfo.fromException(e));
      }
    } else {
      LOG.warn(String.format("Won't delete instance %s with error: %s (%s)",
                             instance.getName(), instance.getErrorInfo().getMessage(), instance.getErrorInfo().getDetailedMessage()));
    }
  }

  public boolean canStartNewInstance() {
    if (getErrorInfo() != null){
      LOG.debug("Can't start new instance, if image is erroneous");
        return false;
    }

    if (myImageDetails.getBehaviour().isUseOriginal()) {
      final VmwareCloudInstance myInstance = myInstances.get(myImageDetails.getSourceName());
      if (myInstance == null) {
        return false;
      }
      return myInstance.getStatus() == InstanceStatus.STOPPED;
    }

    final boolean countStoppedVmsInLimit = TeamCityProperties.getBoolean(VmwareConstants.CONSIDER_STOPPED_VMS_LIMIT)
                                           && myImageDetails.getBehaviour().isDeleteAfterStop();

    final List<String> consideredInstances = new ArrayList<String>();
    for (Map.Entry<String, VmwareCloudInstance> entry : myInstances.entrySet()) {
      if (entry.getValue().getStatus() != InstanceStatus.STOPPED || countStoppedVmsInLimit)
        consideredInstances.add(entry.getKey());
    }
    final boolean canStartMore =  consideredInstances.size() < myImageDetails.getMaxInstances();
    LOG.debug(String.format("Instances count: %d %s, can start more: %s",
                           consideredInstances.size(), Arrays.toString(consideredInstances.toArray()), String.valueOf(canStartMore)));
    return canStartMore;
  }

  @Override
  public void restartInstance(@NotNull final VmwareCloudInstance instance) {
    throw new UnsupportedOperationException("Restart not implemented");
  }


  protected String generateNewVmName() {
    int nextIdx = 0;
      try {
        nextIdx = Integer.parseInt(FileUtil.readText(myIdxFile));
        FileUtil.writeFileAndReportErrors(myIdxFile, String.valueOf(nextIdx+1));
      } catch (Exception e) {
        LOG.warn("Will generate random clone index. Reason: unable to read idx file: " + e.toString());
        Random r = new Random();
        nextIdx = 100000 + r.nextInt(100000);
      }
    return String.format("%s-%d", getId(), nextIdx);
  }

  public void addInstance(@NotNull final VmwareCloudInstance instance){
    LOG.info(String.format("Image %s, put instance %s", myImageDetails.getSourceName(), instance.getName()));
    myInstances.put(instance.getName(), instance);
  }

  public void removeInstance(@NotNull final String name){
    LOG.info(String.format("Image %s, remove instance %s", myImageDetails.getSourceName(), name));
    myInstances.remove(name);
  }

  public VmwareCloudImageDetails getImageDetails() {
    return myImageDetails;
  }

  @NotNull
  @Override
  protected VmwareCloudInstance createInstanceFromReal(final AbstractInstance realInstance) {
    final VmwareInstance vmwareInstance = (VmwareInstance) realInstance;
    return new VmwareCloudInstance(this, realInstance.getName(), vmwareInstance.getSnapshotName());
  }

  private void processStoppedInstances(final Function<VmwareInstance, Boolean> function) throws VmwareCheckedCloudException {
    myApiConnector.processImageInstances(this, new VMWareApiConnector.VmwareInstanceProcessor() {
      public void process(final VmwareInstance vmInstance) {
        if (vmInstance.getInstanceStatus() == InstanceStatus.STOPPED) {

          final String vmName = vmInstance.getName();
          final VmwareCloudInstance instance = myInstances.get(vmName);

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
      } else {
        myInstance.updateErrors(new TypedCloudErrorInfo("Unknown error during processing instance " + myInstance.getName()));
        LOG.warn("Unknown error during processing " + myInstance.getName());
      }
    }
  }

  public void updateActualSnapshotName(@NotNull final String snapshotName){
    if (StringUtil.isNotEmpty(snapshotName) && !snapshotName.equals(myActualSnapshotName.get())){
        LOG.info("Updated actual snapshot name for " + myImageDetails.getNickname() + " to " + snapshotName);
        myActualSnapshotName.set(snapshotName);
    }
  }
}
