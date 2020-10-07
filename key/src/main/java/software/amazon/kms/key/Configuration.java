package software.amazon.kms.key;

import com.amazonaws.util.CollectionUtils;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.json.JSONTokener;

class Configuration extends BaseConfiguration {

    public Configuration() {
        super("aws-kms-key.json");
    }

    public JSONObject resourceSchemaJsonObject() {
        return new JSONObject(
            new JSONTokener(this.getClass().getClassLoader().getResourceAsStream(schemaFilename)));
    }

    public Map<String, String> resourceDefinedTags(final ResourceModel resourceModel) {
        if (CollectionUtils.isNullOrEmpty(resourceModel.getTags())) {
            return null;
        }

        return resourceModel.getTags()
            .stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }
}
