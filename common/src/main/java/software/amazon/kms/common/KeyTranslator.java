package software.amazon.kms.common;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.TerminalException;

/**
 * An abstract class for translating interactions with our key resource models.
 * This allows us to share logic between the AWS::KMS::Key and AWS::KMS::ReplicaKey resources,
 * which each use separately generated ResourceModel types.
 *
 * @param <M> The CFN resource type's resource model type
 */
public abstract class KeyTranslator<M> {
    protected static final String DEFAULT_POLICY_NAME = "default";
    protected static final int LIST_KEYS_PAGE_SIZE = 50;

    private final ObjectMapper objectMapper;

    protected KeyTranslator() {
        this(new ObjectMapper());
    }

    protected KeyTranslator(final ObjectMapper objectMapper) {
        // So the object mapper can be mocked in our tests
        this.objectMapper = objectMapper;
    }

    public abstract String getKeyId(final M model);

    public abstract String getKeyDescription(final M model);

    public abstract Object getKeyPolicy(final M model);

    public abstract boolean getKeyEnabled(final M model);

    public abstract Integer getPendingWindowInDays(final M model);

    public abstract Boolean isBypassPolicyLockoutSafetyCheck(final M model);

    public abstract void setKeyMetadata(final M model, final KeyMetadata describeKeyResponse);

    public abstract void setKeyPolicy(final M model, final Object keyPolicy);

    public abstract void setTags(final M model, final Set<Tag> tags);

    public DescribeKeyRequest describeKeyRequest(final M model) {
        return DescribeKeyRequest.builder()
            .keyId(getKeyId(model))
            .build();
    }

    public DescribeKeyRequest describeKeyRequest(final String keyId) {
        return DescribeKeyRequest.builder()
            .keyId(keyId)
            .build();
    }

    public ListKeysRequest listKeysRequest(final String marker) {
        return ListKeysRequest.builder()
            .marker(marker)
            .limit(LIST_KEYS_PAGE_SIZE)
            .build();
    }

    public ListResourceTagsRequest listResourceTagsRequest(final M model,
                                                           final String marker) {
        return ListResourceTagsRequest.builder()
            .keyId(getKeyId(model))
            .marker(marker)
            .build();
    }

    public UpdateKeyDescriptionRequest updateKeyDescriptionRequest(final M model) {
        return UpdateKeyDescriptionRequest.builder()
            .keyId(getKeyId(model))
            .description(getKeyDescription(model))
            .build();
    }

    public GetKeyPolicyRequest getKeyPolicyRequest(final M model) {
        return GetKeyPolicyRequest.builder()
            .keyId(getKeyId(model))
            .policyName(DEFAULT_POLICY_NAME)
            .build();
    }

    public PutKeyPolicyRequest putKeyPolicyRequest(final M model) {
        return PutKeyPolicyRequest.builder()
            .bypassPolicyLockoutSafetyCheck(isBypassPolicyLockoutSafetyCheck(model))
            .keyId(getKeyId(model))
            .policyName(DEFAULT_POLICY_NAME)
            .policy(translatePolicyInput(getKeyPolicy(model)))
            .build();
    }

    public EnableKeyRequest enableKeyRequest(final M model) {
        return EnableKeyRequest.builder()
            .keyId(getKeyId(model))
            .build();
    }

    public DisableKeyRequest disableKeyRequest(final M model) {
        return DisableKeyRequest.builder()
            .keyId(getKeyId(model))
            .build();
    }

    public TagResourceRequest tagResourceRequest(final M model, final Set<Tag> tagsToAdd) {
        return TagResourceRequest.builder()
            .keyId(getKeyId(model))
            .tags(tagsToAdd)
            .build();
    }

    public UntagResourceRequest untagResourceRequest(final M model,
                                                     final Set<Tag> tagsToRemove) {
        return UntagResourceRequest.builder()
            .keyId(getKeyId(model))
            .tagKeys(tagsToRemove.stream()
                .map(Tag::tagKey)
                .collect(Collectors.toSet())
            ).build();
    }

    public ScheduleKeyDeletionRequest scheduleKeyDeletionRequest(final M model) {
        return ScheduleKeyDeletionRequest.builder()
            .keyId(getKeyId(model))
            .pendingWindowInDays(getPendingWindowInDays(model))
            .build();
    }

    public abstract M translateKeyListEntry(final KeyListEntry keyListEntry);

    public Set<Tag> translateTagsToSdk(final Map<String, String> tags) {
        if (tags == null) {
            return Collections.emptySet();
        }

        return Optional.of(tags.entrySet()).orElse(Collections.emptySet())
            .stream()
            .map(tag -> Tag.builder().tagKey(tag.getKey()).tagValue(tag.getValue()).build())
            .collect(Collectors.toSet());
    }

    public String translatePolicyInput(final Object policy) {
        // Key Policy can be specified as either a string or an object (JSON)
        // Convert it to a string so it can be used in our API calls
        if (policy instanceof Map) {
            try {
                return objectMapper.writeValueAsString(policy);
            } catch (final JsonProcessingException e) {
                throw new TerminalException(e);
            }
        }

        return (String) policy;
    }

    public Map<String, Object> deserializeKeyPolicy(final String keyPolicy) {
        if (StringUtils.isNullOrEmpty(keyPolicy)) {
            return null;
        }

        try {
            return objectMapper.readValue(keyPolicy, new TypeReference<HashMap<String, Object>>() {
            });
        } catch (final IOException e) {
            throw new CfnInternalFailureException(e);
        }
    }
}
