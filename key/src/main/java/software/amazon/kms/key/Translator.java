package software.amazon.kms.key;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Optional;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.TerminalException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Translator {
    private final static String DEFAULT_POLICY_NAME = "default";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    // Create handler
    static CreateKeyRequest createCustomerMasterKey(final ResourceModel resourceModel,
                                                    final Map<String, String > tags) {
        return CreateKeyRequest.builder()
                .description(resourceModel.getDescription())
                .keyUsage(KeyUsageType.fromValue(resourceModel.getKeyUsage()))
                .policy(translatePolicyInput(resourceModel.getKeyPolicy()))
                .tags(translateTagsToSdk(tags))
                .build();
    }

    // Read handler
    static DescribeKeyRequest describeKeyRequest(final ResourceModel model) {
        return DescribeKeyRequest.builder()
                .keyId(model.getKeyId())
                .build();
    }

    static GetKeyRotationStatusRequest getKeyRotationStatusRequest(final ResourceModel model) {
        return GetKeyRotationStatusRequest.builder()
                .keyId(model.getKeyId())
                .build();
    }

    static GetKeyPolicyRequest getKeyPolicyRequest(final String keyId) {
        return GetKeyPolicyRequest.builder()
                .keyId(keyId)
                .policyName(DEFAULT_POLICY_NAME)
                .build();
    }

    static ListResourceTagsRequest listResourceTagsRequest(final ResourceModel model) {
        return ListResourceTagsRequest.builder().keyId(model.getKeyId()).build();
    }

    // Update handler
    static EnableKeyRotationRequest enableKeyRotationRequest(final ResourceModel model) {
        return EnableKeyRotationRequest.builder()
                .keyId(model.getKeyId()).build();
    }

    static DisableKeyRotationRequest disableKeyRotationRequest(final ResourceModel model) {
        return DisableKeyRotationRequest.builder()
                .keyId(model.getKeyId()).build();
    }

    static UpdateKeyDescriptionRequest updateKeyDescriptionRequest(final ResourceModel model) {
        return UpdateKeyDescriptionRequest.builder()
                .keyId(model.getKeyId())
                .description(model.getDescription()).build();
    }

    static PutKeyPolicyRequest putKeyPolicyRequest(final ResourceModel resourceModel) {
        return PutKeyPolicyRequest.builder()
                .keyId(resourceModel.getKeyId())
                .policyName(DEFAULT_POLICY_NAME)
                .policy(translatePolicyInput(resourceModel.getKeyPolicy()))
                .build();
    }

    static String translatePolicyInput(final Object policy) {
        if (policy instanceof Map){
            try {
                return MAPPER.writeValueAsString(policy);
            } catch (final JsonProcessingException e) {
                throw new TerminalException(e);
            }
        }
        return (String)policy;
    }

    static UntagResourceRequest untagResourceRequest(final String keyId,
                                                     final Set<Tag> tags) {
        return UntagResourceRequest.builder()
                .keyId(keyId)
                .tagKeys(
                    tags
                        .stream()
                        .map(Tag::tagKey)
                        .collect(Collectors.toSet())
                ).build();
    }

    static TagResourceRequest tagResourceRequest(final String keyId,
                                                 final Collection<Tag> tags) {
        return TagResourceRequest.builder()
                .keyId(keyId)
                .tags(tags).build();
    }

    // Delete handler
    static ScheduleKeyDeletionRequest scheduleKeyDeletionRequest(final ResourceModel resourceModel) {
        return ScheduleKeyDeletionRequest.builder()
                .keyId(resourceModel.getKeyId())
                .pendingWindowInDays(resourceModel.getPendingWindowInDays()).build();
    }

    // List handler
    static ListKeysRequest listKeysRequest(final String marker) {
        return ListKeysRequest.builder()
                .marker(marker)
                .build();
    }

    static EnableKeyRequest enableKeyRequest(final ResourceModel model) {
        return EnableKeyRequest.builder()
                .keyId(model.getKeyId()).build();
    }

    static DisableKeyRequest disableKeyRequest(final ResourceModel model) {
        return DisableKeyRequest.builder()
                .keyId(model.getKeyId()).build();
    }

    // Translate tags

    static Set<Tag> translateTagsToSdk(final Map<String, String> tags) {
        if (tags == null) return Collections.emptySet();
        return Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(tag -> Tag.builder().tagKey(tag.getKey()).tagValue(tag.getValue()).build())
            .collect(Collectors.toSet());
    }

    static Set<software.amazon.kms.key.Tag> translateTagsFromSdk(final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
            .stream()
            .map(tag -> software.amazon.kms.key.Tag.builder()
                .key(tag.tagKey())
                .value(tag.tagValue())
                .build())
            .collect(Collectors.toSet());
    }

    static Set<software.amazon.kms.key.Tag> mapToTags(final Map<String, String> tags) {
        return Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(entry -> software.amazon.kms.key.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
            .collect(Collectors.toSet());
    }
}
