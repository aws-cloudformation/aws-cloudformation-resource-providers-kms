package software.amazon.kms.alias;

import com.amazonaws.util.StringUtils;
import java.util.Optional;
import java.util.function.Predicate;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;


public class ReadHandler extends BaseHandlerStd {
    public ReadHandler() {
        super();
    }

    public ReadHandler(final ClientBuilder clientBuilder, final AliasApiHelper aliasApiHelper,
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
        final Predicate<ResourceModel> predicate =
            resourceModel -> resourceModel.getAliasName().equals(model.getAliasName());

        String marker = null;
        do {
            request.setNextToken(marker);
            final ProgressEvent<ResourceModel, CallbackContext> listModelsResponse =
                new ListHandler(this.clientBuilder, this.aliasApiHelper,
                    this.eventualConsistencyHandlerHelper)
                    .handleRequest(proxy, request, callbackContext, proxyClient, logger);

            final Optional<ResourceModel> targetResourceModel =
                listModelsResponse.getResourceModels().stream()
                    .filter(predicate).findFirst();
            if (targetResourceModel.isPresent()) {
                return ProgressEvent.defaultSuccessHandler(targetResourceModel.get());
            }
            marker = listModelsResponse.getNextToken();
        } while (!StringUtils.isNullOrEmpty(marker));

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .status(OperationStatus.FAILED)
            .errorCode(HandlerErrorCode.NotFound)
            .build();
    }
}
