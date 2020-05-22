package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ReadHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

        return proxy.initiate("kms::describe-key", proxyClient, request.getDesiredResourceState(), callbackContext)
            .translateToServiceRequest(Translator::describeKeyRequest)
            .makeServiceCall((describeKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(describeKeyRequest, proxyInvocation.client()::describeKey))
            .done((describeKeyRequest, describeKeyResponse, proxyInvocation, resourceModel, context) -> {
                final KeyMetadata keyMetadata = describeKeyResponse.keyMetadata();

                notFoundCheck(keyMetadata);

                final String arn = keyMetadata.arn();
                final String keyId = keyMetadata.keyId();
                final String description = keyMetadata.description();
                final Boolean enabled = keyMetadata.enabled();

                final GetKeyPolicyResponse getKeyPolicyResponse = proxyInvocation.injectCredentialsAndInvokeV2(Translator.getKeyPolicyRequest(keyId), proxyInvocation.client()::getKeyPolicy);
                final Map<String, Object> policyKey = deserializePolicyKey(getKeyPolicyResponse.policy());
                final boolean keyRotationEnabled = proxyInvocation.injectCredentialsAndInvokeV2(Translator.getKeyRotationStatusRequest(resourceModel), proxyInvocation.client()::getKeyRotationStatus).keyRotationEnabled();

                final ListResourceTagsResponse listResourceTagsResponse = proxyInvocation.injectCredentialsAndInvokeV2(Translator.listResourceTagsRequest(resourceModel), proxyInvocation.client()::listResourceTags);
                final Set<Tag> tags = Translator.translateTagsFromSdk(listResourceTagsResponse.tags());
                return ProgressEvent.defaultSuccessHandler(ResourceModel.builder()
                    .arn(arn)
                    .keyId(keyId)
                    .description(description)
                    .enabled(enabled)
                    .keyPolicy(policyKey)
                    .enableKeyRotation(keyRotationEnabled)
                    .keyUsage(keyMetadata.keyUsageAsString())
                    .tags(tags).build());
            });
    }

    public static Map<String, Object> deserializePolicyKey(final String policyKey) { // serializing key policy
        if (StringUtils.isNullOrEmpty(policyKey)) return null;
        try {
            return Translator.MAPPER.readValue(policyKey, new TypeReference<HashMap<String,Object>>() {});
        } catch (final IOException e) {
            throw new CfnInternalFailureException(e);
        }
    }
}
