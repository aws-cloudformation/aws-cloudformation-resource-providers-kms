package software.amazon.kms.key;

import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotUpdatableException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.CreatableKeyHandlerHelper;
import software.amazon.kms.common.CreatableKeyTranslator;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    final ClientBuilder clientBuilder;
    final Translator translator;
    final KeyApiHelper keyApiHelper;
    final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>>
        keyHandlerHelper;
    final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;

    public BaseHandlerStd() {
        this.clientBuilder = new ClientBuilder();
        this.translator = new Translator();
        this.keyApiHelper = new KeyApiHelper();
        this.eventualConsistencyHandlerHelper = new EventualConsistencyHandlerHelper<>();
        this.keyHandlerHelper =
            new CreatableKeyHandlerHelper<>(ResourceModel.TYPE_NAME, keyApiHelper,
                eventualConsistencyHandlerHelper, translator);
    }

    public BaseHandlerStd(final ClientBuilder clientBuilder,
                          final Translator translator,
                          final KeyApiHelper keyApiHelper,
                          final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                              eventualConsistencyHandlerHelper,
                          final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> keyHandlerHelper) {
        // Allows for mocking helpers in our unit tests
        this.clientBuilder = clientBuilder;
        this.translator = translator;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyHandlerHelper = keyHandlerHelper;
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
            proxy.newProxy(clientBuilder::getClient),
            logger);
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        AmazonWebServicesClientProxy proxy,
        ResourceHandlerRequest<ResourceModel> request,
        CallbackContext callbackContext,
        ProxyClient<KmsClient> proxyClient,
        Logger logger);

    protected ProgressEvent<ResourceModel, CallbackContext> updateKeyRotationStatus(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ResourceModel previousModel,
        final ResourceModel model,
        final CallbackContext callbackContext) {
        final boolean shouldBeEnabled = model.getEnableKeyRotation();
        final boolean wasEnabled = previousModel != null && previousModel.getEnableKeyRotation();

        if (!wasEnabled && shouldBeEnabled) {
            return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
                .translateToServiceRequest(translator::enableKeyRotationRequest)
                .makeServiceCall(keyApiHelper::enableKeyRotation)
                .progress();
        } else if (wasEnabled && !shouldBeEnabled) {
            return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
                .translateToServiceRequest(translator::disableKeyRotationRequest)
                .makeServiceCall(keyApiHelper::disableKeyRotation)
                .progress();
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    /**
     * Filters out access denied errors. This is used to maintain backwards compatibility.
     * Features like tagging were added after the initial implementation of AWS::KMS::Key,
     * and existing permissions may deny the use of these newer features.
     */
    protected ProgressEvent<ResourceModel, CallbackContext> softFailAccessDenied(
        final Supplier<ProgressEvent<ResourceModel, CallbackContext>> eventSupplier,
        final ResourceModel model,
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
                .equals(model.getKeySpec(), KeySpec.SYMMETRIC_DEFAULT.toString())
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

            // If the key usage or spec or multi-region value changes,
            // we need to trigger re-creation
            if (!Objects.equals(previousModel.getKeyUsage(), model.getKeyUsage())
                || !Objects.equals(previousModel.getKeySpec(), model.getKeySpec())
                || !Objects.equals(previousModel.getMultiRegion(), model.getMultiRegion())) {
                throw new CfnNotUpdatableException(ResourceModel.TYPE_NAME,
                    Objects.toString(model.getKeyId()));
            }
        }

        return progress;
    }
}
