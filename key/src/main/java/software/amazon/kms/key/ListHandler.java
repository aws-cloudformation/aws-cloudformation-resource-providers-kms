package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;
import java.util.stream.Collectors;

import static software.amazon.kms.key.ReadHandler.describeCustomerMasterKey;
import static software.amazon.kms.key.ReadHandler.getKeyMetadata;

public class ListHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ListKeysResponse listKeysResponse = proxy.injectCredentialsAndInvokeV2(Translator.listKeysRequest(request.getNextToken()), ClientBuilder.getClient()::listKeys);

        final List<ResourceModel> models = listKeysResponse
                .keys()
                .stream()
                .collect(Collectors.mapping(key -> describeCustomerMasterKey(
                        getKeyMetadata(proxy, key.keyId()),
                        proxy
                ), Collectors.toList()));

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(models)
                .nextToken(listKeysResponse.nextMarker())
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
