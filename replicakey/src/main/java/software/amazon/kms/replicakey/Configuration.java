package software.amazon.kms.replicakey;

import com.amazonaws.util.CollectionUtils;
import java.util.Map;
import java.util.stream.Collectors;

class Configuration extends BaseConfiguration {
    public Configuration() {
        super("aws-kms-replicakey.json");
    }

    public Map<String, String> resourceDefinedTags(final ResourceModel resourceModel) {
        if (CollectionUtils.isNullOrEmpty(resourceModel.getTags())) {
            return null;
        }

        return resourceModel.getTags()
            .stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (value1, value2) -> value2));
    }
}
