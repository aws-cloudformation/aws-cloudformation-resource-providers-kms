package software.amazon.kms.key;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.ModelAdapter.unsetWriteOnly;
import static software.amazon.kms.key.Translator.translatePolicyInput;

import com.google.common.collect.Sets;
import java.util.Set;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
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

        final ResourceModel previousModel = setDefaults(request.getPreviousResourceState());
        return ProgressEvent.progress(setDefaults(request.getDesiredResourceState()), callbackContext)
                .then(progress -> proxy.initiate("kms::update-key", proxyClient, setDefaults(request.getDesiredResourceState()), callbackContext)
                        .translateToServiceRequest(Translator::describeKeyRequest)
                        .makeServiceCall((describeKeyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(describeKeyRequest, proxyInvocation.client()::describeKey))
                        .done((describeKeyRequest, describeKeyResponse, proxyInvocation, model, context) -> {
                            notFoundCheck(describeKeyResponse.keyMetadata());

                            if (context.isKeyStatusRotationUpdated()) return ProgressEvent.progress(model, context);


                            final boolean prevIsEnabled = previousModel.getEnabled();
                            final boolean currIsEnabled = model.getEnabled();

                            final boolean prevIsRotationEnabled = previousModel.getEnableKeyRotation();
                            final boolean currIsRotationEnabled = model.getEnableKeyRotation();

                            final boolean hasUpdatedStatus = prevIsEnabled ^ currIsEnabled;
                            final boolean hasUpdatedRotation = prevIsRotationEnabled ^ currIsRotationEnabled;

                            // if key is not being updated and is disabled then we cannot perform any updates on the key
                            if (!prevIsEnabled && !hasUpdatedStatus && hasUpdatedRotation) // key stays disabled
                                throw new CfnInvalidRequestException("You cannot modify the EnableKeyRotation property when the Enabled property is false. Set Enabled to true to modify the EnableKeyRotation property.");

                            // if key is disabled then it needs to get enabled first and then update rotation if necessary
                            // check if key has been enabled and propagated otherwise eventual inconsistency might occur and rotation status update might hit invalid state exception
                            if (!prevIsEnabled && currIsEnabled && !context.isKeyEnabled())  // enable key
                                return updateKeyStatus(proxy, proxyInvocation, model, context, true);

                            // update rotation if necessary
                            if (hasUpdatedRotation)
                                updateKeyRotationStatus(proxy, proxyInvocation, model, context, currIsRotationEnabled);

                            // disable the key if necessary
                            // changing status from enabled to disabled wont affect other updates since they are possible with disabled key
                            // rotation update happens before disabling key
                            if (prevIsEnabled && !currIsEnabled) // disable key
                                updateKeyStatus(proxy, proxyInvocation, model, context, false);

                            context.setKeyStatusRotationUpdated(true);
                            return ProgressEvent.progress(model, context);
                        })
                )
                .then(progress -> {
                    if (previousModel.getDescription().equals(progress.getResourceModel().getDescription()))
                        return progress;
                    return proxy.initiate("kms::update-key-description", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                            .translateToServiceRequest(Translator::updateKeyDescriptionRequest)
                            .makeServiceCall((updateKeyDescriptionRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(updateKeyDescriptionRequest, proxyInvocation.client()::updateKeyDescription))
                            .progress();
                })
                .then(progress -> {
                    final String previousKeyPolicy = translatePolicyInput(previousModel.getKeyPolicy());
                    final String currentKeyPolicy = translatePolicyInput(progress.getResourceModel().getKeyPolicy());
                    if (previousKeyPolicy.equals(currentKeyPolicy))
                        return progress;
                    return proxy.initiate("kms::update-key-keypolicy", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                            .translateToServiceRequest(Translator::putKeyPolicyRequest)
                            .makeServiceCall((putKeyPolicyRequest, proxyInvocation) -> proxyInvocation.injectCredentialsAndInvokeV2(putKeyPolicyRequest, proxyInvocation.client()::putKeyPolicy))
                            .progress();
                })
                .then(progress -> BaseHandlerStd.retrieveResourceTags(proxy, proxyClient, progress))
                .onSuccess(progress -> {
                    final Set<Tag> tagsToRemove = Sets.difference(progress.getCallbackContext().getExistingTags(), Translator.translateTagsToSdk(request.getDesiredResourceTags()));
                    if (tagsToRemove.isEmpty())
                        return ProgressEvent.success(progress.getResourceModel(), progress.getCallbackContext());

                    return proxy.initiate("kms::untag-key", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                            .translateToServiceRequest((model) -> Translator.untagResourceRequest(model.getKeyId(),tagsToRemove))
                            .makeServiceCall((untagResourceRequest, proxyInvocation) -> proxyClient.injectCredentialsAndInvokeV2(untagResourceRequest, proxyClient.client()::untagResource))
                            .handleError(BaseHandlerStd::accessDenied)
                            .success();
                })
                .onSuccess(progress -> {
                    final Set<Tag> tagsToAdd = Sets.difference(Translator.translateTagsToSdk(request.getDesiredResourceTags()), progress.getCallbackContext().getExistingTags());
                    if (tagsToAdd.isEmpty())
                        return progress;

                    return proxy.initiate("kms::tag-key", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                            .translateToServiceRequest((model) -> Translator.tagResourceRequest(model.getKeyId(), tagsToAdd))
                            .makeServiceCall((tagResourceRequest, proxyInvocation) -> proxyClient.injectCredentialsAndInvokeV2(tagResourceRequest, proxyClient.client()::tagResource))
                            .handleError(BaseHandlerStd::accessDenied)
                            .progress();
                })
                .then(BaseHandlerStd::propagate)
                .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(progress.getResourceModel())));
    }
}
