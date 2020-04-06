package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler  extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
      final String keyId = request.getDesiredResourceState().getKeyId();

      return proxy.initiate("kms::delete-key", proxyClient, request.getDesiredResourceState(), callbackContext)
          .request(Translator::scheduleKeyDeletionRequest)
          .call((scheduleKeyDeletionRequest, proxyInvocation) -> {
                  try {
                      return proxyInvocation.injectCredentialsAndInvokeV2(scheduleKeyDeletionRequest,
                          proxyInvocation.client()::scheduleKeyDeletion);
                  } catch (KmsInvalidStateException e) { // if key was scheduled for deletion outside of the stack
                    return null;
                  }
              }
          )
          .stabilize((scheduleKeyDeletionRequest, scheduleKeyDeletionResponse, proxyInvocation, model, context) ->
              isDeleted(proxyInvocation, model))
          .success();
    }

    private boolean isDeleted(
        final ProxyClient<KmsClient> proxyClient,
        final ResourceModel model
    ) {
        final KeyState keyState = proxyClient.injectCredentialsAndInvokeV2(Translator.describeKeyRequest(model), proxyClient.client()::describeKey).keyMetadata().keyState();
        return keyState.equals(KeyState.PENDING_DELETION);
    }
}
