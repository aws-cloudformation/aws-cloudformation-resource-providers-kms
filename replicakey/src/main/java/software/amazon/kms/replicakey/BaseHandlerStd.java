package software.amazon.kms.replicakey;

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

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    final ClientBuilder clientBuilder;
    final Translator translator;
    final KeyApiHelper keyApiHelper;
    final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;
    final KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>>
        keyHandlerHelper;
    final TagHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> tagHelper;

    public BaseHandlerStd() {
        this.clientBuilder = new ClientBuilder();
        this.translator = new Translator();
        this.keyApiHelper = new KeyApiHelper();
        this.eventualConsistencyHandlerHelper = new EventualConsistencyHandlerHelper<>();
        this.keyHandlerHelper =
            new KeyHandlerHelper<>(ResourceModel.TYPE_NAME, keyApiHelper,
                eventualConsistencyHandlerHelper, translator);
        this.tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
    }

    public BaseHandlerStd(final ClientBuilder clientBuilder,
                          final Translator translator,
                          final KeyApiHelper keyApiHelper,
                          final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                              eventualConsistencyHandlerHelper,
                          final KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> keyHandlerHelper) {
        // Allows for mocking helpers in our unit tests
        this.clientBuilder = clientBuilder;
        this.translator = translator;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyHandlerHelper = keyHandlerHelper;
        this.tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
    }

    public BaseHandlerStd(final ClientBuilder clientBuilder,
                         final Translator translator,
                         final KeyApiHelper keyApiHelper,
                         final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                             eventualConsistencyHandlerHelper,
                         final KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> keyHandlerHelper,
                         final TagHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> tagHelper) {
        this.clientBuilder = clientBuilder;
        this.translator = translator;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyHandlerHelper = keyHandlerHelper;
        this.tagHelper = tagHelper;
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
