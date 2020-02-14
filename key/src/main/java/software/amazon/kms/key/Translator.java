package software.amazon.kms.key;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.cloudformation.exceptions.TerminalException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Translator {
    private final static String DEFAULT_POLICY_NAME = "default";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    // Create handler
    static CreateKeyRequest createCustomerMasterKey(final ResourceModel resourceModel,
                                                    final Map<String, String > tags) {
        try{
            return CreateKeyRequest.builder()
                    .description(resourceModel.getDescription())
                    .keyUsage(KeyUsageType.fromValue(resourceModel.getKeyUsage()))
                    .policy(MAPPER.writeValueAsString(resourceModel.getKeyPolicy()))
                    .tags(translateTagsToSdk(tags))
                    .build();
        } catch (final JsonProcessingException e) {
            throw new TerminalException(e);
        }
    }

    // Read handler
    static DescribeKeyRequest describeKeyRequest(final String keyId) {
        return DescribeKeyRequest.builder()
                .keyId(keyId)
                .build();
    }

    static GetKeyRotationStatusRequest getKeyRotationStatusRequest(final String keyId) {
        return GetKeyRotationStatusRequest.builder()
                .keyId(keyId)
                .build();
    }

    static GetKeyPolicyRequest getKeyPolicyRequest(final String keyId) {
        return GetKeyPolicyRequest.builder()
                .keyId(keyId)
                .policyName(DEFAULT_POLICY_NAME)
                .build();
    }

    static ListResourceTagsRequest listResourceTagsRequest(final String keyId) {
        return ListResourceTagsRequest.builder().keyId(keyId).build();
    }

    // Update handler
    static EnableKeyRotationRequest enableKeyRotationRequest(final String keyId) {
        return EnableKeyRotationRequest.builder()
                .keyId(keyId).build();
    }

    static DisableKeyRotationRequest disableKeyRotationRequest(final String keyId) {
        return DisableKeyRotationRequest.builder()
                .keyId(keyId).build();
    }

    static UpdateKeyDescriptionRequest updateKeyDescriptionRequest(final String keyId,
                                                                   final String description) {
        return UpdateKeyDescriptionRequest.builder()
                .keyId(keyId)
                .description(description).build();
    }

    static PutKeyPolicyRequest putKeyPolicyRequest(final ResourceModel resourceModel) {
        try {
            return PutKeyPolicyRequest.builder()
                    .keyId(resourceModel.getKeyId())
                    .policyName(DEFAULT_POLICY_NAME)
                    .policy(MAPPER.writeValueAsString(resourceModel.getKeyPolicy()))
                    .build();
        } catch (final JsonProcessingException e) {
            throw new TerminalException(e);
        }
    }

    static UntagResourceRequest untagResourceRequest(final String keyId,
                                                     final Collection<String> tags) {
        return UntagResourceRequest.builder()
                .keyId(keyId)
                .tagKeys(tags).build();
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

    static EnableKeyRequest enableKeyRequest(final String keyId) {
        return EnableKeyRequest.builder()
                .keyId(keyId).build();
    }

    static DisableKeyRequest disableKeyRequest(final String keyId) {
        return DisableKeyRequest.builder()
                .keyId(keyId).build();
    }

    // Translate tags
    static List<Tag> translateTagsToSdk(final Map<String, String> tags) {
        if (tags == null) return null;
        return tags.entrySet()
                .stream()
                .collect(Collectors.mapping(entry ->
                        Tag.builder()
                                .tagKey(entry.getKey())
                                .tagValue(entry.getValue()).build(),
                        Collectors.toList()));
    }

    static Set<software.amazon.kms.key.Tag> translateTagsFromSdk(final List<Tag> tags) {
        if (CollectionUtils.isNullOrEmpty(tags)) return null;
        return tags
                .stream()
                .collect(Collectors.mapping(entry ->
                        software.amazon.kms.key.Tag.builder()
                                .key(entry.tagKey())
                                .value(entry.tagValue())
                .build(), Collectors.toSet()));
    }
}
