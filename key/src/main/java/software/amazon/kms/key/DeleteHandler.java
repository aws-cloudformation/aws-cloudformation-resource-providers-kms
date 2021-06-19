package software.amazon.kms.key;

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

public class DeleteHandler extends BaseHandlerStd {
    public DeleteHandler() {
        super();
    }

    public DeleteHandler(final ClientBuilder clientBuilder,
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

        return keyHandlerHelper.deleteKey(proxy, proxyClient, model, callbackContext);
    }
}
