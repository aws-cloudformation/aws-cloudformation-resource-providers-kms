package software.amazon.kms.key;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.kms.common.CreatableKeyTranslator;

public class Translator extends CreatableKeyTranslator<ResourceModel> {

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
    public KeyUsageType getKeyUsage(final ResourceModel model) {
        return KeyUsageType.fromValue(model.getKeyUsage());
    }

    @Override
    public KeySpec getKeySpec(final ResourceModel model) {
        return KeySpec.fromValue(model.getKeySpec());
    }

    @Override
    public Boolean isMultiRegion(final ResourceModel model) {
        return model.getMultiRegion();
    }

    @Override
    public void setReadOnlyKeyMetadata(final ResourceModel model, final KeyMetadata keyMetadata) {
        model.setArn(keyMetadata.arn());
        model.setKeyId(keyMetadata.keyId());
    }

    @Override
    public void setKeyMetadata(final ResourceModel model, final KeyMetadata keyMetadata) {
        setReadOnlyKeyMetadata(model, keyMetadata);
        model.setDescription(keyMetadata.description());
        model.setEnabled(keyMetadata.enabled());
        model.setKeyUsage(keyMetadata.keyUsageAsString());
        model.setKeySpec(keyMetadata.keySpecAsString());
        model.setMultiRegion(keyMetadata.multiRegion());
    }

    @Override
    public void setKeyPolicy(final ResourceModel model, final Object keyPolicy) {
        model.setKeyPolicy(keyPolicy);
    }

    @Override
    public void setTags(final ResourceModel model, final Set<Tag> tags) {
        model.setTags(translateTagsFromSdk(tags));
    }

    public CreateKeyRequest createAWSKMSKey(final ResourceModel resourceModel,
                                                    final Map<String, String> tags) {
        return CreateKeyRequest.builder()
            .description(resourceModel.getDescription())
            .keyUsage(KeyUsageType.fromValue(resourceModel.getKeyUsage()))
            .keySpec(KeySpec.fromValue(resourceModel.getKeySpec()))
            .policy(translatePolicyInput(resourceModel.getKeyPolicy()))
            .multiRegion(resourceModel.getMultiRegion())
            .tags(translateTagsToSdk(tags))
            .build();
    }

    public GetKeyRotationStatusRequest getKeyRotationStatusRequest(final ResourceModel model) {
        return GetKeyRotationStatusRequest.builder()
            .keyId(model.getKeyId())
            .build();
    }

    public EnableKeyRotationRequest enableKeyRotationRequest(final ResourceModel model) {
        return EnableKeyRotationRequest.builder()
            .keyId(model.getKeyId())
            .build();
    }

    public DisableKeyRotationRequest disableKeyRotationRequest(final ResourceModel model) {
        return DisableKeyRotationRequest.builder()
            .keyId(model.getKeyId())
            .build();
    }

    @Override
    public ResourceModel translateKeyListEntry(KeyListEntry keyListEntry) {
        return ResourceModel.builder().keyId(keyListEntry.keyId()).build();
    }

    private Set<software.amazon.kms.key.Tag> translateTagsFromSdk(
        final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.kms.key.Tag.builder()
                .key(tag.tagKey())
                .value(tag.tagValue())
                .build())
            .collect(Collectors.toSet());
    }
}
