package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    public DeleteHandler() {
        super();
    }

    public DeleteHandler(final KeyHelper keyHelper) {
        super(keyHelper);
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
                try {
                    return proxy.initiate("kms::delete-key", proxyClient, model, callbackContext)
                        .translateToServiceRequest(Translator::scheduleKeyDeletionRequest)
                        .makeServiceCall(keyHelper::scheduleKeyDeletion)
                        .stabilize(this::isDeleted)
                        .done(scheduleKeyDeletionResponse -> progress);
                } catch (final CfnInvalidRequestException e) {
                    if (e.getCause() instanceof KmsInvalidStateException) {
                        // Invalid state can only happen if the key is pending deletion,
                        // treat it as not found.
                        return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
                    }

                    throw e;
                }
            })
            .then(BaseHandlerStd::propagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(null));
    }

    private boolean isDeleted(final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest,
                              final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse,
                              final ProxyClient<KmsClient> proxyClient,
                              final ResourceModel resourceModel,
                              final CallbackContext callbackContext) {
        final KeyState keyState =
            keyHelper.describeKey(Translator.describeKeyRequest(resourceModel), proxyClient)
                .keyMetadata().keyState();
        return keyState.equals(KeyState.PENDING_DELETION);
    }
}
