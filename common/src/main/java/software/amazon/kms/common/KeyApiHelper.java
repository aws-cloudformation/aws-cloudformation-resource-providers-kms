package software.amazon.kms.common;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.ReplicateKeyRequest;
import software.amazon.awssdk.services.kms.model.ReplicateKeyResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.TagResourceResponse;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceResponse;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionResponse;
import software.amazon.cloudformation.proxy.ProxyClient;

/**
 * Implementation of AbstractKmsApiHelper that supports KMS Key Operations.
 */
public class KeyApiHelper extends AbstractKmsApiHelper {
    static final String THROTTLING_ERROR_CODE = "ThrottlingException";
    static final String ACCESS_DENIED_ERROR_CODE = "AccessDeniedException";
    static final String VALIDATION_ERROR_CODE = "ValidationException";

    private static final String CREATE_KEY = "CreateKey";
    private static final String REPLICATE_KEY = "ReplicateKey";
    private static final String DISABLE_KEY = "DisableKey";
    private static final String ENABLE_KEY = "EnableKey";
    private static final String DISABLE_KEY_ROTATION = "DisableKeyRotation";
    private static final String ENABLE_KEY_ROTATION = "EnableKeyRotation";
    private static final String GET_KEY_POLICY = "GetKeyPolicy";
    private static final String GET_KEY_ROTATION_STATUS = "GetKeyRotationStatus";
    private static final String LIST_KEYS = "ListKeys";
    private static final String LIST_RESOURCE_TAGS = "ListResourceTags";
    private static final String PUT_KEY_POLICY = "PutKeyPolicy";
    private static final String SCHEDULE_KEY_DELETION = "ScheduleKeyDeletion";
    private static final String TAG_RESOURCE = "TagResource";
    private static final String UNTAG_RESOURCE = "UntagResource";
    private static final String UPDATE_KEY_DESCRIPTION = "UpdateKeyDescription";

    public CreateKeyResponse createKey(final CreateKeyRequest createKeyRequest,
                                       final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(CREATE_KEY,
            () -> proxyClient.injectCredentialsAndInvokeV2(createKeyRequest,
                proxyClient.client()::createKey));
    }

    public ReplicateKeyResponse replicateKey(final ReplicateKeyRequest replicateKeyRequest,
                                             final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(REPLICATE_KEY,
            () -> proxyClient.injectCredentialsAndInvokeV2(replicateKeyRequest,
                proxyClient.client()::replicateKey));
    }

    public DisableKeyResponse disableKey(final DisableKeyRequest disableKeyRequest,
                                         final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(DISABLE_KEY,
            () -> proxyClient.injectCredentialsAndInvokeV2(disableKeyRequest,
                proxyClient.client()::disableKey));
    }

    public EnableKeyResponse enableKey(final EnableKeyRequest enableKeyRequest,
                                       final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(ENABLE_KEY,
            () -> proxyClient.injectCredentialsAndInvokeV2(enableKeyRequest,
                proxyClient.client()::enableKey));
    }

    public DisableKeyRotationResponse disableKeyRotation(
        final DisableKeyRotationRequest disableKeyRotationRequest,
        final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(DISABLE_KEY_ROTATION,
            () -> proxyClient.injectCredentialsAndInvokeV2(
                disableKeyRotationRequest, proxyClient.client()::disableKeyRotation));
    }

    public EnableKeyRotationResponse enableKeyRotation(
        final EnableKeyRotationRequest enableKeyRotationRequest,
        final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(ENABLE_KEY_ROTATION,
            () -> proxyClient.injectCredentialsAndInvokeV2(
                enableKeyRotationRequest, proxyClient.client()::enableKeyRotation));
    }

    public GetKeyPolicyResponse getKeyPolicy(final GetKeyPolicyRequest getKeyPolicyRequest,
                                             final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(GET_KEY_POLICY,
            () -> proxyClient.injectCredentialsAndInvokeV2(getKeyPolicyRequest,
                proxyClient.client()::getKeyPolicy));
    }

    public GetKeyRotationStatusResponse getKeyRotationStatus(
        final GetKeyRotationStatusRequest getKeyRotationStatusRequest,
        final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(GET_KEY_ROTATION_STATUS,
            () -> proxyClient.injectCredentialsAndInvokeV2(
                getKeyRotationStatusRequest, proxyClient.client()::getKeyRotationStatus));
    }

    public ListKeysResponse listKeys(final ListKeysRequest listKeysRequest,
                                     final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(LIST_KEYS, () -> proxyClient.injectCredentialsAndInvokeV2(
            listKeysRequest, proxyClient.client()::listKeys));
    }

    public ListResourceTagsResponse listResourceTags(
        final ListResourceTagsRequest listResourceTagsRequest,
        final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(LIST_RESOURCE_TAGS, () -> proxyClient.injectCredentialsAndInvokeV2(
            listResourceTagsRequest, proxyClient.client()::listResourceTags));
    }

    public PutKeyPolicyResponse putKeyPolicy(final PutKeyPolicyRequest putKeyPolicyRequest,
                                             final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(PUT_KEY_POLICY, () -> proxyClient.injectCredentialsAndInvokeV2(
            putKeyPolicyRequest, proxyClient.client()::putKeyPolicy));
    }

    public ScheduleKeyDeletionResponse scheduleKeyDeletion(
        final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest,
        final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(SCHEDULE_KEY_DELETION,
            () -> proxyClient.injectCredentialsAndInvokeV2(
                scheduleKeyDeletionRequest, proxyClient.client()::scheduleKeyDeletion));
    }

    public TagResourceResponse tagResource(final TagResourceRequest tagResourceRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(TAG_RESOURCE, () -> proxyClient.injectCredentialsAndInvokeV2(
            tagResourceRequest, proxyClient.client()::tagResource));
    }

    public UntagResourceResponse untagResource(final UntagResourceRequest untagResourceRequest,
                                               final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(UNTAG_RESOURCE, () -> proxyClient.injectCredentialsAndInvokeV2(
            untagResourceRequest, proxyClient.client()::untagResource));
    }

    public UpdateKeyDescriptionResponse updateKeyDescription(
        final UpdateKeyDescriptionRequest updateKeyDescriptionRequest,
        final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(UPDATE_KEY_DESCRIPTION,
            () -> proxyClient.injectCredentialsAndInvokeV2(
                updateKeyDescriptionRequest, proxyClient.client()::updateKeyDescription));
    }
}
