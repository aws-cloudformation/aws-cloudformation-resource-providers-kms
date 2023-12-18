package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateAliasResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;

public class CreateHandler extends BaseHandlerStd {
    public CreateHandler() {
        super();
    }

    public CreateHandler(final ClientBuilder clientBuilder, final AliasApiHelper aliasApiHelper,
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
            .then(progress -> {
                if (progress.getCallbackContext().isPreCreateCheckDone()) {
                    return progress;
                } else {
                    return proxy.initiate("kms::describe-key", proxyClient, model, progress.getCallbackContext())
                            .translateToServiceRequest(resourceModel ->
                                    DescribeKeyRequest.builder().keyId(resourceModel.getAliasName()).build())
                            .makeServiceCall((describeKeyRequest, client) -> {
                                boolean aliasExists;
                                try {
                                    aliasApiHelper.describeKey(describeKeyRequest, client);
                                    aliasExists = true;
                                } catch (final CfnNotFoundException e) {
                                    aliasExists = false;
                                } catch (final CfnAccessDeniedException e) {
                                    aliasExists = true;
                                }

                                if (aliasExists) {
                                    throw new CfnAlreadyExistsException("AWS::KMS::Alias", model.getAliasName());
                                } else {
                                    progress.getCallbackContext().setPreCreateCheckDone(true);
                                    return DescribeKeyResponse.builder().build();
                                }
                            }).progress(1);
                }
            })
            .then(
                progress -> proxy.initiate("kms::create-alias", proxyClient, model, progress.getCallbackContext())
                    .translateToServiceRequest(Translator::createAliasRequest)
                    .makeServiceCall((createAliasRequest, client) -> {
                        try {
                            return aliasApiHelper.createAlias(createAliasRequest, client);
                        } catch (final CfnAlreadyExistsException e) {
                            // We already confirmed the alias did not exist on initial run of handler. If it exists
                            // now, then this must be a retry of CreateAlias.
                            return CreateAliasResponse.builder().build();
                        }
                    }).done(createAliasResponse -> {
                        logger.log(String
                            .format("%s [%s] has been successfully created",
                                ResourceModel.TYPE_NAME,
                                model.getAliasName()));

                        return progress;
                    }))
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(model));
    }
}
