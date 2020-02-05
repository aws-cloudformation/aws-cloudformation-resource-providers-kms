package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.key.KeyStatus.KeyProgress;


import static software.amazon.kms.key.ReadHandler.getKeyMetadata;

public class DeleteHandler extends BaseHandler<CallbackContext> {
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for key deletion";
    final int stabilizationRetries = 12;
    final int callbackDelaySeconds = 5; // polling every 5s up to a minute
    private Logger loggerClient = null;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        loggerClient = logger;

        final CallbackContext currentContext = callbackContext == null ? CallbackContext
                .builder()
                .stabilizationRetriesRemaining(stabilizationRetries)
                .build() : callbackContext;

        return deleteKeyAndUpdateProgress(model, proxy, currentContext);
    }

    private ProgressEvent<ResourceModel, CallbackContext> deleteKeyAndUpdateProgress(final ResourceModel model,
                                                                                     final AmazonWebServicesClientProxy proxyClient,
                                                                                     final CallbackContext callbackContext) {
        if (callbackContext.getStabilizationRetriesRemaining() == 0)
            throw new RuntimeException(TIMED_OUT_MESSAGE);

        final KeyProgress currentStatus = callbackContext.getKeyProgress() == null ? KeyProgress.Default : callbackContext.getKeyProgress();
        KeyProgress updatedStatus = null;

        switch (currentStatus) {
            // deleting customer master key with propagation time of 1 min
            case Default: updatedStatus = deleteCustomerMasterKey(model, proxyClient);
                break;
            // checking for stabilization
            case Deleting: updatedStatus = checkStabilizationStatus(model.getKeyId(), proxyClient);
                break;
        }

        callbackContext.setKeyProgress(updatedStatus);
        return updatedStatus == KeyProgress.Deleted ? updateSuccess(model) : updateInProgress(model, callbackContext);
    }

    private KeyProgress deleteCustomerMasterKey(final ResourceModel model,
                                                final AmazonWebServicesClientProxy proxyClient) {
        try {
            proxyClient.injectCredentialsAndInvokeV2(Translator.scheduleKeyDeletionRequest(model), ClientBuilder.getClient()::scheduleKeyDeletion);
            loggerClient.log(String.format("%s [%s] deletion has been initiated", ResourceModel.TYPE_NAME, model.getKeyId()));
            return KeyProgress.Deleting;
        } catch (NotFoundException e) {
            throw new CfnNotFoundException(ResourceModel.TYPE_NAME, model.getKeyId());
        } catch (InvalidArnException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (KmsInternalException e) {
            throw new CfnInternalFailureException(e);
        }
    }

    private KeyProgress checkStabilizationStatus(final String keyId,
                                                 final AmazonWebServicesClientProxy proxyClient) {
        if (getKeyMetadata(proxyClient, keyId).keyState() == KeyState.PENDING_DELETION) { // if in deletion state then stabalized
            loggerClient.log(String.format("%s [%s] has been successfully deleted", ResourceModel.TYPE_NAME, keyId));
            return KeyProgress.Deleted;
        }
        return KeyProgress.Deleting;
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateInProgress(final ResourceModel model,
                                                                           final CallbackContext callbackContext) {
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .callbackDelaySeconds(callbackDelaySeconds)
                .callbackContext(CallbackContext.builder()
                        .keyProgress(callbackContext.getKeyProgress())
                        .stabilizationRetriesRemaining(callbackContext.getStabilizationRetriesRemaining() - 1)
                        .build())
                .status(OperationStatus.IN_PROGRESS)
                .build();
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateSuccess(final ResourceModel model) {
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.SUCCESS)
                .build();
    }
}
