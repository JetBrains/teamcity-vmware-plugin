package jetbrains.buildServer.clouds.vmware.errors;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.vmware.VmInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/4/2014
 *         Time: 3:26 PM
 */
public enum VMWareCloudErrorType {
  INSTANCE_CANNOT_START("Cannot start instance %s@%s"),
  INSTANCE_CANNOT_STOP("Cannot stop instance %s@%s"),
  IMAGE_NOT_EXISTS("Virtual machine %s doesn't exist"),
  IMAGE_CANNOT_CLONE("Cannot clone image %s@%s"),
  IMAGE_SNAPSHOT_NOT_EXISTS("Cannot find snapshot %s@%s"),
  CUSTOM("Unknown error %s@%s")
  ;

  VMWareCloudErrorType(final String descriptionPattern) {
    myDescriptionPattern = descriptionPattern;
  }

  private String myDescriptionPattern;

  public String getErrorMessage(@NotNull final VmInfo info){
    return String.format(myDescriptionPattern, info.getName(), info.getSnapshotName());
  }
}
