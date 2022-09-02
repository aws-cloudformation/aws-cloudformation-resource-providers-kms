package software.amazon.kms.replicakey;


import static software.amazon.kms.replicakey.ModelAdapter.setDefaults;
import static software.amazon.kms.replicakey.ModelAdapter.unsetWriteOnly;


import java.util.Map;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.KeyHandlerHelper;
import software.amazon.kms.common.KeyTranslator;
import software.amazon.kms.common.TagHelper;

public class UpdateHandler extends BaseHandlerStd {
    public UpdateHandler() {
        super();
    }

    public UpdateHandler(final ClientBuilder clientBuilder,
                         final Translator translator,
                         final KeyApiHelper keyApiHelper,
                         final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                             eventualConsistencyHandlerHelper,
                         final KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> keyHandlerHelper,
                         final TagHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> tagHelper) {
        super(clientBuilder, translator, keyApiHelper, eventualConsistencyHandlerHelper,
            keyHandlerHelper, tagHelper);
    }

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = setDefaults(request.getDesiredResourceState());
        final ResourceModel previousModel = setDefaults(request.getPreviousResourceState());
        final Map<String, String> tags = tagHelper.getNewDesiredTags(request);

        return ProgressEvent.progress(model, callbackContext)
            // Describe the key (without updating the model) to verify that it has not been deleted
            .then(progress -> keyHandlerHelper
                .describeKey(proxy, proxyClient, model, callbackContext, false))
            // Key status does not affect any of these other updates, so there is no need to wait
            .then(progress -> keyHandlerHelper
                .enableKeyIfNecessary(proxy, proxyClient, previousModel, model, callbackContext,
                    false))
            .then(progress -> keyHandlerHelper
                .disableKeyIfNecessary(proxy, proxyClient, previousModel, model, callbackContext))
            .then(progress -> keyHandlerHelper
                .updateKeyDescription(proxy, proxyClient, previousModel, model, callbackContext))
            .then(progress -> keyHandlerHelper
                .updateKeyPolicy(proxy, proxyClient, previousModel, model, callbackContext))
            .then(progress -> tagHelper
                .updateKeyTags(proxy, proxyClient, model, request, callbackContext, tags))
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
