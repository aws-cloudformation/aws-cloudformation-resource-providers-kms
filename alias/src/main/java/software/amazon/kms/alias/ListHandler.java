package software.amazon.kms.alias;

import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;

public class ListHandler extends BaseHandlerStd {
    public ListHandler() {
        super();
    }

    public ListHandler(final ClientBuilder clientBuilder, final AliasApiHelper aliasApiHelper,
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

        return proxy.initiate("kms::list-aliases", proxyClient, model, callbackContext)
            .translateToServiceRequest(
                m -> Translator.listAliasesRequest(m, request.getNextToken()))
            .makeServiceCall(aliasApiHelper::listAliases)
            .done(listAliasesResponse -> ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(listAliasesResponse.aliases().stream()
                    .map(Translator::translateToResourceModel)
                    .collect(Collectors.toList()))
                .status(OperationStatus.SUCCESS)
                .nextToken(listAliasesResponse.nextMarker())
                .build());
    }
}
