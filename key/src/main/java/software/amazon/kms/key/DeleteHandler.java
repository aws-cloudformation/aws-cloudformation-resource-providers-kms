package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = request.getDesiredResourceState();

        return proxy.initiate("kms::delete-key", proxyClient, model, callbackContext)
            .translateToServiceRequest(Translator::scheduleKeyDeletionRequest)
            .makeServiceCall((scheduleKeyDeletionRequest, proxyInvocation) ->
                proxyInvocation.injectCredentialsAndInvokeV2(scheduleKeyDeletionRequest,
                    proxyInvocation.client()::scheduleKeyDeletion))
            .stabilize(this::isDeleted)
            .handleError(
                (scheduleKeyDeletionRequest, exception, proxyInvocation, resourceModel,
                    context) -> {
                    // Invalid state can only happen if the key is pending deletion,
                    // treat it as not found.
                    if (exception instanceof KmsInvalidStateException) {
                        return ProgressEvent
                            .defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                    }
                    throw exception;
                })
            .done(scheduleKeyDeletionResponse -> ProgressEvent.defaultSuccessHandler(null));
    }

    private boolean isDeleted(final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest,
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse,
        final ProxyClient<KmsClient> proxyClient,
        final ResourceModel resourceModel, final CallbackContext callbackContext) {
        final KeyState keyState =
            proxyClient.injectCredentialsAndInvokeV2(Translator.describeKeyRequest(resourceModel),
                proxyClient.client()::describeKey).keyMetadata().keyState();
        return keyState.equals(KeyState.PENDING_DELETION);
    }
}
