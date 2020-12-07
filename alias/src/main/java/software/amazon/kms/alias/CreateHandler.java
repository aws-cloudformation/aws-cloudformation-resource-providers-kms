package software.amazon.kms.alias;

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

    public CreateHandler(final AliasHelper aliasHelper) {
        super(aliasHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        return proxy.initiate("kms::create-alias", proxyClient, model, callbackContext)
                .translateToServiceRequest(Translator::createAliasRequest)
                .makeServiceCall(aliasHelper::createAlias)
                .done(createAliasResponse -> ProgressEvent.defaultSuccessHandler(model));
    }
}
