package software.amazon.kms.replicakey;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.ReplicateKeyRequest;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.kms.common.KeyTranslator;

public class Translator extends KeyTranslator<ResourceModel> {

    @Override
    public String getKeyId(final ResourceModel model) {
        return model.getKeyId();
    }

    @Override
    public String getKeyDescription(final ResourceModel model) {
        return model.getDescription();
    }

    @Override
    public Object getKeyPolicy(final ResourceModel model) {
        return model.getKeyPolicy();
    }

    @Override
    public boolean getKeyEnabled(final ResourceModel model) {
        return model.getEnabled();
    }

    @Override
    public Integer getPendingWindowInDays(final ResourceModel model) {
        return model.getPendingWindowInDays();
    }

    @Override
    public Boolean isBypassPolicyLockoutSafetyCheck(final ResourceModel model) {
        // Return BypassPolicyLockoutSafetyCheck value when ReplicaKey supports this.
        // TODO: https://sim.amazon.com/issues/KMSFACE-9300
        return null;
    }

    @Override
    public void setKeyMetadata(final ResourceModel model, final KeyMetadata keyMetadata) {
        model.setArn(keyMetadata.arn());
        model.setKeyId(keyMetadata.keyId());
        model.setDescription(keyMetadata.description());
        model.setEnabled(keyMetadata.enabled());
        model.setPrimaryKeyArn(keyMetadata.multiRegionConfiguration().primaryKey().arn());
    }

    @Override
    public void setKeyPolicy(final ResourceModel model, final Object keyPolicy) {
        model.setKeyPolicy(keyPolicy);
    }

    @Override
    public void setTags(final ResourceModel model, final Set<Tag> tags) {
        model.setTags(translateTagsFromSdk(tags));
    }

    public ReplicateKeyRequest replicateKeyRequest(final ResourceModel model,
                                                   final String replicaRegion,
                                                   final Map<String, String> tags) {
        return ReplicateKeyRequest.builder()
            .keyId(model.getPrimaryKeyArn())
            .replicaRegion(replicaRegion)
            .description(model.getDescription())
            .policy(translatePolicyInput(model.getKeyPolicy()))
            .tags(translateTagsToSdk(tags))
            .build();
    }

    @Override
    public ResourceModel translateKeyListEntry(final KeyListEntry keyListEntry) {
        return ResourceModel.builder().keyId(keyListEntry.keyId()).build();
    }

    private static Set<software.amazon.kms.replicakey.Tag> translateTagsFromSdk(
        final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.kms.replicakey.Tag.builder()
                .key(tag.tagKey())
                .value(tag.tagValue())
                .build())
            .collect(Collectors.toSet());
    }
}
