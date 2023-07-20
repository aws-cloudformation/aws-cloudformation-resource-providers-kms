package software.amazon.kms.key;


import java.util.Objects;

import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;


import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.CreatableKeyHandlerHelper;
import software.amazon.kms.common.CreatableKeyTranslator;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;

public class ReadHandler extends BaseHandlerStd {
    public ReadHandler() {
        super();
    }

    public ReadHandler(final ClientBuilder clientBuilder,
                       final Translator translator,
                       final KeyApiHelper keyApiHelper,
                       final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                           eventualConsistencyHandlerHelper,
                       final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> keyHandlerHelper) {
        super(clientBuilder, translator, keyApiHelper, eventualConsistencyHandlerHelper,
            keyHandlerHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = request.getDesiredResourceState();

        return ProgressEvent.progress(model, callbackContext)
            // Describe the key, and update our resource model
            .then(
                p -> keyHandlerHelper.describeKey(proxy, proxyClient, model, callbackContext, true))
            // Retrieving the key policy can potentially cause an access denied exception
            .then(p -> softFailAccessDenied(() -> keyHandlerHelper
                .getKeyPolicy(proxy, proxyClient, model, callbackContext), model, callbackContext))
            // Retrieving the rotation status can potentially cause an access denied exception
            .then(p -> softFailAccessDenied(() -> {
                if (!Objects.equals(model.getOrigin(), OriginType.EXTERNAL.toString())) {
                    return proxy
                        .initiate("kms::get-key-rotation-status", proxyClient, model, callbackContext)
                        .translateToServiceRequest(translator::getKeyRotationStatusRequest)
                        .makeServiceCall(keyApiHelper::getKeyRotationStatus)
                        .done(getKeyRotationStatusResponse -> {
                            model.setEnableKeyRotation(getKeyRotationStatusResponse.keyRotationEnabled());
                            return ProgressEvent.progress(model, callbackContext);
                        });
                }
                model.setEnableKeyRotation(false);
                return ProgressEvent.progress(model, callbackContext);
            }, model, callbackContext))
            // Retrieving the tags can potentially cause an access denied exception, fail gracefully
            .then(p -> softFailAccessDenied(() -> keyHandlerHelper
                .retrieveResourceTags(proxy, proxyClient, model, callbackContext, true),
                model, callbackContext))
                // !!! WARNING !!! Make sure to update unsetWriteOnly when you add a new property
                // which is not a WriteOnly property or contract tests will break
            .then(p -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
