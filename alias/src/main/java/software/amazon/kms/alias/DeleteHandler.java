package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    public DeleteHandler() {
        super();
    }

    public DeleteHandler(final AliasHelper aliasHelper) {
        super(aliasHelper);
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
                    .makeServiceCall(aliasHelper::deleteAlias)
                    .done(deleteAliasResponse -> {
                        logger.log(String
                            .format("%s [%s] has been successfully deleted",
                                ResourceModel.TYPE_NAME,
                                model.getAliasName()));

                        return progress;
                    }))
            .then(BaseHandlerStd::propagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(model));
    }
}
