package jetbrains.buildServer.clouds.vmware.errors;

import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.VmInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/4/2014
 *         Time: 3:31 PM
 */
public class VMWareCloudErrorInfo {

  private final Map<VMWareCloudErrorType, String> myErrorTypeSet;
  private CloudErrorInfo myErrorInfo = null;
  private final VmInfo myVmInfo;

  public VMWareCloudErrorInfo(@NotNull final VmInfo vmInfo) {
    this.myVmInfo = vmInfo;
    myErrorTypeSet = new HashMap<VMWareCloudErrorType, String>();
  }

  public void setErrorType(@NotNull final VMWareCloudErrorType errorType, @Nullable final String message){
    if (!myErrorTypeSet.containsKey(errorType)) {
      myErrorTypeSet.put(errorType, message);
      updateErrorInfo();
    }
  }

  public void clearErrorType(@NotNull final VMWareCloudErrorType errorType){
    if (myErrorTypeSet.containsKey(errorType)) {
      myErrorTypeSet.remove(errorType);
      updateErrorInfo();
    }
  }

  public void clearAllErrors(){
    myErrorTypeSet.clear();
    updateErrorInfo();
  }

  private void updateErrorInfo() {
    if (myErrorTypeSet.size() ==0 ){
      myErrorInfo = null;
      return;
    }
    Set<String> errorStrs = new HashSet<String>();
    for (VMWareCloudErrorType errorType : myErrorTypeSet.keySet()) {
      final String message = myErrorTypeSet.get(errorType);
      if (message == null) {
        errorStrs.add(errorType.getErrorMessage(myVmInfo));
      } else {
        errorStrs.add(message);
      }
    }
    myErrorInfo = new CloudErrorInfo(Arrays.toString(errorStrs.toArray()));
  }


  public CloudErrorInfo getErrorInfo(){
    return myErrorInfo;
  }

}
