package software.amazon.kms.key;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CustomerMasterKeySpec;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.exceptions.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

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

  // As KMS::Key cannot be immediately deleted, pending deletion state is treated as not found state
  protected void resourceStateCheck(final KeyMetadata keyMetadata) {
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

  protected static ProgressEvent<ResourceModel, CallbackContext> retrieveResourceTags(
          final AmazonWebServicesClientProxy proxy,
          final ProxyClient<KmsClient> proxyClient,
          final ProgressEvent<ResourceModel, CallbackContext> progressEvent,
          final boolean softFailOnAccessDenied // filtering out access denied permission issue (soft fail on Read and hard fail on Update)
  ) {
      final CallbackContext callbackContext = progressEvent.getCallbackContext();
      ProgressEvent<ResourceModel, CallbackContext> progress = progressEvent;
      do { // pagination to make sure that all the tags are retrieved
          progress = proxy.initiate("kms::list-tag-key:" + callbackContext.getMarker(), proxyClient, progressEvent.getResourceModel(), callbackContext)
                .translateToServiceRequest((model) -> Translator.listResourceTagsRequest(model, callbackContext.getMarker()))
                .makeServiceCall((listResourceTagsRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(listResourceTagsRequest, proxyInvocation.client()::listResourceTags))
                .handleError((listResourceTagsRequest, exception, proxyInvocation, resourceModel, context) -> {
                    if (softFailOnAccessDenied) // for Read Handler -> soft fail for GetAtt
                        return BaseHandlerStd.handleAccessDenied(listResourceTagsRequest, exception, proxyInvocation, resourceModel, context);
                    throw exception; // hard fail for update
                })
                .done((listResourceTagsRequest, listResourceTagsResponse, proxyInvocation, resourceModel, context) -> {
                    final Set<Tag> existingTags = Optional.ofNullable(context.getExistingTags()).orElse(new HashSet<>());
                    existingTags.addAll(new HashSet<>(listResourceTagsResponse.tags()));
                    context.setExistingTags(existingTags);
                    context.setMarker(listResourceTagsResponse.nextMarker());
                    return ProgressEvent.progress(resourceModel, context);
                });
      } while (callbackContext.getMarker() != null);
      return progress;
  }

  // final propagation before stack event is considered completed
  protected static ProgressEvent<ResourceModel, CallbackContext> propagate(
          final ProgressEvent<ResourceModel, CallbackContext> progressEvent
  ) {
    final CallbackContext callbackContext = progressEvent.getCallbackContext();
    if (callbackContext.isPropagated()) return progressEvent;

    callbackContext.setPropagated(true);
    return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY_SECONDS, progressEvent.getResourceModel());
  }

  // lambda to filter out access denied exception (used for Read Handler only)
  protected static ProgressEvent<ResourceModel, CallbackContext> handleAccessDenied(
          final AwsRequest awsRequest,
          final Exception e,
          final ProxyClient<KmsClient> proxyClient,
          final ResourceModel model,
          final CallbackContext context
  ) {
    if (e instanceof KmsException && ((KmsException)e).awsErrorDetails().errorCode().equals(ACCESS_DENIED_ERROR_CODE))
      return ProgressEvent.progress(model, context);
    return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.GeneralServiceException);
  }

  // by default not found exception has the same error code as invalid request, hence mapping it correctly
  protected static ProgressEvent<ResourceModel, CallbackContext> handleNotFound(
          final AwsRequest awsRequest,
          final Exception e,
          final ProxyClient<KmsClient> proxyClient,
          final ResourceModel model,
          final CallbackContext context
  ) {
      if (e instanceof software.amazon.awssdk.services.kms.model.NotFoundException)
          return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
      return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.GeneralServiceException);
    }

    /**
     * A helper method for validating that the requested resource model transition is possible
     */
    protected static ProgressEvent<ResourceModel, CallbackContext> validateResourceModel(
            final ProgressEvent<ResourceModel, CallbackContext> progress, final ResourceModel previousModel,
            final ResourceModel model) {
        // If the key is asymmetric, we cannot enable key rotation
        if (!Objects.equals(model.getKeySpec(), CustomerMasterKeySpec.SYMMETRIC_DEFAULT.toString()) &&
                model.getEnableKeyRotation())
            throw new CfnInvalidRequestException("You cannot set the EnableKeyRotation property to true on asymmetric keys.");

        // Update specific validation
        if (previousModel != null) {
            // If the key is disabled, and will continue to be, we cannot modify the EnableKeyRotation property
            if (!previousModel.getEnabled() && !model.getEnabled() &&
                    previousModel.getEnableKeyRotation() != model.getEnableKeyRotation()) {
                throw new CfnInvalidRequestException("You cannot change the EnableKeyRotation property while the Enabled property is false.");
            }

            // If the key usage or spec changes, we need to trigger re-creation
            if (!Objects.equals(previousModel.getKeyUsage(), model.getKeyUsage()) ||
                    !Objects.equals(previousModel.getKeySpec(), model.getKeySpec())) {
                throw new CfnNotUpdatableException(ResourceModel.TYPE_NAME, Objects.toString(model.getKeyId()));
            }
        }

        return progress;
    }
}
