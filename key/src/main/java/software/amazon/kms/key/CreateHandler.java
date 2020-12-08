package software.amazon.kms.key;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;


import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandlerStd {
    public CreateHandler() {
        super();
    }

    public CreateHandler(final KeyHelper keyHelper) {
        super(keyHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = setDefaults(request.getDesiredResourceState());

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> validateResourceModel(progress, null, model))
            .then(progress -> proxy.initiate("kms::create-key", proxyClient, model, callbackContext)
                .translateToServiceRequest((resourceModel) ->
                    Translator
                        .createCustomerMasterKey(resourceModel, request.getDesiredResourceTags()))
                .makeServiceCall(keyHelper::createKey)
                .done(createKeyResponse -> {
                    if (!StringUtils.isNullOrEmpty(model.getKeyId())) {
                        return ProgressEvent.progress(model, callbackContext);
                    }

                    model.setKeyId(createKeyResponse.keyMetadata().keyId());
                    model.setArn(createKeyResponse.keyMetadata().arn());

                    // Wait for key state to propagate to other hosts
                    return ProgressEvent
                        .defaultInProgressHandler(callbackContext, CALLBACK_DELAY_SECONDS,
                            model);
                })
            )
            .then(progress -> {
                if (model.getEnableKeyRotation()) {
                    // Update key rotation status (Disabled by default)
                    return updateKeyRotationStatus(proxy, proxyClient, model, callbackContext,
                        model.getEnableKeyRotation());
                }
                return progress;
            })
            .then(progress -> {
                if (!model.getEnabled()) {
                    // Update key status (Enabled by default)
                    return updateKeyStatus(proxy, proxyClient, model, callbackContext,
                        model.getEnabled());
                }
                return progress;
            })
            // final propagation to make sure all updates are reflected
            .then(BaseHandlerStd::propagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
