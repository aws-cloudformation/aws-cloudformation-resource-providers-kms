package software.amazon.kms.key;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CustomerMasterKeySpec;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    protected static final int CALLBACK_DELAY_SECONDS = 60;

    final KeyHelper keyHelper;

    public BaseHandlerStd() {
        this.keyHelper = new KeyHelper();
    }

    public BaseHandlerStd(final KeyHelper keyHelper) {
        // Allows for mocking key helper in our unit tests
        this.keyHelper = keyHelper;
    }

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
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

    // KMS::Key cannot be immediately deleted, so pending deletion is treated as not found
    protected void resourceStateCheck(final KeyMetadata keyMetadata) {
        if (keyMetadata.keyState() == KeyState.PENDING_DELETION) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, keyMetadata.keyId());
        }
    }

    protected ProgressEvent<ResourceModel, CallbackContext> updateKeyRotationStatus(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ResourceModel model,
        final CallbackContext callbackContext,
        final boolean enabled) {
        if (enabled) {
            return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
                .translateToServiceRequest(Translator::enableKeyRotationRequest)
                .makeServiceCall(keyHelper::enableKeyRotation)
                .progress();
        }

        return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
            .translateToServiceRequest(Translator::disableKeyRotationRequest)
            .makeServiceCall(keyHelper::disableKeyRotation)
            .progress();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> updateKeyStatus(
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
                .makeServiceCall(keyHelper::enableKey)
                // Changing key status from disabled -> enabled might affect rotation update since
                // it's only allowed on enabled keys. If enabled state hasn't been propagated then
                // the rotation update might hit invalid state exception. Wait 1 min to make sure
                // the changes propagate.
                .progress(CALLBACK_DELAY_SECONDS);
        }

        return proxy.initiate("kms::disable-key", proxyClient, model, callbackContext)
            .translateToServiceRequest(Translator::disableKeyRequest)
            .makeServiceCall(keyHelper::disableKey)
            .progress();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> retrieveResourceTags(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progressEvent,
        final boolean softFailOnAccessDenied
    ) {
        final CallbackContext callbackContext = progressEvent.getCallbackContext();
        ProgressEvent<ResourceModel, CallbackContext> progress = progressEvent;
        do { // pagination to make sure that all the tags are retrieved
            final Supplier<ProgressEvent<ResourceModel, CallbackContext>> progressEventSupplier =
                () -> proxy
                    .initiate("kms::list-tag-key:" + callbackContext.getMarker(), proxyClient,
                        progressEvent.getResourceModel(), callbackContext)
                    .translateToServiceRequest((model) -> Translator
                        .listResourceTagsRequest(model, callbackContext.getMarker()))
                    .makeServiceCall(keyHelper::listResourceTags)
                    .done(
                        (listResourceTagsRequest, listResourceTagsResponse, proxyInvocation,
                         resourceModel, context) -> {
                            final Set<Tag> existingTags =
                                Optional.ofNullable(context.getExistingTags())
                                    .orElse(new HashSet<>());
                            existingTags.addAll(new HashSet<>(listResourceTagsResponse.tags()));
                            context.setExistingTags(existingTags);
                            context.setMarker(listResourceTagsResponse.nextMarker());
                            return ProgressEvent.progress(resourceModel, context);
                        });

            // for Read Handler -> soft fail for GetAtt
            progress = softFailOnAccessDenied ?
                softFailAccessDenied(progressEventSupplier, progress.getResourceModel(),
                    progress.getCallbackContext()) : progressEventSupplier.get();
        } while (callbackContext.getMarker() != null);
        return progress;
    }

    // final propagation before stack event is considered completed
    protected static ProgressEvent<ResourceModel, CallbackContext> propagate(
        final ProgressEvent<ResourceModel, CallbackContext> progressEvent
    ) {
        final CallbackContext callbackContext = progressEvent.getCallbackContext();
        if (callbackContext.isPropagated()) {
            return progressEvent;
        }

        callbackContext.setPropagated(true);
        return ProgressEvent.defaultInProgressHandler(callbackContext, CALLBACK_DELAY_SECONDS,
            progressEvent.getResourceModel());
    }

    // Filters out access denied exception (used for Read Handler only)
    protected ProgressEvent<ResourceModel, CallbackContext> softFailAccessDenied(
        final Supplier<ProgressEvent<
            ResourceModel, CallbackContext>> eventSupplier, final ResourceModel model,
        final CallbackContext callbackContext) {
        try {
            return eventSupplier.get();
        } catch (final CfnAccessDeniedException e) {
            return ProgressEvent.progress(model, callbackContext);
        }
    }

    /**
     * A helper method for validating that the requested resource model transition is possible.
     */
    protected static ProgressEvent<ResourceModel, CallbackContext> validateResourceModel(
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final ResourceModel previousModel,
        final ResourceModel model) {
        // If the key is asymmetric, we cannot enable key rotation
        if (!Objects
            .equals(model.getKeySpec(), CustomerMasterKeySpec.SYMMETRIC_DEFAULT.toString())
            && model.getEnableKeyRotation()) {
            throw new CfnInvalidRequestException(
                "You cannot set the EnableKeyRotation property to true on asymmetric keys.");
        }

        // Update specific validation
        if (previousModel != null) {
            // If the key is disabled, and will continue to be, we cannot modify the
            // EnableKeyRotation property
            if (!previousModel.getEnabled() && !model.getEnabled()
                && previousModel.getEnableKeyRotation() != model.getEnableKeyRotation()) {
                throw new CfnInvalidRequestException("You cannot change the EnableKeyRotation "
                    + "property while the Enabled property is false.");
            }

            // If the key usage or spec changes, we need to trigger re-creation
            if (!Objects.equals(previousModel.getKeyUsage(), model.getKeyUsage())
                || !Objects.equals(previousModel.getKeySpec(), model.getKeySpec())) {
                throw new CfnNotUpdatableException(ResourceModel.TYPE_NAME,
                    Objects.toString(model.getKeyId()));
            }
        }

        return progress;
    }
}
