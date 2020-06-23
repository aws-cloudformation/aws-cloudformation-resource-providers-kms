package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.Logger;

import java.util.Map;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;

public class CreateHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
      return ProgressEvent.progress(setDefaults(request.getDesiredResourceState()), callbackContext)
              .then(progress -> proxy.initiate("kms::create-key", proxyClient, setDefaults(request.getDesiredResourceState()), callbackContext)
                      .translateToServiceRequest((resourceModel) -> Translator.createCustomerMasterKey(resourceModel, request.getDesiredResourceTags()))
                      .makeServiceCall((createKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(createKeyRequest, proxyInvocation.client()::createKey))
                      .done((createKeyRequest, createKeyResponse, proxyInvocation, model, context) -> {
                          if (!StringUtils.isNullOrEmpty(model.getKeyId()))
                              return ProgressEvent.progress(model, callbackContext);

                          model.setKeyId(createKeyResponse.keyMetadata().keyId());
                          // Wait for key state to propagate to other hosts
                          return ProgressEvent.defaultInProgressHandler(context, CALLBACK_DELAY_SECONDS, model);
                      })
              )
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
              .then(BaseHandlerStd::propagate)
              .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(progress.getResourceModel())));
    }
}
