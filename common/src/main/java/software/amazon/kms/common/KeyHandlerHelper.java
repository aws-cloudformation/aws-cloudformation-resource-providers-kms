package software.amazon.kms.common;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Delay;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.delay.CappedExponential;

/**
 * A helper class for performing common KMS Key operations that are
 * used by both the AWS::KMS::Key and AWS::KMS::ReplicaKey resource types.
 *
 * @param <M> The CFN resource type's resource model type
 * @param <C> The CFN resource type's callback context type
 * @param <T> The KeyTranslator<M> used to translate the resource model
 */
public class KeyHandlerHelper<M, C extends KeyCallbackContext, T extends KeyTranslator<M>> {
    final String typeName;
    final KeyApiHelper keyApiHelper;
    final EventualConsistencyHandlerHelper<M, C> eventualConsistencyHandlerHelper;
    final T keyTranslator;

    private final Delay stabilizeDelay;

    //Exponential retry strategy for operation
    public final Delay BACKOFF_STRATEGY =
            CappedExponential.of()
                    .minDelay(Duration.ofSeconds(1))
                    .maxDelay(Duration.ofSeconds(5))
                    .powerBy(1.3)
                    .timeout(Duration.ofSeconds(60))
                    .build();

    public KeyHandlerHelper(final String typeName,
                            final KeyApiHelper keyApiHelper,
                            final EventualConsistencyHandlerHelper<M, C> eventualConsistencyHandlerHelper,
                            final T keyTranslator) {
        this.typeName = typeName;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyTranslator = keyTranslator;
        this.stabilizeDelay = BACKOFF_STRATEGY;
    }

    @VisibleForTesting
    public KeyHandlerHelper(final String typeName,
                            final KeyApiHelper keyApiHelper,
                            final EventualConsistencyHandlerHelper<M, C> eventualConsistencyHandlerHelper,
                            final T keyTranslator, final Delay stabilizeDelay) {
        this.typeName = typeName;
        this.keyApiHelper = keyApiHelper;
        this.eventualConsistencyHandlerHelper = eventualConsistencyHandlerHelper;
        this.keyTranslator = keyTranslator;
        this.stabilizeDelay = stabilizeDelay != null ? stabilizeDelay : BACKOFF_STRATEGY;
    }

    /**
     * Describes a KMS key. If the key is pending deletion, a CfnNotFoundException is thrown.
     * If the updateResourceModel parameter is true, the model will be updated with the
     * key metadata from the describe key response.
     */
    public ProgressEvent<M, C> describeKey(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M model,
        final C callbackContext,
        final boolean updateResourceModel
    ) {
        return proxy.initiate("kms::describe-key", proxyClient, model, callbackContext)
            .translateToServiceRequest(keyTranslator::describeKeyRequest)
            .makeServiceCall(keyApiHelper::describeKey)
            .done(describeKeyResponse -> {
                final KeyMetadata keyMetadata = describeKeyResponse.keyMetadata();
                if (keyMetadata.keyState() == KeyState.PENDING_DELETION ||
                    keyMetadata.keyState() == KeyState.PENDING_REPLICA_DELETION) {
                    throw new CfnNotFoundException(typeName, keyMetadata.keyId());
                }

                if (updateResourceModel) {
                    keyTranslator.setKeyMetadata(model, keyMetadata);
                }

                return ProgressEvent.progress(model, callbackContext);
            });
    }

    /**
     * Gets a KMS key's policy and updates the resource model.
     */
    public ProgressEvent<M, C> getKeyPolicy(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M model,
        final C callbackContext
    ) {
        return proxy.initiate("kms::get-key-policy", proxyClient, model, callbackContext)
            .translateToServiceRequest(keyTranslator::getKeyPolicyRequest)
            .makeServiceCall(keyApiHelper::getKeyPolicy)
            .done(getKeyPolicyResponse -> {
                keyTranslator.setKeyPolicy(model,
                    keyTranslator.deserializeKeyPolicy(getKeyPolicyResponse.policy()));

                return ProgressEvent.progress(model, callbackContext);
            });
    }

