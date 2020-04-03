package software.amazon.kms.key;

import static software.amazon.kms.key.ModelAdapter.setDefaults;

import com.amazonaws.util.CollectionUtils;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {

        return proxy.initiate("kms::update-custom-key", proxyClient, setDefaults(request.getDesiredResourceState()), callbackContext)
            .request(Translator::describeKeyRequest)
            .call((describeKeyRequest, proxyInvocation) -> {
                return proxyInvocation.injectCredentialsAndInvokeV2(describeKeyRequest, proxyInvocation.client()::describeKey);
            })
            .done((describeKeyRequest, describeKeyResponse, proxyInvocation, model, context) ->
                updateKeyStatusAndRotation(proxy, proxyInvocation, model, describeKeyResponse.keyMetadata(), context))
            .then(progress -> updateKeyDescription(proxy, proxyClient, progress))
            .then(progress -> updateKeyPolicy(proxy, proxyClient, progress))
            .then(progress -> tagResource(proxy, proxyClient, progress, request.getDesiredResourceTags()))
            .then(BaseHandlerStd::proparateResource)
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateKeyStatusAndRotation(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ResourceModel model,
        final KeyMetadata keyMetadata,
        final CallbackContext callbackContext
    ) {
        if (callbackContext.isKeyStatusRotationUpdated()) return ProgressEvent.progress(model, callbackContext);

        final boolean prevIsEnabled = keyMetadata.enabled();
        final boolean currIsEnabled = model.getEnabled();

        final boolean prevIsRotationEnabled = proxyClient.injectCredentialsAndInvokeV2(Translator.getKeyRotationStatusRequest(model), proxyClient.client()::getKeyRotationStatus).keyRotationEnabled();
        final boolean currIsRotationEnabled = model.getEnableKeyRotation();

        final boolean hasUpdatedStatus = prevIsEnabled ^ currIsEnabled;
        final boolean hasUpdatedRotation = prevIsRotationEnabled ^ currIsRotationEnabled;

        // if key is not being updated and is disabled then we cannot perform any updates on the key
        if (!prevIsEnabled && !hasUpdatedStatus && hasUpdatedRotation) // key stays disabled
            throw new CfnInvalidRequestException(
                "You cannot modify the EnableKeyRotation property when the Enabled property is false. Set Enabled to true to modify the EnableKeyRotation property.");


        // if key is disabled then it needs to get enabled first and then update rotation if necessary
        // check if key has been enabled and propagated otherwise eventual inconsistency might occur and rotation status update might hit invalid state exception
        if (!prevIsEnabled && currIsEnabled && !callbackContext.isPartiallyPropagated())  // enable key
            return updateKeyStatus(proxy, proxyClient, model, callbackContext, true);

        // update rotation if necessary
        if (hasUpdatedRotation)
            updateKeyRotationStatus(proxy, proxyClient, model, callbackContext, currIsRotationEnabled);


        // disable the key if necessary
        // changing status from enabled to disabled wont affect other updates since they are possible with disabled key
        // rotation update happens before disabling key
        if (prevIsEnabled && !currIsEnabled) // disable key
            updateKeyStatus(proxy, proxyClient, model, callbackContext, false);

        callbackContext.setKeyStatusRotationUpdated(true);
        return ProgressEvent.progress(model, callbackContext);
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateKeyDescription(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progressEvent
    ) {
        final String currDescription = progressEvent.getResourceModel().getDescription();

        return proxy.initiate("kms::update-custom-key-description", proxyClient, progressEvent.getResourceModel(), progressEvent.getCallbackContext())
            .request(Translator::updateKeyDescriptionRequest)
            .call((updateKeyDescriptionRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(updateKeyDescriptionRequest, proxyInvocation.client()::updateKeyDescription))
            .progress();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateKeyPolicy(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progressEvent
    ) {
        return proxy.initiate("kms::update-custom-key-keypolicy", proxyClient, progressEvent.getResourceModel(), progressEvent.getCallbackContext())
            .request(Translator::putKeyPolicyRequest)
            .call((putKeyPolicyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(putKeyPolicyRequest, proxyInvocation.client()::putKeyPolicy))
            .progress();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> tagResource(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final ProgressEvent<ResourceModel, CallbackContext> progress,
        final Map<String, String> tags
    ) {
        return proxy.initiate("rds::tag-custom-key", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
            .request(Translator::listResourceTagsRequest)
            .call((listResourceTagsRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(listResourceTagsRequest, proxyInvocation.client()::listResourceTags))
            .done((listResourceTagsRequest, listResourceTagsResponse, proxyInvocation, resourceModel, context) -> {
                final Set<Tag> currentTags = Translator.translateTagsToSdk(tags);

                final Set<Tag> existingTags = new HashSet<>(listResourceTagsResponse.tags());

                final Set<Tag> tagsToRemove = Sets.difference(existingTags, currentTags);
                final Set<Tag> tagsToAdd = Sets.difference(currentTags, existingTags);

                proxyInvocation.injectCredentialsAndInvokeV2(Translator.untagResourceRequest(resourceModel.getKeyId(),tagsToRemove), proxyInvocation.client()::untagResource);
                proxyInvocation.injectCredentialsAndInvokeV2(Translator.tagResourceRequest(resourceModel.getKeyId(), tagsToAdd), proxyInvocation.client()::tagResource);
                return ProgressEvent.progress(resourceModel, context);
            });
    }
}
