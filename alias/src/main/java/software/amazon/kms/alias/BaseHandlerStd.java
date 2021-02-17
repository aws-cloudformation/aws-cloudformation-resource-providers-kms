package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext>  {
  protected static final int CALLBACK_DELAY_SECONDS = 60;

  final AliasHelper aliasHelper;

  public BaseHandlerStd() {
    this(new AliasHelper());
  }

  public BaseHandlerStd(final AliasHelper aliasHelper) {
    // Allows for mocking alias helper in our unit tests
    this.aliasHelper = aliasHelper;
  }

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

  /**
   * Perform the final propagation delay to make sure the latest
   * changes to the alias are available throughout the region.
   */
  protected static ProgressEvent<ResourceModel, CallbackContext> propagate(
      final ProgressEvent<ResourceModel, CallbackContext> progressEvent) {
    final CallbackContext callbackContext = progressEvent.getCallbackContext();
    if (callbackContext.isPropagated()) {
      return progressEvent;
    }

    callbackContext.setPropagated(true);
    return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY_SECONDS,
            progressEvent.getResourceModel());
  }
}
