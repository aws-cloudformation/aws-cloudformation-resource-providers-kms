package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ReadHandler extends BaseHandlerStd {
    private static final String ACCESS_DENIED_ERROR_CODE = "AccessDeniedException";
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
                        .done((describeKeyRequest, describeKeyResponse, proxyInvocation, resourceModel, context) -> {
                            final KeyMetadata keyMetadata = describeKeyResponse.keyMetadata();
                            final ResourceModel desiredState = ResourceModel.builder().build();

                            notFoundCheck(keyMetadata);

                            desiredState.setArn(keyMetadata.arn());
                            desiredState.setKeyId(keyMetadata.keyId());
                            desiredState.setDescription(keyMetadata.description());
                            desiredState.setEnabled(keyMetadata.enabled());
                            return ProgressEvent.progress(desiredState, context);
                        })
                )
                .then(progress -> proxy.initiate("kms::get-key-policy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest((model) -> Translator.getKeyPolicyRequest(model.getKeyId()))
                        .makeServiceCall((getKeyPolicyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(getKeyPolicyRequest, proxyInvocation.client()::getKeyPolicy))
                        .handleError(this::accessDenied)
                        .done((getKeyPolicyRequest, getKeyPolicyResponse, proxyInvocation, resourceModel, context) -> {
                            resourceModel.setKeyPolicy(deserializePolicyKey(getKeyPolicyResponse.policy()));
                            return ProgressEvent.progress(resourceModel, context);
                        })
                )
                .then(progress -> proxy.initiate("kms::get-key-rotation-status", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::getKeyRotationStatusRequest)
                        .makeServiceCall((getKeyRotationStatusRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(getKeyRotationStatusRequest, proxyInvocation.client()::getKeyRotationStatus))
                        .handleError(this::accessDenied)
                        .done((getKeyRotationStatusRequest, getKeyRotationStatusResponse, proxyInvocation, resourceModel, context) -> {
                            resourceModel.setEnableKeyRotation(getKeyRotationStatusResponse.keyRotationEnabled());
                            return ProgressEvent.progress(resourceModel, context);
                        })
                )
                .then(progress -> proxy.initiate("kms::list-tags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::listResourceTagsRequest)
                        .makeServiceCall((listResourceTagsRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(listResourceTagsRequest, proxyInvocation.client()::listResourceTags))
                        .handleError(this::accessDenied)
                        .done((listResourceTagsRequest, listResourceTagsResponse, proxyInvocation, resourceModel, context) -> {
                            resourceModel.setTags(Translator.translateTagsFromSdk(listResourceTagsResponse.tags()));
                            return ProgressEvent.progress(resourceModel, context);
                        })
                )
                .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
    }

    private ProgressEvent<ResourceModel, CallbackContext> accessDenied(AwsRequest awsRequest, Exception e, ProxyClient<KmsClient> proxyClient, ResourceModel model, CallbackContext context) {
        if (e instanceof KmsException && ((KmsException)e).awsErrorDetails().errorCode().equals(ACCESS_DENIED_ERROR_CODE)) {
            return ProgressEvent.progress(model, context);
        }
        throw new CfnGeneralServiceException(e);
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
