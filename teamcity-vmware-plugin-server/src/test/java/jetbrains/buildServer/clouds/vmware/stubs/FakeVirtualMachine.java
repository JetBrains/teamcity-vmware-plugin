package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:07 PM
 */
public class FakeVirtualMachine extends VirtualMachine {

  private static final int GUEST_SHUTDOWN_TIMEOUT = 5*1000;
  private String myName;
  private VirtualMachineRuntimeInfo myRuntimeInfo;
  private GuestInfo myGuestInfo;
  private VirtualMachineSnapshotInfo mySnapshotInfo;
  private VirtualMachineConfigInfo myConfigInfo;
  private Map<String, String> myCustomUserData;
  private boolean myTasksSuccessfull = true;
  private AtomicBoolean myIsStarted = new AtomicBoolean(false);
  private final List<VirtualMachineSnapshotTree> myRootSnapshotList = new ArrayList<VirtualMachineSnapshotTree>();
  private String myVersion;

  private FakeVirtualMachine(){
    super(null, null);
  }

  public FakeVirtualMachine(final String name, final boolean isTemplate, final boolean isRunning) {
    this();
    myName = name;
    updateVersion();
    myIsStarted.set(isRunning);
    myConfigInfo = new VirtualMachineConfigInfo(){
      @Override
      public boolean isTemplate() {
        return isTemplate;
      }

      @Override
      public String getChangeVersion() {
        return myVersion;
      }

      @Override
      public OptionValue[] getExtraConfig() {
        final OptionValue[] retval = new OptionValue[myCustomUserData.size()];
        int i=0;
        for (final Map.Entry<String, String> entry : myCustomUserData.entrySet()) {
          retval[i] = new OptionValue();
          retval[i].setKey(entry.getKey());
          retval[i++].setValue(entry.getValue());
        }
        return retval;
      }
    };

    myRuntimeInfo = new VirtualMachineRuntimeInfo(){
      @Override
      public VirtualMachinePowerState getPowerState() {
        return myIsStarted.get() ? VirtualMachinePowerState.poweredOn : VirtualMachinePowerState.poweredOff;
      }
    };

    mySnapshotInfo = new VirtualMachineSnapshotInfo(){
      @Override
      public VirtualMachineSnapshotTree[] getRootSnapshotList() {
        return myRootSnapshotList.toArray(new VirtualMachineSnapshotTree[myRootSnapshotList.size()]);
      }
    };

    myCustomUserData = new HashMap<String, String>();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public VirtualMachineRuntimeInfo getRuntime() {
    return myRuntimeInfo;
  }

  @Override
  public VirtualMachineSnapshotInfo getSnapshot() {
    return mySnapshotInfo;
  }

  @Override
  public Task cloneVM_Task(final Folder folder, final String name, final VirtualMachineCloneSpec spec)
    throws RemoteException {
    FakeModel.instance().addVM(name, false, spec);
    return conditionalTask();
  }

  @Override
  public Task powerOffVM_Task() throws RemoteException {
    if (!myIsStarted.get()){
      throw new RemoteException("Already stopped");
    }
    myIsStarted.set(false);
    return conditionalTask();
  }

  @Override
  public Task powerOnVM_Task(final HostSystem host) throws RemoteException {
    if (myIsStarted.get()){
      throw new RemoteException("Already started");
    }
    myIsStarted.set(true);
    return conditionalTask();
  }

  @Override
  public GuestInfo getGuest() {
    return myGuestInfo;
  }

  @Override
  public VirtualMachineConfigInfo getConfig() {
    return myConfigInfo;
  }

  @Override
  public Task destroy_Task() throws RemoteException {
    FakeModel.instance().removeVM(getName());
    return conditionalTask();
  }

  @Override
  public Task reconfigVM_Task(final VirtualMachineConfigSpec spec) throws RemoteException {
    final OptionValue[] extraConfig = spec.getExtraConfig();
    for (OptionValue opt : extraConfig) {
      myCustomUserData.put(opt.getKey(), opt.getValue().toString());
    }
    return conditionalTask();
  }

  @Override
  public void shutdownGuest() throws RemoteException {
    try {
      Thread.sleep(GUEST_SHUTDOWN_TIMEOUT);
      updateVersion();
      myIsStarted.set(false);
    } catch (InterruptedException e) {

    }

  }

  public void setTasksSuccessStatus(boolean success){
    myTasksSuccessfull = success;
  }


  public void addSnapshot(@NotNull final String snapshotName) {
    VirtualMachineSnapshotTree tree = new VirtualMachineSnapshotTree();
    tree.setName(snapshotName);
    tree.setSnapshot(new ManagedObjectReference());
    tree.setCreateTime(Calendar.getInstance());
    myRootSnapshotList.add(tree);
  }

  public void removeSnapshot(@NotNull final String snapshotName) {
    for (VirtualMachineSnapshotTree tree : myRootSnapshotList) {
      if (tree.getName().equals(snapshotName)){
        myRootSnapshotList.remove(tree);
        return;
      }
    }
  }

  private static Task successTask(){
    return new Task(null, null) {
      @Override
      public TaskInfo getTaskInfo() throws RemoteException {
        final TaskInfo taskInfo = new TaskInfo();
        taskInfo.setState(TaskInfoState.success);
        return taskInfo;
      }

      @Override
      public String waitForTask() throws RemoteException, InterruptedException {
        return Task.SUCCESS;
      }
    };
  }

  private static Task failureTask(){
    return new Task(null, null) {
      @Override
      public TaskInfo getTaskInfo() throws RemoteException {
        final TaskInfo taskInfo = new TaskInfo();
        taskInfo.setState(TaskInfoState.error);
        return taskInfo;
      }

      @Override
      public String waitForTask() throws RemoteException, InterruptedException {
        return "failure";
      }
    };
  }

  private Task conditionalTask(){
    return myTasksSuccessfull ? successTask() : failureTask();
  }

  public void addCustomParam(final String key, final Object value){
    myCustomUserData.put(key, String.valueOf(value));

  }

  private void updateVersion(){
    myVersion = new Date().toString();
  }
}
