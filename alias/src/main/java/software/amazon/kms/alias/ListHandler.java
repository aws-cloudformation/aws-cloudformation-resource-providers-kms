package software.amazon.kms.alias;

import com.google.common.collect.Lists;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


import java.util.List;
import java.util.stream.Collectors;

public class ListHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        try {
            List<ResourceModel> models = Lists.newArrayList();
            final ListAliasesResponse response = proxy.injectCredentialsAndInvokeV2(
                    Translator.listAliasesRequest(model.getTargetKeyId(), request.getNextToken()),
                    proxyClient.client()::listAliases);
            response.aliases().stream().map(Translator::translateToResourceModel).collect(Collectors.toCollection(() -> models));

            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModels(models)
                    .status(OperationStatus.SUCCESS)
                    .nextToken(response.nextMarker())
                    .build();
        } catch (InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (KmsInternalException e) {
            throw new CfnInternalFailureException(e);
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, e.getMessage());
        }
    }
}
