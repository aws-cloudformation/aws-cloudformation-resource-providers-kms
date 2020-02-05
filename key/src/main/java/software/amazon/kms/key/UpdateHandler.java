package software.amazon.kms.key;

import com.amazonaws.util.CollectionUtils;
import com.google.common.collect.Sets;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.DisabledException;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.TerminalException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.ReadHandler.getKeyMetadata;
import static software.amazon.kms.key.ReadHandler.getKeyRotationStatus;

public class UpdateHandler extends BaseHandler<CallbackContext> {

    private static final KmsClient kmsClient = ClientBuilder.getClient();
    private static Logger loggerClient = null;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = setDefaults(request.getDesiredResourceState());
        loggerClient = logger;

        final KeyMetadata keyMetadata = getKeyMetadata(proxy, model.getKeyId());

        updateKeyStatusAndRotation(model, proxy, keyMetadata);
        updateKeyDescription(model, proxy, keyMetadata);
        updateKeyPolicy(model, proxy);
        updateKeyTags(model.getKeyId(), request.getDesiredResourceTags(), proxy);

        model.setArn(keyMetadata.arn());

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
    }

    private void updateKeyStatusAndRotation(final ResourceModel model,
                                            final AmazonWebServicesClientProxy proxyClient,
                                            final KeyMetadata keyMetadata) {
        final boolean prevIsEnabled = keyMetadata.enabled();
        final boolean currIsEnabled = model.getEnabled();

        final boolean prevIsRotationEnabled = getKeyRotationStatus(proxyClient, model.getKeyId());
        final boolean currIsRotationEnabled = model.getEnableKeyRotation();

        final boolean hasUpdatedStatus = prevIsEnabled ^ currIsEnabled;
        final boolean hasUpdatedRotation = prevIsRotationEnabled ^ currIsRotationEnabled;

        // if key is not being updated and is disabled then we cannot perform any updates on the key
        if (!prevIsEnabled && !hasUpdatedStatus) // key stays disabled
            throw new TerminalException("You cannot modify the EnableKeyRotation property when the Enabled property is false. Set Enabled to true to modify the EnableKeyRotation property.");

        // if key is being enabled then we need to enable it first and then update rotation if necessary
        if (!prevIsEnabled && currIsEnabled) { // enable key
            updateKeyStatus(model.getKeyId(), proxyClient, currIsEnabled);
            loggerClient.log(String.format("%s [%s] was successfully enabled", ResourceModel.TYPE_NAME, model.getKeyId()));
        }

        // update rotation if necessary
        if (hasUpdatedRotation) { // update rotation
            updateKeyRotationStatus(model.getKeyId(), proxyClient, currIsRotationEnabled);
            loggerClient.log(String.format("%s [%s] key rotation was successfully updated", ResourceModel.TYPE_NAME, model.getKeyId()));
        }

        // disable the key if necessary
        if (prevIsEnabled && !currIsEnabled) { // disable key
            updateKeyStatus(model.getKeyId(), proxyClient, currIsEnabled);
            loggerClient.log(String.format("%s [%s] was successfully disabled", ResourceModel.TYPE_NAME, model.getKeyId()));
        }
    }

    public static void updateKeyStatus(final String keyId,
                                       final AmazonWebServicesClientProxy proxyClient,
                                       final boolean enable) {
        try {
            if (enable) {
                proxyClient.injectCredentialsAndInvokeV2(Translator.enableKeyRequest(keyId), kmsClient::enableKey);
                return;
            }
            proxyClient.injectCredentialsAndInvokeV2(Translator.disableKeyRequest(keyId), kmsClient::disableKey);
        } catch (InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (KmsInternalException e) {
            throw new CfnInternalFailureException(e);
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, keyId);
        }
    }

    public static void updateKeyRotationStatus(final String keyId,
                                               final AmazonWebServicesClientProxy proxyClient,
                                               final boolean enable) {
        try {
            if (enable) {
                proxyClient.injectCredentialsAndInvokeV2(Translator.enableKeyRotationRequest(keyId), kmsClient::enableKeyRotation);
                return;
            }
            proxyClient.injectCredentialsAndInvokeV2(Translator.disableKeyRotationRequest(keyId), kmsClient::disableKeyRotation);
        } catch (DisabledException | InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (KmsInternalException e) {
            throw new CfnInternalFailureException(e);
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, keyId);
        }
    }

    private void updateKeyDescription(final ResourceModel model,
                                      final AmazonWebServicesClientProxy proxyClient,
                                      final KeyMetadata keyMetadata) {
        final String prevDescription = keyMetadata.description();
        final String currDescription = model.getDescription();

        final boolean hasUpdatedDescription = !prevDescription.equals(currDescription);

        if (hasUpdatedDescription) {
            try {
                proxyClient.injectCredentialsAndInvokeV2(Translator.updateKeyDescriptionRequest(model.getKeyId(), currDescription), kmsClient::updateKeyDescription);
            } catch (InvalidArnException e) {
                throw new CfnInvalidRequestException(e.getMessage());
            }
            loggerClient.log(String.format("%s [%s] description was successfully updated", ResourceModel.TYPE_NAME, model.getKeyId()));
        }
    }

    private void updateKeyPolicy(final ResourceModel model,
                                 final AmazonWebServicesClientProxy proxyClient) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(Translator.putKeyPolicyRequest(model), kmsClient::putKeyPolicy);
            loggerClient.log(String.format("%s [%s] policy was successfully updated", ResourceModel.TYPE_NAME, model.getKeyId()));
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, model.getKeyId());
        } catch (MalformedPolicyDocumentException e) {
            throw new CfnInvalidRequestException(e);
        }
    }

    private void updateKeyTags(final String keyId,
                               final Map<String, String> tags,
                               final AmazonWebServicesClientProxy proxyClient) {
        final Set<Tag> newTags = tags == null ? Collections.emptySet() : Sets.newHashSet(Translator.translateTagsToSdk(tags));
        final Set<Tag> existingTags = Sets.newHashSet(proxyClient.injectCredentialsAndInvokeV2(Translator.listResourceTagsRequest(keyId), kmsClient::listResourceTags).tags());
        final List<String> toRemove = existingTags.stream()
                .filter(tag -> !newTags.contains(tag))
                .collect(Collectors.mapping(tag -> tag.tagKey(), Collectors.toList()));
        final List<Tag> toAdd = newTags.stream()
                .filter(tag -> !existingTags.contains(tag))
                .collect(Collectors.toList());
        if (!CollectionUtils.isNullOrEmpty(toRemove)) proxyClient.injectCredentialsAndInvokeV2(Translator.untagResourceRequest(keyId, toRemove), kmsClient::untagResource);
        if (!CollectionUtils.isNullOrEmpty(toAdd)) proxyClient.injectCredentialsAndInvokeV2(Translator.tagResourceRequest(keyId, toAdd), kmsClient::tagResource);
        loggerClient.log(String.format("%s [%s] tags were successfully updated", ResourceModel.TYPE_NAME, keyId));
    }
}
