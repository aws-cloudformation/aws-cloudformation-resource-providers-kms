package software.amazon.kms.key;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;
import static software.amazon.kms.key.Translator.translatePolicyInput;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {
    private static final String ACCESS_DENIED_EXCEPTION_MESSAGE = "not authorized";

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = setDefaults(request.getDesiredResourceState());
        final ResourceModel previousModel = setDefaults(request.getPreviousResourceState());

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> proxy.initiate("kms::update-key", proxyClient, model, callbackContext)
                .translateToServiceRequest(Translator::describeKeyRequest)
                .makeServiceCall((describeKeyRequest, proxyInvocation) -> proxyInvocation
                    .injectCredentialsAndInvokeV2(describeKeyRequest,
                        proxyInvocation.client()::describeKey))
                .handleError(BaseHandlerStd::handleNotFound)
                .done(describeKeyResponse -> {
                    resourceStateCheck(describeKeyResponse.keyMetadata());

                    return progress;
                })
            )
            .then(progress -> validateResourceModel(progress, previousModel, model))
            .then(progress -> {
                // If the key is disabled, then it needs to get enabled before updating rotation
                // Check if key has been enabled and propagated otherwise eventual inconsistency
                // might occur and rotation status update might hit an invalid state exception
                if (!previousModel.getEnabled() && model.getEnabled()
                    && !callbackContext.isKeyEnabled()) {
                    return updateKeyStatus(proxy, proxyClient, model, callbackContext,
                        model.getEnabled());
                }
                return progress;
            })
            .then(progress -> {
                // Update rotation if necessary
                if (previousModel.getEnableKeyRotation() != model.getEnableKeyRotation()) {
                    return updateKeyRotationStatus(proxy, proxyClient, model, callbackContext,
                        model.getEnableKeyRotation());
                }
                return progress;
            })
            .then(progress -> {
                // Disable the key if necessary
                // This won't affect other updates since the rotation update already happened and
                // the other updates are allowed with disabled keys
                if (previousModel.getEnabled() && !model.getEnabled()) {
                    // Disable key
                    return updateKeyStatus(proxy, proxyClient, model, callbackContext,
                        model.getEnabled());
                }
                return progress;
            })
            .then(progress -> {
                if (!previousModel.getDescription().equals(model.getDescription())) {
                    return proxy.initiate("kms::update-key-description", proxyClient, model,
                        callbackContext)
                        .translateToServiceRequest(Translator::updateKeyDescriptionRequest)
                        .makeServiceCall(
                            (updateKeyDescriptionRequest, proxyInvocation) -> proxyInvocation
                                .injectCredentialsAndInvokeV2(updateKeyDescriptionRequest,
                                    proxyInvocation.client()::updateKeyDescription))
                        .progress();
                }

                return progress;
            })
            .then(progress -> {
                final String previousKeyPolicy = translatePolicyInput(previousModel.getKeyPolicy());
                final String currentKeyPolicy = translatePolicyInput(model.getKeyPolicy());
                if (!previousKeyPolicy.equals(currentKeyPolicy) && !callbackContext
                    .isKeyPolicyUpdated()) { // context carries policy propagation status
                    callbackContext.setKeyPolicyUpdated(true);
                    return proxy
                        .initiate("kms::update-key-keypolicy", proxyClient, model, callbackContext)
                        .translateToServiceRequest(Translator::putKeyPolicyRequest)
                        .makeServiceCall((putKeyPolicyRequest, proxyInvocation) -> proxyInvocation
                            .injectCredentialsAndInvokeV2(putKeyPolicyRequest,
                                proxyInvocation.client()::putKeyPolicy))
                        .progress(BaseHandlerStd.CALLBACK_DELAY_SECONDS);
                    // This requires some propagation delay because the updated policy
                    // might provision new permissions which are required by the next events
                }

                return progress;
            })
            .then(progress -> {
                ProgressEvent<ResourceModel, CallbackContext> event =
                    ProgressEvent.progress(model, callbackContext)
                        .then(progressEvent -> BaseHandlerStd
                            .retrieveResourceTags(proxy, proxyClient, progressEvent, false))
                        .then(progressEvent -> {
                            final Set<Tag> existingTags =
                                Optional.ofNullable(callbackContext.getExistingTags())
                                    .orElse(new HashSet<>());
                            final Set<Tag> tagsToRemove = Sets.difference(existingTags,
                                Translator.translateTagsToSdk(request.getDesiredResourceTags()));
                            if (!tagsToRemove.isEmpty()) {
                                return proxy
                                    .initiate("kms::untag-key", proxyClient, model, callbackContext)
                                    .translateToServiceRequest((m) -> Translator
                                        .untagResourceRequest(m.getKeyId(), tagsToRemove))
                                    .makeServiceCall(
                                        (untagResourceRequest, proxyInvocation) -> proxyClient
                                            .injectCredentialsAndInvokeV2(untagResourceRequest,
                                                proxyClient.client()::untagResource))
                                    .progress();
                            }

                            return progressEvent;
                        })
                        .then(progressEvent -> {
                            final Set<Tag> existingTags =
                                Optional.ofNullable(callbackContext.getExistingTags())
                                    .orElse(new HashSet<>());
                            final Set<Tag> tagsToAdd = Sets.difference(
                                Translator.translateTagsToSdk(request.getDesiredResourceTags()),
                                existingTags);
                            if (!tagsToAdd.isEmpty()) {
                                return proxy
                                    .initiate("kms::tag-key", proxyClient, model, callbackContext)
                                    .translateToServiceRequest((m) -> Translator
                                        .tagResourceRequest(m.getKeyId(), tagsToAdd))
                                    .makeServiceCall(
                                        (tagResourceRequest, proxyInvocation) -> proxyClient
                                            .injectCredentialsAndInvokeV2(tagResourceRequest,
                                                proxyClient.client()::tagResource))
                                    .progress();
                            }

                            return progressEvent;
                        });

                if (event.isFailed() && event.getErrorCode() == HandlerErrorCode.InvalidRequest
                    && event.getMessage().contains(ACCESS_DENIED_EXCEPTION_MESSAGE)) {
                    logger.log("[Tag Update: Soft Fail]" + event.getMessage());
                    return progress;
                }
                return event;
            })
            .then(BaseHandlerStd::propagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }
}
