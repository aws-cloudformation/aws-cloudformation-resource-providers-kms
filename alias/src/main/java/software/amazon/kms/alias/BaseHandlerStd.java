package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    final ClientBuilder clientBuilder;
    final AliasApiHelper aliasApiHelper;
    final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;

    public BaseHandlerStd() {
        this(new ClientBuilder(), new AliasApiHelper(), new EventualConsistencyHandlerHelper<>());
    }

    public BaseHandlerStd(final ClientBuilder clientBuilder, final AliasApiHelper aliasApiHelper,
                          final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                              eventualConsistencyHandlerHelper) {
        // Allows for mocking helpers in our unit tests
        this.clientBuilder = clientBuilder;
        this.aliasApiHelper = aliasApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
    }

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {
        return handleRequest(
            proxy,
            request,
            callbackContext != null ? callbackContext : new CallbackContext(),
            proxy.newProxy(clientBuilder::getClient),
            logger);
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        AmazonWebServicesClientProxy proxy,
        ResourceHandlerRequest<ResourceModel> request,
        CallbackContext callbackContext,
        ProxyClient<KmsClient> proxyClient,
        Logger logger);
}
