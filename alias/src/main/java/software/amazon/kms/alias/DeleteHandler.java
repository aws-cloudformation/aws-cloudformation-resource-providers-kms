package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;

public class DeleteHandler extends BaseHandlerStd {
    public DeleteHandler() {
        super();
    }

    public DeleteHandler(final ClientBuilder clientBuilder, final AliasApiHelper aliasApiHelper,
                         final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                             eventualConsistencyHandlerHelper) {
        super(clientBuilder, aliasApiHelper, eventualConsistencyHandlerHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        return ProgressEvent.progress(model, callbackContext)
            .then(
                progress -> proxy.initiate("kms::delete-alias", proxyClient, model, callbackContext)
                    .translateToServiceRequest(Translator::deleteAliasRequest)
                    .makeServiceCall(aliasApiHelper::deleteAlias)
                    .done(deleteAliasResponse -> {
                        logger.log(String
                            .format("%s [%s] has been successfully deleted",
                                ResourceModel.TYPE_NAME,
                                model.getAliasName()));

                        return progress;
                    }))
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(null));
    }
}
