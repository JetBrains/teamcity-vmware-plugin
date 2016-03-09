package jetbrains.buildServer.clouds.vmware;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Sergey.Pak on 1/29/2016.
 */
public class VmwarePropertiesProcessor implements PropertiesProcessor {
  @NotNull
  public Collection<InvalidProperty> process(final Map<String, String> properties) {
    List<InvalidProperty> list = new ArrayList<InvalidProperty>();

    notEmpty(properties, VMWareWebConstants.SECURE_PASSWORD, list);
    notEmpty(properties, VMWareWebConstants.USERNAME, list);
    notEmpty(properties, VMWareWebConstants.SERVER_URL, list);
    final String instancesLimit = properties.get(VMWareWebConstants.PROFILE_INSTANCE_LIMIT);
    if (!StringUtil.isEmpty(instancesLimit)){
      if (!StringUtil.isAPositiveNumber(instancesLimit)){
        list.add(new InvalidProperty(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "Must be a positive number or empty"));
      }
    }

    return list;
  }

  protected static void notEmpty(@NotNull final Map<String, String> props,
                                 @NotNull final String key,
                                 @NotNull final Collection<InvalidProperty> col) {
    if (!props.containsKey(key) || StringUtil.isEmptyOrSpaces(props.get(key))) {
      col.add(new InvalidProperty(key, "Value should be set"));
    }
  }
}
