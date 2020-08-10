package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ReadHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> proxy.initiate("kms::describe-key", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::describeKeyRequest)
                        .makeServiceCall((describeKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(describeKeyRequest, proxyInvocation.client()::describeKey))
                        .handleError(BaseHandlerStd::handleNotFound)
                        .done((describeKeyRequest, describeKeyResponse, proxyInvocation, resourceModel, context) -> {
                            final KeyMetadata keyMetadata = describeKeyResponse.keyMetadata();
                            final ResourceModel desiredState = ResourceModel.builder().build();

                            resourceStateCheck(keyMetadata);

                            desiredState.setArn(keyMetadata.arn());
                            desiredState.setKeyId(keyMetadata.keyId());
                            desiredState.setDescription(keyMetadata.description());
                            desiredState.setEnabled(keyMetadata.enabled());
                            desiredState.setKeyUsage(keyMetadata.keyUsageAsString());
                            return ProgressEvent.progress(desiredState, context);
                        })
                )
                .then(progress -> proxy.initiate("kms::get-key-policy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest((model) -> Translator.getKeyPolicyRequest(model.getKeyId()))
                        .makeServiceCall((getKeyPolicyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(getKeyPolicyRequest, proxyInvocation.client()::getKeyPolicy))
                        .handleError(BaseHandlerStd::handleAccessDenied) // retrieving key policy with potentially access denied exception
                        .done((getKeyPolicyRequest, getKeyPolicyResponse, proxyInvocation, resourceModel, context) -> {
                            resourceModel.setKeyPolicy(deserializePolicyKey(getKeyPolicyResponse.policy()));
                            return ProgressEvent.progress(resourceModel, context);
                        })
                )
                .then(progress -> proxy.initiate("kms::get-key-rotation-status", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::getKeyRotationStatusRequest)
                        .makeServiceCall((getKeyRotationStatusRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(getKeyRotationStatusRequest, proxyInvocation.client()::getKeyRotationStatus))
                        .handleError(BaseHandlerStd::handleAccessDenied) // retrieving key rotation with potentially access denied exception
                        .done((getKeyRotationStatusRequest, getKeyRotationStatusResponse, proxyInvocation, resourceModel, context) -> {
                            resourceModel.setEnableKeyRotation(getKeyRotationStatusResponse.keyRotationEnabled());
                            return ProgressEvent.progress(resourceModel, context);
                        })
                )
                .then(progress -> BaseHandlerStd.retrieveResourceTags(proxy, proxyClient, progress, true)) // retrieving tags with potentially access denied exception
                .then(progress -> {
                    final ResourceModel model = progress.getResourceModel();
                    final CallbackContext context = progress.getCallbackContext();
                    if (!CollectionUtils.isEmpty(context.getExistingTags()))
                        model.setTags(Translator.translateTagsFromSdk(context.getExistingTags()));
                    return ProgressEvent.defaultSuccessHandler(model);
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
