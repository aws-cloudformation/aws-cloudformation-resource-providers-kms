package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.Logger;

import java.util.Map;

import static software.amazon.kms.key.ModelAdapter.setDefaults;

public class CreateHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

      final Map<String, String> tags = request.getDesiredResourceTags();

      return proxy.initiate("kms::create-custom-key", proxyClient, setDefaults(request.getDesiredResourceState()), callbackContext)
          .request((resourceModel) -> Translator.createCustomerMasterKey(resourceModel, tags))
          .call((createKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(createKeyRequest, proxyInvocation.client()::createKey))
          .done((createKeyRequest, createKeyResponse, proxyInvocation, model, context) -> {
            model.setKeyId(createKeyResponse.keyMetadata().keyId());
            return ProgressEvent.progress(model, context);
          })
          // propagate key state to notify other hosts
          .then(BaseHandlerStd::proparateState)
          .then(progress -> {
            if(progress.getResourceModel().getEnableKeyRotation()) // update key rotation status (by default is disabled)
              updateKeyRotationStatus(proxy, proxyClient, progress.getResourceModel(), progress.getCallbackContext(), true);
            return progress;
          })
          .then(progress -> {
            if(!progress.getResourceModel().getEnabled()) // update key status (by default is enabled)
              return updateKeyStatus(proxy, proxyClient, progress.getResourceModel(), progress.getCallbackContext(), false);
            return progress;
          })
          // final propagation to make sure all updates are reflected
          .then(BaseHandlerStd::proparateResource)
          .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}