    /**
     * Update a KMS key's description.
     * No updates are made unless the new description differs from the previous one.
     */
    public ProgressEvent<M, C> updateKeyDescription(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M previousModel,
        final M model,
        final C callbackContext
    ) {
        if (!keyTranslator.getKeyDescription(previousModel)
            .equals(keyTranslator.getKeyDescription(model))) {
            return proxy.initiate("kms::update-key-description", proxyClient, model,
                callbackContext)
                .translateToServiceRequest(keyTranslator::updateKeyDescriptionRequest)
                .makeServiceCall(keyApiHelper::updateKeyDescription)
                .progress();
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    /**
     * Update a KMS key's policy and waits for it to propagate throughout the region.
     * No updates are made unless the new policy differs from the previous one.
     */
    public ProgressEvent<M, C> updateKeyPolicy(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M previousModel,
        final M model,
        final C callbackContext
    ) {
        final String previousKeyPolicy =
            keyTranslator.translatePolicyInput(keyTranslator.getKeyPolicy(previousModel));
        final String currentKeyPolicy =
            keyTranslator.translatePolicyInput(keyTranslator.getKeyPolicy(model));
        if (!previousKeyPolicy.equals(currentKeyPolicy) && !callbackContext
            .isKeyPolicyUpdated()) { // context carries policy propagation status
            callbackContext.setKeyPolicyUpdated(true);
            return proxy
                .initiate("kms::update-key-keypolicy", proxyClient, model, callbackContext)
                .translateToServiceRequest(keyTranslator::putKeyPolicyRequest)
                .makeServiceCall(keyApiHelper::putKeyPolicy)
                .progress(EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS);
            // This requires some propagation delay because the updated policy
            // might provision new permissions which are required by the next events
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    /**
     * Enables a KMS key.
     * If the useEventualConsistencyDelay parameter is true, wait until the updates propagate
     * throughout the region. No updates are made if the key was already enabled.
     */
    public ProgressEvent<M, C> enableKeyIfNecessary(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M previousModel,
        final M model,
        final C callbackContext,
        final boolean useEventualConsistencyDelay
    ) {
        final boolean shouldBeEnabled = keyTranslator.getKeyEnabled(model);
        final boolean wasEnabled =
            previousModel == null || keyTranslator.getKeyEnabled(previousModel);

        if (!wasEnabled && shouldBeEnabled && !callbackContext.isKeyEnabled()) {
            callbackContext.setKeyEnabled(true);
            return proxy.initiate("kms::enable-key", proxyClient, model, callbackContext)
                .translateToServiceRequest(keyTranslator::enableKeyRequest)
                .makeServiceCall(keyApiHelper::enableKey)
                // Changing key status from disabled -> enabled might affect rotation update since
                // it's only allowed on enabled keys. If enabled state hasn't been propagated then
                // the rotation update might hit invalid state exception. Wait 1 min to make sure
                // the changes propagate.
                .progress(useEventualConsistencyDelay ?
                    EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS : 0);
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    /**
     * Disables a KMS key.
     * No updates are made if the key was already disabled.
     */
    public ProgressEvent<M, C> disableKeyIfNecessary(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M previousModel,
        final M model,
        final C callbackContext
    ) {
        final boolean shouldBeEnabled = keyTranslator.getKeyEnabled(model);
        final boolean wasEnabled =
            previousModel == null || keyTranslator.getKeyEnabled(previousModel);

        if (wasEnabled && !shouldBeEnabled) {
            return proxy.initiate("kms::disable-key", proxyClient, model, callbackContext)
                .translateToServiceRequest(keyTranslator::disableKeyRequest)
                    .backoffDelay(stabilizeDelay)
                    .makeServiceCall((disableKeyRequest, proxyClient1) -> {
                        try {
                            return keyApiHelper.disableKey((DisableKeyRequest) disableKeyRequest, proxyClient1);
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
        }

        return ProgressEvent.progress(model, callbackContext);
    }

    /**
     * Retrieves the key's tags. If the softFailOnAccessDenied parameter is true,
     * access denied exceptions will be ignored. If the updateResourceModel parameter is true
     * the resource model will be updated with the latest tags.
     */
    public ProgressEvent<M, C> retrieveResourceTags(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M model,
        final C callbackContext,
        final boolean updateResourceModel
    ) {
        ProgressEvent<M, C> progress;
        do {
            // Pagination to make sure that all the tags are retrieved
            progress = proxy
                .initiate("kms::list-tag-key:" + callbackContext.getTagMarker(), proxyClient,
                    model, callbackContext)
                .translateToServiceRequest(m -> keyTranslator
                    .listResourceTagsRequest(m, callbackContext.getTagMarker()))
                .makeServiceCall(keyApiHelper::listResourceTags)
                .done(
                    (listResourceTagsRequest, listResourceTagsResponse, proxyInvocation,
                     resourceModel, context) -> {
                        final Set<Tag> existingTags =
                            Optional.ofNullable(context.getExistingTags())
                                .orElse(new HashSet<>());
                        existingTags.addAll(new HashSet<>(listResourceTagsResponse.tags()));
                        context.setExistingTags(existingTags);
                        context.setTagMarker(listResourceTagsResponse.nextMarker());

                        if (updateResourceModel) {
                            keyTranslator.setTags(model, callbackContext.getExistingTags());
                        }

                        return ProgressEvent.progress(resourceModel, context);
                    });
        } while (callbackContext.getTagMarker() != null);
        return progress;
    }

    /**
     * Updates the key's tags to the provided desired tags.
     * Access denied exceptions will be ignored.
     */
    public ProgressEvent<M, C> updateKeyTags(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M model,
        final Map<String, String> desiredResourceTags,
        final C callbackContext
    ) {
        return ProgressEvent.progress(model, callbackContext)
            .then(progressEvent -> retrieveResourceTags(proxy, proxyClient, model,
                callbackContext, false))
            .then(progressEvent -> {
                final Set<Tag> existingTags =
                    Optional.ofNullable(callbackContext.getExistingTags())
                        .orElse(new HashSet<>());
                final Set<Tag> tagsToRemove = Sets.difference(existingTags,
                    keyTranslator.translateTagsToSdk(desiredResourceTags));
                if (!tagsToRemove.isEmpty()) {
                    return proxy
                        .initiate("kms::untag-key", proxyClient, model, callbackContext)
                        .translateToServiceRequest(
                            (m) -> keyTranslator.untagResourceRequest(m, tagsToRemove))
                        .makeServiceCall(keyApiHelper::untagResource)
                        .progress();
                }

                return progressEvent;
            })
            .then(progressEvent -> {
                final Set<Tag> existingTags =
                    Optional.ofNullable(callbackContext.getExistingTags())
                        .orElse(new HashSet<>());
                final Set<Tag> tagsToAdd =
                    Sets.difference(keyTranslator.translateTagsToSdk(desiredResourceTags),
                        existingTags);
                if (!tagsToAdd.isEmpty()) {
                    return proxy
                        .initiate("kms::tag-key", proxyClient, model, callbackContext)
                        .translateToServiceRequest(
                            (m) -> keyTranslator.tagResourceRequest(m, tagsToAdd))
                        .makeServiceCall(keyApiHelper::tagResource)
                        .progress();
                }

                return progressEvent;
            });
    }

    /**
     * Deletes the desired key. If the key is not found, a NotFound error is returned.
     */
    public ProgressEvent<M, C> deleteKey(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M model,
        final C callbackContext
    ) {
        return ProgressEvent.progress(model, callbackContext)
            .then(p -> {
                try {
                    return proxy.initiate("kms::delete-key", proxyClient, model, callbackContext)
                        .translateToServiceRequest(keyTranslator::scheduleKeyDeletionRequest)
                        .makeServiceCall(keyApiHelper::scheduleKeyDeletion)
                        .stabilize(this::isDeleted)
                        .done(scheduleKeyDeletionResponse -> ProgressEvent.progress(model, callbackContext));
                } catch (final CfnInvalidRequestException e) {
                    if (e.getCause() instanceof KmsInvalidStateException) {
                        // Invalid state can only happen if the key is pending deletion,
                        // treat it as not found.
                        return ProgressEvent.defaultFailureHandler(e, HandlerErrorCode.NotFound);
                    }

                    throw e;
                }
            }).then(p -> eventualConsistencyHandlerHelper.setRequestType(p, false))
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(p -> ProgressEvent.defaultSuccessHandler(null));
    }

    /**
     * List all of the key's in a customer's account and filter them by their key metadata.
     */
    public ProgressEvent<M, C> listKeysAndFilterByMetadata(
        final ProxyClient<KmsClient> proxyClient,
        final String nextToken,
        final Function<KeyMetadata, Boolean> filterFunction
    ) {
        final ListKeysResponse listKeysResponse =
            keyApiHelper.listKeys(keyTranslator.listKeysRequest(nextToken), proxyClient);

        final List<M> models = listKeysResponse.keys().stream()
            .filter(key -> {
                final KeyMetadata keyMetadata = keyApiHelper
                    .describeKey(keyTranslator.describeKeyRequest(key.keyArn()), proxyClient)
                    .keyMetadata();

                // Apply our metadata filter, and remove any pending deletion keys
                return filterFunction.apply(keyMetadata) &&
                    !keyMetadata.keyState().equals(KeyState.PENDING_DELETION) &&
                    !keyMetadata.keyState().equals(KeyState.PENDING_REPLICA_DELETION);
            })
            .map(keyTranslator::translateKeyListEntry)
            .collect(Collectors.toList());

        return ProgressEvent.<M, C>builder()
            .resourceModels(models)
            .nextToken(listKeysResponse.nextMarker())
            .status(OperationStatus.SUCCESS)
            .build();
    }

    private boolean isDeleted(final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest,
                              final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse,
                              final ProxyClient<KmsClient> proxyClient,
                              final M resourceModel,
                              final C callbackContext) {
        final KeyState keyState =
            keyApiHelper.describeKey(keyTranslator.describeKeyRequest(resourceModel), proxyClient)
                .keyMetadata().keyState();
        return keyState.equals(KeyState.PENDING_DELETION) ||
            keyState.equals(KeyState.PENDING_REPLICA_DELETION);
    }
}
