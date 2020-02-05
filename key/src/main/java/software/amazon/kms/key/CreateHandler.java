package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.awssdk.services.kms.model.TagException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Map;

import static software.amazon.kms.key.ModelAdapter.setDefaults;
import static software.amazon.kms.key.UpdateHandler.updateKeyRotationStatus;
import static software.amazon.kms.key.UpdateHandler.updateKeyStatus;

public class CreateHandler extends BaseHandler<CallbackContext> {
    final int callbackDelaySeconds = 60;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger)  {

        final ResourceModel model = setDefaults(request.getDesiredResourceState());
        final Map<String, String > tags = request.getDesiredResourceTags();

        if (callbackContext == null) {
            try {
                final CreateKeyResponse createKeyResponse = proxy.injectCredentialsAndInvokeV2(Translator.createCustomerMasterKey(model, tags), ClientBuilder.getClient()::createKey);
                final String arn = createKeyResponse.keyMetadata().arn();
                final String keyId = createKeyResponse.keyMetadata().keyId();

                model.setKeyId(keyId); // set primary identifier
                model.setArn(arn);

                logger.log(String.format("%s [%s] Created Successfully", ResourceModel.TYPE_NAME, keyId));

                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .callbackDelaySeconds(callbackDelaySeconds)
                        .callbackContext(CallbackContext.builder().build())
                        .status(OperationStatus.IN_PROGRESS)
                        .build();

            } catch (MalformedPolicyDocumentException | InvalidArnException | TagException e) {
                throw new CfnInvalidRequestException(e.getMessage());
            } catch (KmsInternalException e) {
                throw new CfnInternalFailureException(e);
            }
        }

        if (model.getEnableKeyRotation()) { // by default rotation is disabled do only if requested
            updateKeyRotationStatus(model.getKeyId(), proxy, true);
            logger.log(String.format("%s KeyRotation was enabled", ResourceModel.TYPE_NAME));
        }

        if (!model.getEnabled()) { // by default CMK is enabled do only if disabled requested
            updateKeyStatus(model.getKeyId(), proxy, false);
            logger.log(String.format("%s KeyStatus was disabled", ResourceModel.TYPE_NAME));
        }

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
