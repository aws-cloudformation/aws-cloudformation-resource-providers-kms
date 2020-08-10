package software.amazon.kms.key;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.cloudformation.exceptions.TerminalException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest extends AbstractTestBase{

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    private DeleteHandler handler;

    @BeforeEach
    public void setup() {
        handler = new DeleteHandler();
        kms = mock(KmsClient.class);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @AfterEach
    public void post_execute() {
        verify(kms, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(proxyKmsClient.client());
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse = ScheduleKeyDeletionResponse.builder().build();
        when(proxyKmsClient.client().scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class))).thenReturn(scheduleKeyDeletionResponse);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KeyMetadata.builder().keyState(KeyState.PENDING_DELETION).build()).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder().build())
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class));
        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
    }

    // Key has been deleted out of band -> considered deleted
    @Test
    public void handleRequest_SimpleSuccessNotFound() {
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse = ScheduleKeyDeletionResponse.builder().build();
        when(proxyKmsClient.client().scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class)))
            .thenThrow(KmsInvalidStateException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder().build())
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);

        verify(proxyKmsClient.client()).scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class));
    }

    // error handler
    @Test
    public void handleRequest_SimpleSuccessInvalidArn() {
        when(proxyKmsClient.client().scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class)))
            .thenThrow(InvalidArnException.class);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder()
                .build())
            .build();

        assertThrows(TerminalException.class,
            () ->handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));

        verify(proxyKmsClient.client()).scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class));
    }
}
