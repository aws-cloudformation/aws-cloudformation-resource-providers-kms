package software.amazon.kms.key;

import static software.amazon.kms.key.ModelAdapter.setDefaultsForCreateKey;
import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;

import com.google.common.annotations.VisibleForTesting;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Delay;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.CreatableKeyHandlerHelper;
import software.amazon.kms.common.CreatableKeyTranslator;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.TagHelper;

public class CreateHandler extends BaseHandlerStd {
    public CreateHandler() {
        super();
    }

    public CreateHandler(final ClientBuilder clientBuilder,
                         final Translator translator,
                         final KeyApiHelper keyApiHelper,
                         final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                             eventualConsistencyHandlerHelper,
                         final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> keyHandlerHelper,
                         final TagHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> tagHelper) {
        super(clientBuilder, translator, keyApiHelper, eventualConsistencyHandlerHelper,
            keyHandlerHelper, tagHelper);
    }

    @VisibleForTesting
    public CreateHandler(final ClientBuilder clientBuilder,
                         final Translator translator,
                         final KeyApiHelper keyApiHelper,
                         final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                                 eventualConsistencyHandlerHelper,
                         final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> keyHandlerHelper,
                         final TagHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> tagHelper,final Delay stabilizeDelay) {
        super(clientBuilder, translator, keyApiHelper, eventualConsistencyHandlerHelper,
                keyHandlerHelper, tagHelper,stabilizeDelay);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = setDefaultsForCreateKey(request.getDesiredResourceState());

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> validateResourceModel(progress, null, model))
            .then(progress -> keyHandlerHelper.createKey(proxy, proxyClient, model, callbackContext,
                    tagHelper.generateTagsForCreate(request)))
            .then(progress -> updateKeyRotationStatus(proxy, proxyClient, null, model,
                callbackContext))
            .then(progress -> keyHandlerHelper
                .disableKeyIfNecessary(proxy, proxyClient, null, model, callbackContext))
                .then(progress -> eventualConsistencyHandlerHelper.setRequestType(progress,false))
            // Final propagation to make sure all updates are reflected
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
