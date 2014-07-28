package jetbrains.buildServer.clouds.vmware.connector;

import com.vmware.vim25.OptionValue;
import com.vmware.vim25.mo.VirtualMachine;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/25/2014
 *         Time: 6:45 PM
 */
public class VmwareInstance extends AbstractInstance {
  public VmwareInstance(@NotNull final VirtualMachine vm) {
    super(vm.getName(), extractProperties(vm));
  }


  @Nullable
  private static Map<String, String> extractProperties(@NotNull final VirtualMachine vm){
    if (vm.getConfig()== null){
      return null;
    }

    final OptionValue[] extraConfig = vm.getConfig().getExtraConfig();
    Map<String,String> retval = new HashMap<String, String>();
    for (OptionValue optionValue : extraConfig) {
      retval.put(optionValue.getKey(), String.valueOf(optionValue.getValue()));
    }
    return retval;
  }

}
