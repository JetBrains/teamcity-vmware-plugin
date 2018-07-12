package jetbrains.buildServer.clouds.vmware.stubs;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.clouds.vmware.stubs.FakeModel.FAKE_MODEL_THREAD_FACTORY;

/**
 * @author Sergey.Pak
 *         Date: 5/13/2014
 *         Time: 1:07 PM
 */
public class FakeVirtualMachine extends VirtualMachine {

  private static final int GUEST_SHUTDOWN_SLEEP_INTERVAL = 600;
  private String myName;
  private VirtualMachineRuntimeInfo myRuntimeInfo;
  private GuestInfo myGuestInfo;
  private VirtualMachineSnapshotInfo mySnapshotInfo;
  private final AtomicReference<VirtualMachineConfigInfo> myConfigInfoRef = new AtomicReference<VirtualMachineConfigInfo>();
  private Map<String, String> myCustomUserData;
  private boolean myTasksSuccessfull = true;
  private AtomicBoolean myIsStarted = new AtomicBoolean(false);
  private final List<VirtualMachineSnapshotTree> myRootSnapshotList = new ArrayList<VirtualMachineSnapshotTree>();
  private String myVersion;
  private ManagedEntity myParent;
  private Calendar myBootTime;
  private CustomizationSpec myCustomizationSpec;
  private AtomicBoolean myGone = new AtomicBoolean(false);
  private volatile ManagedObjectReference selfMOR = null;

  public FakeVirtualMachine(final String name, final boolean isTemplate, final boolean isRunning) {
    super(null, createVMMor(name));
    myName = name;
    updateVersion();
    myIsStarted.set(isRunning);
    myConfigInfoRef.set(new VirtualMachineConfigInfo(){
      @Override
      public String getName() {
        return myName;
      }

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
    myParent = new ManagedEntity(getServerConnection(), createVMMor("parent"));
  }

  @Override
  public String getName() {
    checkIfInstanceIsGone();
    return myName;
  }

  private void checkIfInstanceIsGone() {
    if (myGone.get()){
      final ManagedObjectNotFound cause = new ManagedObjectNotFound();
      cause.setObj(getMOR());
      throw new RuntimeException(cause);
    }
  }

  @Override
  protected Object getCurrentProperty(final String propertyName) {
    checkIfInstanceIsGone();

    return super.getCurrentProperty(propertyName);
  }

  @Override
  public VirtualMachineRuntimeInfo getRuntime() {
    checkIfInstanceIsGone();

    return myRuntimeInfo;
  }

  @Override
  public VirtualMachineSnapshotInfo getSnapshot() {
    checkIfInstanceIsGone();
    return mySnapshotInfo;
  }

  @Override
  public Task cloneVM_Task(final Folder folder, final String name, final VirtualMachineCloneSpec spec)
    throws RemoteException {
    FakeModel.instance().publishEvent(getName(), "cloneVM_Task");
    final FakeVirtualMachine newVm = FakeModel.instance().addVM(name, false, spec);
    newVm.setParentFolder(folder.getName());
    final VirtualMachineConfigInfo oldConfig = newVm.myConfigInfoRef.get();
    newVm.myConfigInfoRef.set(null);
    newVm.myCustomizationSpec = spec.getCustomization();
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(){
      @Override
      public void run() {
        try {sleep(500);} catch (InterruptedException e) {}
        latch.countDown();
        newVm.myConfigInfoRef.set(oldConfig);

        if (spec.isPowerOn()){
          if (!newVm.myIsStarted.compareAndSet(false, true)){
            //throw new RemoteException("Already started");
          }
        }
      }
    }.start();
    return longTask(latch);
  }

  @Override
  public Task powerOffVM_Task() throws RemoteException {
    FakeModel.instance().publishEvent(getName(), "powerOffVM_Task");
    if (!myIsStarted.get()){
      throw new RemoteException("Already stopped");
    }
    myIsStarted.set(false);
    return conditionalTask();
  }

  @Override
  public Task powerOnVM_Task(final HostSystem host) throws RemoteException {
    FakeModel.instance().publishEvent(getName(), "powerOnVM_Task");

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
    checkIfInstanceIsGone();
    return myConfigInfoRef.get();
  }

  public void setConfigInfo(VirtualMachineConfigInfo configInfo){
    myConfigInfoRef.set(configInfo);
  }


  @Override
  public Task destroy_Task() throws RemoteException {
    FakeModel.instance().publishEvent(getName(), "destroy_Task");
    FakeModel.instance().removeVM(getName());
    return conditionalTask();
  }

  @Override
  public Task reconfigVM_Task(final VirtualMachineConfigSpec spec) throws RemoteException {
    FakeModel.instance().publishEvent(getName(), "reconfigVM_Task");
    final OptionValue[] extraConfig = spec.getExtraConfig();
    for (OptionValue opt : extraConfig) {
      myCustomUserData.put(opt.getKey(), opt.getValue().toString());
    }
    return conditionalTask();
  }

  @Override
  public void shutdownGuest() throws RemoteException {
    FakeModel.instance().publishEvent(getName(), "shutdownGuest");
    if (myGuestInfo != null) {
      FAKE_MODEL_THREAD_FACTORY.newThread(() -> {
        try {
          Thread.sleep(TeamCityProperties.getIntervalMilliseconds("test.guest.shutdown.sleep.interval", GUEST_SHUTDOWN_SLEEP_INTERVAL));
          updateVersion();
          myIsStarted.set(false);
        } catch (InterruptedException e) {}
      }).start();
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

  public static Task failureTask(){
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

  @Override
  public void setMOR(final ManagedObjectReference mor) {
     selfMOR = mor;
     super.setMOR(mor);
  }

  @Override
  public ManagedObjectReference getMOR() {
    return selfMOR == null ? super.getMOR() : selfMOR;
  }

  public void enableGuestTools(){
    myGuestInfo = new GuestInfo();
    Random r = new Random();
    myGuestInfo.setIpAddress("192.168.1."  + (1+ r.nextInt(254)));
  }

  public void disableGuestTools(){
    myGuestInfo = null;
  }

  public CustomizationSpec getCustomizationSpec() {
    return myCustomizationSpec;
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

  public boolean isRunning(){
    return myIsStarted.get();
  }

  public void setGone(boolean gone){
    myGone.set(gone);
  }

  public boolean isGone(){
    return myGone.get();
  }

  @Override
  public String toString() {
    return "FakeVirtualMachine{" +
           "myName='" + myName + '\'' +
           ", myIsStarted=" + myIsStarted +
           '}';
  }
}
