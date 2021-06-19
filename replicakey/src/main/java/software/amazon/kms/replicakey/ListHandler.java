package software.amazon.kms.replicakey;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MultiRegionKeyType;
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

public class ListHandler extends BaseHandlerStd {
    public ListHandler() {
        super();
    }

    public ListHandler(final ClientBuilder clientBuilder,
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

        // List all multi-region replica keys
        return keyHandlerHelper.listKeysAndFilterByMetadata(proxyClient, request.getNextToken(),
            keyMetadata -> keyMetadata.multiRegion() &&
                keyMetadata.multiRegionConfiguration().multiRegionKeyType()
                    .equals(MultiRegionKeyType.REPLICA));
    }
}
