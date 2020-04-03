package software.amazon.kms.key;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
        verifyNoMoreInteractions(proxyKmsClient.client());
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse = ScheduleKeyDeletionResponse.builder().build();
        when(proxyKmsClient.client().scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class))).thenReturn(scheduleKeyDeletionResponse);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(
            KeyMetadata.builder()
                .keyState(KeyState.PENDING_DELETION)
                .build()
        ).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder()
                .build())
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext().partiallyPropagated).isEqualTo(false);
        assertThat(response.getCallbackContext().fullyPropagated).isEqualTo(false);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class));
        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
    }


    @Test
    public void handleRequest_SimpleSuccessNotFound() {
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse = ScheduleKeyDeletionResponse.builder().build();
        when(proxyKmsClient.client().scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class)))
            .thenThrow(KmsInvalidStateException.class);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(
            KeyMetadata.builder()
                .keyState(KeyState.PENDING_DELETION)
                .build()
        ).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder()
                .build())
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext().partiallyPropagated).isEqualTo(false);
        assertThat(response.getCallbackContext().fullyPropagated).isEqualTo(false);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class));
        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
    }
}
