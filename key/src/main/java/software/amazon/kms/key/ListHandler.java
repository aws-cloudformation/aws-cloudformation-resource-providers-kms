package software.amazon.kms.key;

import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandlerStd {
    public ListHandler() {
        super();
    }

    public ListHandler(final KeyHelper keyHelper) {
        super(keyHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ListKeysResponse listKeysResponse =
            keyHelper.listKeys(Translator.listKeysRequest(request.getNextToken()),
                proxyClient);

        final List<ResourceModel> models = listKeysResponse.keys().stream()
            .map(key -> ResourceModel.builder().keyId(key.keyId()).build())
            .collect(Collectors.toList());

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModels(models)
            .nextToken(listKeysResponse.nextMarker())
            .status(OperationStatus.SUCCESS)
            .build();
    }
}
