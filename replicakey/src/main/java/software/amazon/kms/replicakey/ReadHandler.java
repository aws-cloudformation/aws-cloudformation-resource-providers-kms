package software.amazon.kms.replicakey;

import static software.amazon.kms.replicakey.ModelAdapter.unsetWriteOnly;


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

public class ReadHandler extends BaseHandlerStd {
    public ReadHandler() {
        super();
    }

    public ReadHandler(final ClientBuilder clientBuilder,
                       final Translator translator,
                       final KeyApiHelper keyApiHelper,
                       final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                           eventualConsistencyHandlerHelper,
                       final KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> keyHandlerHelper) {
        super(clientBuilder, translator, keyApiHelper, eventualConsistencyHandlerHelper,
            keyHandlerHelper);
    }

    @Override
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
            .then(p -> keyHandlerHelper.getKeyPolicy(proxy, proxyClient, model, callbackContext))
            .then(p -> keyHandlerHelper
                .retrieveResourceTags(proxy, proxyClient, model, callbackContext, true))
            .then(p -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
