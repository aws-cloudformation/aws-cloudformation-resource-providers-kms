package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private DeleteHandler handler;
    private ResourceModel model;
    private ResourceHandlerRequest<ResourceModel> request;

    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for key deletion";
    private static final String KEY_ID = "samplearn";
    private static final KeyMetadata KEY_METADATA_DELETED = KeyMetadata.builder().keyState(KeyState.PENDING_DELETION).build();
    private static final KeyMetadata KEY_METADATA_DELETING = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

    @BeforeEach
    public void setup() {
        handler = new DeleteHandler();
        model = ResourceModel.builder().keyId(KEY_ID).build();
        request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(model).build();
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CallbackContext context = CallbackContext.builder()
                .stabilizationRetriesRemaining(1)
                .keyProgress(KeyStatus.KeyProgress.Deleting).build();

        doReturn(DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_DELETED).build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, context, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_NotYetStabalized() {
        final CallbackContext context = CallbackContext.builder()
                .stabilizationRetriesRemaining(1)
                .keyProgress(KeyStatus.KeyProgress.Deleting).build();

        doReturn(DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_DELETING).build())
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, context, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().getKeyProgress()).isEqualTo(KeyStatus.KeyProgress.Deleting);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_DeletionNotYetStarted() {
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.scheduleKeyDeletionRequest(model)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().getKeyProgress()).isEqualTo(KeyStatus.KeyProgress.Deleting);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_StabilizationTimeout() {
        final CallbackContext context = CallbackContext.builder()
                .stabilizationRetriesRemaining(0)
                .keyProgress(null).build();

        try{
            handler.handleRequest(proxy, request, context, logger);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo(TIMED_OUT_MESSAGE);
        }
    }

    @Test
    public void handleRequest_KeyNotFound() {
        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_InvalidArn() {
        doThrow(InvalidArnException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KmsInternal() {
        doThrow(KmsInternalException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KmsInvalidState() {
        doThrow(KmsInvalidStateException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
