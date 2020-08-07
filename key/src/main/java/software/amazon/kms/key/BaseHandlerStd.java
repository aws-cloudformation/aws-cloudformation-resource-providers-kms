package software.amazon.kms.key;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext>  {

  protected static final int CALLBACK_DELAY_SECONDS = 60;
  private static final String ACCESS_DENIED_ERROR_CODE = "AccessDeniedException";

  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final Logger logger) {
    return handleRequest(
        proxy,
        request,
        callbackContext != null ? callbackContext : new CallbackContext(),
        proxy.newProxy(ClientBuilder::getClient),
        logger);
  }

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      AmazonWebServicesClientProxy proxy,
      ResourceHandlerRequest<ResourceModel> request,
      CallbackContext callbackContext,
      ProxyClient<KmsClient> proxyClient,
      Logger logger);

  protected void notFoundCheck(final KeyMetadata keyMetadata) {
    if (keyMetadata.keyState() == KeyState.PENDING_DELETION)
      throw new ResourceNotFoundException(ResourceModel.TYPE_NAME, keyMetadata.keyId());
  }

  protected static ProgressEvent<ResourceModel, CallbackContext> updateKeyRotationStatus(
      final AmazonWebServicesClientProxy proxy,
      final ProxyClient<KmsClient> proxyClient,
      final ResourceModel model,
      final CallbackContext callbackContext,
      final boolean enabled) {
    if (enabled) {
      return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
          .translateToServiceRequest(Translator::enableKeyRotationRequest)
          .makeServiceCall((enableKeyRotationRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(enableKeyRotationRequest, proxyInvocation.client()::enableKeyRotation))
          .progress();
    }

    return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
        .translateToServiceRequest(Translator::disableKeyRotationRequest)
        .makeServiceCall((disableKeyRotationRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(disableKeyRotationRequest, proxyInvocation.client()::disableKeyRotation))
        .progress();
  }

  public static ProgressEvent<ResourceModel, CallbackContext> updateKeyStatus(
      final AmazonWebServicesClientProxy proxy,
      final ProxyClient<KmsClient> proxyClient,
      final ResourceModel model,
      final CallbackContext callbackContext,
      final boolean enabled
  ) {
    if (enabled) {
      callbackContext.setKeyEnabled(true);
      return proxy.initiate("kms::enable-key", proxyClient, model, callbackContext)
          .translateToServiceRequest(Translator::enableKeyRequest)
          .makeServiceCall((enableKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(enableKeyRequest, proxyInvocation.client()::enableKey))
          .progress(CALLBACK_DELAY_SECONDS);
          // changing key status from disabled -> enabled might affect rotation update since it's only possible on enabled key
          // if enabled state hasn't been propagated then rotation update might hit invalid state exception
          // 1 min is required for state propagation
    }

    return proxy.initiate("kms::disable-key", proxyClient, model, callbackContext)
        .translateToServiceRequest(Translator::disableKeyRequest)
        .makeServiceCall((disableKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(disableKeyRequest, proxyInvocation.client()::disableKey))
        .progress();
  }

  protected static ProgressEvent<ResourceModel, CallbackContext> retrieveResourceTags(final AmazonWebServicesClientProxy proxy, final ProxyClient<KmsClient> proxyClient, final ProgressEvent<ResourceModel, CallbackContext> progressEvent) {
      CallbackContext context = progressEvent.getCallbackContext();
      ProgressEvent<ResourceModel, CallbackContext> progressEvt = progressEvent;
      do {
        progressEvt = proxy.initiate("kms::list-tag-key:" + context.getMarker(), proxyClient, progressEvent.getResourceModel(), context)
                .translateToServiceRequest((model) -> Translator.listResourceTagsRequest2(model, context.getMarker()))
                .makeServiceCall((listResourceTagsRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(listResourceTagsRequest, proxyInvocation.client()::listResourceTags))
                .handleError(BaseHandlerStd::accessDenied)
                .done((listResourceTagsRequest, listResourceTagsResponse, proxyInvocation, resourceModel, cxt) -> {
                  final Set<Tag> existingTags = cxt.getExistingTags() == null ? new HashSet<>() : new HashSet<>(cxt.getExistingTags());
                  existingTags.addAll(new HashSet<>(listResourceTagsResponse.tags()));
                  cxt.setExistingTags(existingTags);
                  cxt.setMarker(listResourceTagsResponse.nextMarker());
                  return ProgressEvent.success(resourceModel, cxt);
                });
        if (!progressEvt.isSuccess())
          return progressEvt;

      } while (context.getMarker() != null);
      return progressEvt;
  }

  // final propagation before stack event is considered completed
  protected static ProgressEvent<ResourceModel, CallbackContext> propagate(final ProgressEvent<ResourceModel, CallbackContext> progressEvent) {
    final CallbackContext callbackContext = progressEvent.getCallbackContext();
    if (callbackContext.isPropagated()) return progressEvent;

    callbackContext.setPropagated(true);
    return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY_SECONDS, progressEvent.getResourceModel());
  }

  protected static ProgressEvent<ResourceModel, CallbackContext> accessDenied(AwsRequest awsRequest, Exception e, ProxyClient<KmsClient> proxyClient, ResourceModel model, CallbackContext context) {
    if (e instanceof KmsException && ((KmsException)e).awsErrorDetails().errorCode().equals(ACCESS_DENIED_ERROR_CODE)) {
      return ProgressEvent.progress(model, context);
    }
    throw new CfnGeneralServiceException(e);
  }
}
