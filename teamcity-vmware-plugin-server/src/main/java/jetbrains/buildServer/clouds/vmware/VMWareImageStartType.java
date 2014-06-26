package jetbrains.buildServer.clouds.vmware;

/**
 * @author Sergey.Pak
 *         Date: 4/29/2014
 *         Time: 2:26 PM
 */
public enum VMWareImageStartType {
  CLONE(true, false),
  START(false, true),
  ON_DEMAND_CLONE(false, false);

  private boolean myDeleteAfterStop;
  private boolean myUseOriginal;

  VMWareImageStartType(final boolean deleteAfterStop, final boolean useOriginal) {
    myDeleteAfterStop = deleteAfterStop;
    myUseOriginal = useOriginal;
  }

  public boolean isDeleteAfterStop() {
    return myDeleteAfterStop;
  }

  public boolean isUseOriginal() {
    return myUseOriginal;
  }

}
