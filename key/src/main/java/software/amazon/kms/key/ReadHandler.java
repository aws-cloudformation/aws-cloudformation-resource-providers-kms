package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {

    public ReadHandler() {
        super();
    }

    public ReadHandler(final KeyHelper keyHelper) {
        super(keyHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = request.getDesiredResourceState();

        return ProgressEvent.progress(model, callbackContext)
            .then(
                progress -> proxy.initiate("kms::describe-key", proxyClient, model, callbackContext)
                    .translateToServiceRequest(Translator::describeKeyRequest)
                    .makeServiceCall(keyHelper::describeKey)
                    .done(describeKeyResponse -> {
                        final KeyMetadata keyMetadata = describeKeyResponse.keyMetadata();

                        resourceStateCheck(keyMetadata);

                        model.setArn(keyMetadata.arn());
                        model.setKeyId(keyMetadata.keyId());
                        model.setDescription(keyMetadata.description());
                        model.setEnabled(keyMetadata.enabled());
                        model.setKeyUsage(keyMetadata.keyUsageAsString());
                        model.setKeySpec(keyMetadata.customerMasterKeySpecAsString());
                        return ProgressEvent.progress(model, callbackContext);
                    })
            )
            // Retrieving the key policy can potentially cause an access denied exception
            .then(progress -> softFailAccessDenied(() -> proxy
                .initiate("kms::get-key-policy", proxyClient, model, callbackContext)
                .translateToServiceRequest((m) -> Translator.getKeyPolicyRequest(m.getKeyId()))
                .makeServiceCall(keyHelper::getKeyPolicy)
                .done(getKeyPolicyResponse -> {
                    model.setKeyPolicy(deserializeKeyPolicy(getKeyPolicyResponse.policy()));
                    return ProgressEvent.progress(model, callbackContext);
                }), model, callbackContext)
            )
            // Retrieving the rotation status can potentially cause an access denied exception
            .then(progress -> softFailAccessDenied(() -> proxy
                .initiate("kms::get-key-rotation-status", proxyClient, model, callbackContext)
                .translateToServiceRequest(Translator::getKeyRotationStatusRequest)
                .makeServiceCall(keyHelper::getKeyRotationStatus)
                .done(getKeyRotationStatusResponse -> {
                    model.setEnableKeyRotation(getKeyRotationStatusResponse.keyRotationEnabled());
                    return ProgressEvent.progress(model, callbackContext);
                }), model, callbackContext)
            )
            // Retrieving the tags can potentially cause an access denied exception
            .then(
                progress -> retrieveResourceTags(proxy, proxyClient, progress, true))
            .then(progress -> {
                if (!CollectionUtils.isEmpty(callbackContext.getExistingTags())) {
                    model.setTags(
                        Translator.translateTagsFromSdk(callbackContext.getExistingTags()));
                }
                return ProgressEvent.defaultSuccessHandler(model);
            });
    }

    /**
     * Deserializes a key policy from a string.
     *
     * @param keyPolicy the policy to deserialize
     * @return the deserialized policy
     */
    public static Map<String, Object> deserializeKeyPolicy(final String keyPolicy) {
        if (StringUtils.isNullOrEmpty(keyPolicy)) {
            return null;
        }

        try {
            return Translator.MAPPER
                .readValue(keyPolicy, new TypeReference<HashMap<String, Object>>() {
                });
        } catch (final IOException e) {
            throw new CfnInternalFailureException(e);
        }
    }
}
