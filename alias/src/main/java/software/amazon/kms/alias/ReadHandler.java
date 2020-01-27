package software.amazon.kms.alias;

import com.amazonaws.util.StringUtils;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Optional;
import java.util.function.Predicate;


public class ReadHandler extends BaseHandler<CallbackContext> {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        final Predicate<ResourceModel> predicate = resourceModel -> resourceModel.getAliasName().equals(model.getAliasName());

        String marker = null;
        do {
            request.setNextToken(marker);
            final ProgressEvent<ResourceModel, CallbackContext> listModelsResponse = new ListHandler().handleRequest(proxy, request, null, logger);

            final Optional<ResourceModel> targetResourceModel = listModelsResponse.getResourceModels().stream()
                    .filter(predicate).findFirst();
            if (targetResourceModel.isPresent()) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(targetResourceModel.get())
                        .status(OperationStatus.SUCCESS)
                        .build();
            }
            marker = listModelsResponse.getNextToken();
        } while (!StringUtils.isNullOrEmpty(marker));

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .status(OperationStatus.FAILED)
                .build();
    }
}
