package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
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
      final String keyId = request.getDesiredResourceState().getKeyId();

      return proxy.initiate("kms::delete-key", proxyClient, request.getDesiredResourceState(), callbackContext)
          .translateToServiceRequest(Translator::scheduleKeyDeletionRequest)
          .makeServiceCall((scheduleKeyDeletionRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(scheduleKeyDeletionRequest, proxyInvocation.client()::scheduleKeyDeletion))
          .stabilize((scheduleKeyDeletionRequest, scheduleKeyDeletionResponse, proxyInvocation, model, context) -> isDeleted(proxyInvocation, model))
          .handleError((scheduleKeyDeletionRequest, exception, proxyInvocation, resourceModel, context) -> {
            if (exception instanceof KmsInvalidStateException) // invalid state can only happen if resource is pending deletion, hence treating as not found
              return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
            throw exception;
          })
          .done((scheduleKeyDeletionRequest, scheduleKeyDeletionResponse, client, model, context) -> ProgressEvent.defaultSuccessHandler(null));
    }

    private boolean isDeleted(
        final ProxyClient<KmsClient> proxyClient,
        final ResourceModel model
    ) {
        final KeyState keyState = proxyClient.injectCredentialsAndInvokeV2(Translator.describeKeyRequest(model), proxyClient.client()::describeKey).keyMetadata().keyState();
        return keyState.equals(KeyState.PENDING_DELETION);
    }
}
