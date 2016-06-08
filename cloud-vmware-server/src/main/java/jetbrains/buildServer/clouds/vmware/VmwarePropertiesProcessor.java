package jetbrains.buildServer.clouds.vmware;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jetbrains.buildServer.clouds.CloudConstants;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudProfile;
import jetbrains.buildServer.clouds.server.CloudManager;
import jetbrains.buildServer.clouds.server.impl.CloudManagerBase;
import jetbrains.buildServer.clouds.server.impl.CloudManagerFacade;
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
    List<InvalidProperty> list = new ArrayList<InvalidProperty>();

    notEmpty(properties, VMWareWebConstants.SECURE_PASSWORD, list);
    notEmpty(properties, VMWareWebConstants.USERNAME, list);
    notEmpty(properties, VMWareWebConstants.SERVER_URL, list);
    final String instancesLimit = properties.get(VMWareWebConstants.PROFILE_INSTANCE_LIMIT);
    if (!StringUtil.isEmpty(instancesLimit)){
      if (!StringUtil.isAPositiveNumber(instancesLimit)){
        list.add(new InvalidProperty(VMWareWebConstants.PROFILE_INSTANCE_LIMIT, "Must be a positive integer or empty"));
      }
    }

    final String currentProfileId = properties.get(CloudConstants.PROFILE_ID);
    final Map<String, String> existingImages = new HashMap<>();

     myCloudManager.listProfiles().stream()
      .filter(p->(VmwareConstants.TYPE.equals(p.getCloudCode()) && (currentProfileId == null || !currentProfileId.equals(p.getProfileId()))))
      .forEach(p->
        myCloudManager
          .getClient(p.getProfileId())
          .getImages()
          .stream()
          .forEach(i->existingImages.put(i.getId(), p.getProfileName()))
      );

    final String imagesData = properties.get(CloudImageParameters.SOURCE_IMAGES_JSON);
    if (StringUtil.isEmpty(imagesData))
      return list; // allowing empty profiles
    JsonParser parser = new JsonParser();
    final JsonElement element = parser.parse(imagesData);
    if (element.isJsonArray()){
      final Iterator<JsonElement> iterator = element.getAsJsonArray().iterator();

      StreamSupport.stream(element.getAsJsonArray().spliterator(), false)
        .map(JsonElement::getAsJsonObject)
        .map(obj->obj.getAsJsonPrimitive(CloudImageParameters.SOURCE_ID_FIELD))
        .filter(Objects::nonNull)
        .map(JsonPrimitive::getAsString)
        .filter(existingImages::containsKey)
        .map(id->new InvalidProperty(CloudImageParameters.SOURCE_IMAGES_JSON,
          String.format("Cloud profile '%s' already contains image with name '%s'. Please choose another VM or nickname",
                        existingImages.get(id), id)
        )).forEachOrdered(list::add);

      while (iterator.hasNext()) {
        final JsonObject elem = iterator.next().getAsJsonObject();
        final JsonPrimitive srcIdJson = elem.getAsJsonPrimitive(CloudImageParameters.SOURCE_ID_FIELD);
        if (srcIdJson != null){
          if (existingImages.containsKey(srcIdJson.getAsString())){
            final String anotherProfileName = existingImages.get(srcIdJson.getAsString());
            list.add(new InvalidProperty(
              CloudImageParameters.SOURCE_IMAGES_JSON,
              String.format("Cloud profile '%s' already contains image with name '%s'. Please choose another VM or nickname",
                            anotherProfileName, srcIdJson.getAsString())
              )
            );
          }
        }
      }
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
