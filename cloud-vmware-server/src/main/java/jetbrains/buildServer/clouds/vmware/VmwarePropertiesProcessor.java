

package jetbrains.buildServer.clouds.vmware;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManagerBase;
import jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Sergey.Pak on 1/29/2016.
 */
public class VmwarePropertiesProcessor implements PropertiesProcessor {

  @NotNull private final CloudManagerBase myCloudManager;

  public VmwarePropertiesProcessor(@NotNull final CloudManagerBase cloudManager){
    myCloudManager = cloudManager;
  }

  @NotNull
  public Collection<InvalidProperty> process(final Map<String, String> properties) {
    List<InvalidProperty> list = new ArrayList<>();

    // Remove helper properties used in ConfigurationHelperController and GetSnapshotsListController
    try {
      properties.remove("helperFieldValue");
      properties.remove("helperFieldId");
      properties.remove("image");
      properties.remove("force_trust_manager");
    } catch (UnsupportedOperationException ignored) {
      // In case of unmodifiable map passed
    }

    notEmpty(properties, VMWareWebConstants.SECURE_PASSWORD, list);
    notEmpty(properties, VMWareWebConstants.USERNAME, list);
    notEmpty(properties, VMWareWebConstants.SERVER_URL, list);

    String instancesLimit = properties.get(VMWareWebConstants.PROFILE_INSTANCE_LIMIT);
    if (!StringUtil.isEmpty(instancesLimit) && !StringUtil.isAPositiveNumber(instancesLimit)) {
      list.add(new InvalidProperty(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "Must be a positive integer or empty"));
    }

    if (!list.isEmpty()) {
      return list;
    }

    String currentProfileId = properties.get(CloudConstants.PROFILE_ID);
    CloudProfile cloudProfile = myCloudManager.findProfileGloballyById(currentProfileId);

    String cloudProfileName = cloudProfile == null ? null : cloudProfile.getProfileName();

    String imagesData = properties.get(CloudImageParameters.SOURCE_IMAGES_JSON);

    if (StringUtil.isEmpty(imagesData)) {
      return list; // allowing empty profiles
    }

    JsonParser parser = new JsonParser();
    JsonElement element = parser.parse(imagesData);
    if (element.isJsonArray()){
      StreamSupport.stream(element.getAsJsonArray().spliterator(), false)
              .map(JsonElement::getAsJsonObject)
              .map(obj -> obj.getAsJsonPrimitive(CloudImageParameters.SOURCE_ID_FIELD))
              .filter(Objects::nonNull)
              .map(json -> json.getAsString().toUpperCase())
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
              .entrySet()
              .stream()
              .filter(e -> e.getValue() > 1)
              .map(Map.Entry::getKey)
              .map(id -> new InvalidProperty(CloudImageParameters.SOURCE_IMAGES_JSON,
                      String.format("The cloud profile '%s' already contains an image named '%s'. Select a different VM or change the custom name.", cloudProfileName, id)
              ))
              .forEach(list::add);
    } else {
      list.add(new InvalidProperty(CloudImageParameters.SOURCE_IMAGES_JSON, "Unable to parse images data - bad format"));
    }

    return list;
  }

  private void notEmpty(@NotNull final Map<String, String> props,
                                 @NotNull final String key,
                                 @NotNull final Collection<InvalidProperty> col) {
    if (!props.containsKey(key) || StringUtil.isEmptyOrSpaces(props.get(key))) {
      col.add(new InvalidProperty(key, "Value should be set"));
    }
  }
}