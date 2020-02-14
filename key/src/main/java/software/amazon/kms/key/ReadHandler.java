package software.amazon.kms.key;

import com.fasterxml.jackson.core.type.TypeReference;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ReadHandler extends BaseHandler<CallbackContext> {

    private static KmsClient kmsClient = ClientBuilder.getClient();

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(describeCustomerMasterKey(getKeyMetadata(proxy, model.getKeyId()), proxy))
                .status(OperationStatus.SUCCESS)
                .build();
    }

    public static KeyMetadata getKeyMetadata(final AmazonWebServicesClientProxy proxyClient,
                                             final String keyId) {
        final DescribeKeyResponse describeKeyResponse;
        try {
            describeKeyResponse = proxyClient.injectCredentialsAndInvokeV2(Translator.describeKeyRequest(keyId), kmsClient::describeKey);
        } catch (InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (KmsInternalException e) {
            throw new CfnInternalFailureException(e);
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, keyId);
        }
        return describeKeyResponse.keyMetadata();
    }

    public static boolean getKeyRotationStatus(final AmazonWebServicesClientProxy proxyClient,
                                               final String keyId) {
        try {
            final GetKeyRotationStatusResponse getKeyRotationStatusResponse = proxyClient.injectCredentialsAndInvokeV2(Translator.getKeyRotationStatusRequest(keyId), kmsClient::getKeyRotationStatus);
            return getKeyRotationStatusResponse.keyRotationEnabled();
        } catch (InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, keyId);
        }
    }

    public static Map<String, Object> getKeyPolicy(final String keyId,
                                                   final AmazonWebServicesClientProxy proxyClient) {
        final GetKeyPolicyResponse getKeyPolicyResponse;
        try {
            getKeyPolicyResponse = proxyClient.injectCredentialsAndInvokeV2(Translator.getKeyPolicyRequest(keyId), kmsClient::getKeyPolicy);
            return deserializePolicyKey(getKeyPolicyResponse.policy());
        } catch (InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, keyId);
        }
    }

    public static Map<String, Object> deserializePolicyKey(final String policyKey) { // serializing key policy
        if (policyKey == null) return null;
        try {
            return Translator.MAPPER.readValue(policyKey, new TypeReference<HashMap<String,Object>>() {});
        } catch (final IOException e) {
            throw new CfnInternalFailureException(e);
        }
    }

    public static ResourceModel describeCustomerMasterKey(final KeyMetadata keyMetadata,
                                                          final AmazonWebServicesClientProxy proxyClient) {

        final ListResourceTagsResponse listResourceTagsResponse;

        final String arn = keyMetadata.arn();
        final String keyId = keyMetadata.keyId();
        final String description = keyMetadata.description();
        final Boolean enabled = keyMetadata.enabled();
        final Map<String, Object> policyKey = getKeyPolicy(keyId, proxyClient);
        final boolean keyRotationEnabled = getKeyRotationStatus(proxyClient, keyId);

        listResourceTagsResponse = proxyClient.injectCredentialsAndInvokeV2(Translator.listResourceTagsRequest(keyId), kmsClient::listResourceTags);
        final Set<Tag> tags = Translator.translateTagsFromSdk(listResourceTagsResponse.tags());
        return ResourceModel.builder()
                .arn(arn)
                .keyId(keyId)
                .description(description)
                .enabled(enabled)
                .keyPolicy(policyKey)
                .enableKeyRotation(keyRotationEnabled)
                .keyUsage(keyMetadata.keyUsageAsString())
                .tags(tags).build();
    }
}
