package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:07 PM
 */
public class FakeVirtualMachine extends VirtualMachine {

  private static final int GUEST_SHUTDOWN_TIMEOUT = 600;
  private String myName;
  private VirtualMachineRuntimeInfo myRuntimeInfo;
  private GuestInfo myGuestInfo;
  private VirtualMachineSnapshotInfo mySnapshotInfo;
  private final AtomicReference<VirtualMachineConfigInfo> myConfigInfo = new AtomicReference<VirtualMachineConfigInfo>();
  private Map<String, String> myCustomUserData;
  private boolean myTasksSuccessfull = true;
  private AtomicBoolean myIsStarted = new AtomicBoolean(false);
  private final List<VirtualMachineSnapshotTree> myRootSnapshotList = new ArrayList<VirtualMachineSnapshotTree>();
  private String myVersion;
  private ManagedEntity myParent;
  private Calendar myBootTime;

  public FakeVirtualMachine(final String name, final boolean isTemplate, final boolean isRunning) {
    super(null, createVMMor(name));
    myName = name;
    updateVersion();
    myIsStarted.set(isRunning);
    myConfigInfo.set(new VirtualMachineConfigInfo(){
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
    });

    myRuntimeInfo = new VirtualMachineRuntimeInfo(){
      @Override
      public VirtualMachinePowerState getPowerState() {
        return myIsStarted.get() ? VirtualMachinePowerState.poweredOn : VirtualMachinePowerState.poweredOff;
      }

      @Override
      public Calendar getBootTime() {
        if (myBootTime != null)
          return myBootTime;
        return super.getBootTime();
      }
    };

    mySnapshotInfo = new VirtualMachineSnapshotInfo(){
      @Override
      public VirtualMachineSnapshotTree[] getRootSnapshotList() {
        return myRootSnapshotList.toArray(new VirtualMachineSnapshotTree[myRootSnapshotList.size()]);
      }
    };

    myCustomUserData = new HashMap<String, String>();
    enableGuestTools();
    myParent = null;
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
    final VirtualMachine vm = FakeModel.instance().addVM(name, false, spec);
    final VirtualMachineConfigInfo oldConfig = ((FakeVirtualMachine)vm).myConfigInfo.get();
    ((FakeVirtualMachine)vm).myConfigInfo.set(null);
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(){
      @Override
      public void run() {
        try {sleep(500);} catch (InterruptedException e) {}
        latch.countDown();
        ((FakeVirtualMachine)vm).myConfigInfo.set(oldConfig);
        if (spec.isPowerOn()){
          if (!((FakeVirtualMachine)vm).myIsStarted.compareAndSet(false, true)){
            //throw new RemoteException("Already started");
          }
        }
      }
    }.start();
    return longTask(latch);
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
    return myConfigInfo.get();
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
    if (myGuestInfo != null) {
      try {
        Thread.sleep(GUEST_SHUTDOWN_TIMEOUT);
        updateVersion();
        myIsStarted.set(false);
      } catch (InterruptedException e) {
      }
    } else {
      throw new RemoteException("no guest tools available");
    }
  }

  @Override
  public ManagedEntity getParent() {
    return myParent;
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

  public void setParentFolder(String folderName){
    final FakeFolder folder = FakeModel.instance().getFolder(folderName);
    myParent = folder;
  }

  public void setBootTime(final Calendar bootTime) {
    myBootTime = bootTime;
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

  private Task longTask(final CountDownLatch latch){
    return new Task(null, null) {

      @Override
      public TaskInfo getTaskInfo() throws RemoteException {
        final TaskInfo taskInfo = new TaskInfo();
        taskInfo.setState(latch.getCount()==0 ? TaskInfoState.success : TaskInfoState.running);
        return taskInfo;
      }

      @Override
      public String waitForTask() throws RemoteException, InterruptedException {
        latch.await();
        return SUCCESS;
      }
    };
  }

  public void addCustomParam(final String key, final Object value){
    myCustomUserData.put(key, String.valueOf(value));

  }

  private void updateVersion(){
    myVersion = new Date().toString();
  }

  public void enableGuestTools(){
    myGuestInfo = new GuestInfo();
    Random r = new Random();
    myGuestInfo.setIpAddress("192.168.1."  + (1+ r.nextInt(254)));
  }

  public void disableGuestTools(){
    myGuestInfo = null;
  }

  private static ManagedObjectReference createVMMor(final String name){
    return new ManagedObjectReference(){
      @Override
      public String getVal() {
        return "vm-" + name.hashCode();
      }

      @Override
      public String get_value() {
        return getVal();
      }

      @Override
      public String getType() {
        return "VirtualMachine";
      }
    };
  }
}
