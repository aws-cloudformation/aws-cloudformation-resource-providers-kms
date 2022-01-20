package software.amazon.kms.key;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;


import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.kms.KmsClient;
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

public class UpdateHandler extends BaseHandlerStd {
    public UpdateHandler() {
        super();
    }

    public UpdateHandler(final ClientBuilder clientBuilder,
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
        final ResourceModel model = setDefaults(request.getDesiredResourceState());
        final ResourceModel previousModel = setDefaults(request.getPreviousResourceState());
        final Map<String, String> tags = request.getDesiredResourceTags();

        return ProgressEvent.progress(model, callbackContext)
            // Describe the key (without updating the model) to verify that it has not been deleted
            .then(progress -> keyHandlerHelper
                .describeKey(proxy, proxyClient, model, callbackContext, false))
            .then(progress -> validateResourceModel(progress, previousModel, model))
            .then(progress -> keyHandlerHelper
                .enableKeyIfNecessary(proxy, proxyClient, previousModel, model, callbackContext,
                    true))
            .then(progress -> updateKeyRotationStatus(proxy, proxyClient, previousModel, model,
                callbackContext))
            .then(progress -> keyHandlerHelper
                .disableKeyIfNecessary(proxy, proxyClient, previousModel, model, callbackContext))
            .then(progress -> keyHandlerHelper
                .updateKeyDescription(proxy, proxyClient, previousModel, model, callbackContext))
            .then(progress -> keyHandlerHelper
                .updateKeyPolicy(proxy, proxyClient, previousModel, model, callbackContext))
            .then(progress -> {
                if (!Objects.equals(tags, request.getPreviousResourceTags())) {
                    // Customer is attempting to change tags, no soft fail
                    return keyHandlerHelper
                        .updateKeyTags(proxy, proxyClient, model, tags, callbackContext);
                } else {
                    // Customer did not explicitly request a tag update, fixing the drift
                    return softFailAccessDenied(() -> keyHandlerHelper
                        .updateKeyTags(proxy, proxyClient, model, tags, callbackContext),
                            model, callbackContext);
                }
            })
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
