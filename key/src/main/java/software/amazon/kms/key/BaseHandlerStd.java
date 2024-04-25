package software.amazon.kms.key;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnUnauthorizedTaggingOperationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Delay;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.CreatableKeyHandlerHelper;
import software.amazon.kms.common.CreatableKeyTranslator;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.TagHelper;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    final ClientBuilder clientBuilder;
    final Translator translator;
    final KeyApiHelper keyApiHelper;
    final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>>
        keyHandlerHelper;
    final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;
    final TagHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> tagHelper;

    private final Delay stabilizeDelay;

    public BaseHandlerStd() {
        this.clientBuilder = new ClientBuilder();
        this.translator = new Translator();
        this.keyApiHelper = new KeyApiHelper();
        this.eventualConsistencyHandlerHelper = new EventualConsistencyHandlerHelper<>();
        this.keyHandlerHelper =
            new CreatableKeyHandlerHelper<>(ResourceModel.TYPE_NAME, keyApiHelper,
                eventualConsistencyHandlerHelper, translator);
        this.tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
        this.stabilizeDelay = keyHandlerHelper.BACKOFF_STRATEGY;
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
        this.tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
        this.stabilizeDelay = keyHandlerHelper.BACKOFF_STRATEGY;
    }

    public BaseHandlerStd(final ClientBuilder clientBuilder,
        final Translator translator,
        final KeyApiHelper keyApiHelper,
        final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                eventualConsistencyHandlerHelper,
        final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> keyHandlerHelper,
        final TagHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> tagHelper) {
        // Allows for mocking helpers in our unit tests
        this.clientBuilder = clientBuilder;
        this.translator = translator;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyHandlerHelper = keyHandlerHelper;
        this.tagHelper = tagHelper;
        this.stabilizeDelay = keyHandlerHelper.BACKOFF_STRATEGY;
    }

    @VisibleForTesting
    public BaseHandlerStd(final ClientBuilder clientBuilder,
                          final Translator translator,
                          final KeyApiHelper keyApiHelper,
                          final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                                  eventualConsistencyHandlerHelper,
                          final CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> keyHandlerHelper,
                          final TagHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> tagHelper, final Delay stabilizeDelay) {
        // Allows for mocking helpers in our unit tests
        this.clientBuilder = clientBuilder;
        this.translator = translator;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyHandlerHelper = keyHandlerHelper;
        this.tagHelper = tagHelper;
        this.stabilizeDelay = stabilizeDelay != null ? stabilizeDelay : keyHandlerHelper.BACKOFF_STRATEGY;
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

        if ((!wasEnabled && shouldBeEnabled) || ((wasEnabled && shouldBeEnabled) &&
            (!Objects.equals(model.getRotationPeriodInDays(), previousModel.getRotationPeriodInDays())))) {
            return proxy.initiate("kms::update-key-rotation", proxyClient, model, callbackContext)
                .translateToServiceRequest(translator::enableKeyRotationRequest)
                    .backoffDelay(stabilizeDelay)
                    .makeServiceCall((enableKeyRotationRequest, enableKeyRotationProxyClient) -> {
                        try {
                            return keyApiHelper.enableKeyRotation((EnableKeyRotationRequest) enableKeyRotationRequest, enableKeyRotationProxyClient);
                        } catch (Exception e) {
                            if (e instanceof CfnNotFoundException) {
                                throw NotFoundException.builder()
                                        .message(e.getMessage())
                                        .cause(e.getCause())
                                        .build();
                            }
                            throw e;
                        }
                    })
                    .retryErrorFilter((_req, ex, _client, _model, _cb) -> ex instanceof NotFoundException)
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
        } catch (final CfnAccessDeniedException | CfnUnauthorizedTaggingOperationException e) {
            return ProgressEvent.progress(model, callbackContext);
        }
    }

    public static final String CFN_INVALID_REQUEST_MESSAGE = "You cannot change the values of the KeySpec, "
            + "KeyUsage, Origin, or MultiRegion properties of an AWS::KMS::Key resource.";

    /**
     * A helper method for validating that the requested resource model transition is possible.
     */
    protected static ProgressEvent<ResourceModel, CallbackContext> validateResourceModel(
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final ResourceModel previousModel,
        final ResourceModel model) {
        // If the key is asymmetric or external, we cannot enable key rotation
        if ((!Objects.equals(model.getKeySpec(), KeySpec.SYMMETRIC_DEFAULT.toString()) ||
            Objects.equals(model.getOrigin(), OriginType.EXTERNAL.toString()))
            && model.getEnableKeyRotation()) {
            throw new CfnInvalidRequestException(
                "You cannot set the EnableKeyRotation property to true on asymmetric or external keys.");
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

            // If the key usage, spec, origin or multi-region value changes,
            // The update fails, CfnInvalidRequestException is thrown
            if (!Objects.equals(previousModel.getKeyUsage(), model.getKeyUsage())
                || !Objects.equals(previousModel.getKeySpec(), model.getKeySpec())
                || !Objects.equals(previousModel.getMultiRegion(), model.getMultiRegion())
                || !Objects.equals(previousModel.getOrigin(), model.getOrigin())) {
                throw new CfnInvalidRequestException(CFN_INVALID_REQUEST_MESSAGE);
            }
        }

        return progress;
    }
}
