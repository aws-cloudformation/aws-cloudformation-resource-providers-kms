package software.amazon.kms.key;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;
import software.amazon.cloudformation.exceptions.TerminalException;
import software.amazon.cloudformation.proxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase{

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    private ReadHandler handler;

    @BeforeEach
    public void setup() {
        handler = new ReadHandler();
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
        final KeyMetadata keyMetadata = KeyMetadata.builder()
            .keyId("sampleId")
            .arn("sampleArn")
            .build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().policy(null).build();
        when(proxyKmsClient.client().getKeyPolicy(any(GetKeyPolicyRequest.class))).thenReturn(getKeyPolicyResponse);

        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(true).build();
        when(proxyKmsClient.client().getKeyRotationStatus(any(GetKeyRotationStatusRequest.class))).thenReturn(getKeyRotationStatusResponse);

        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder().build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder()
                .build())
            .build();
        final CallbackContext callbackContext = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).getKeyPolicy(any(GetKeyPolicyRequest.class));
        verify(proxyKmsClient.client()).getKeyRotationStatus(any(GetKeyRotationStatusRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccessWithPolicy() {
        final KeyMetadata keyMetadata = KeyMetadata.builder()
                .keyId("sampleId")
                .arn("sampleArn")
                .build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().policy("{\"foo\": \"bar\"}").build();
        when(proxyKmsClient.client().getKeyPolicy(any(GetKeyPolicyRequest.class))).thenReturn(getKeyPolicyResponse);

        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(true).build();
        when(proxyKmsClient.client().getKeyRotationStatus(any(GetKeyRotationStatusRequest.class))).thenReturn(getKeyRotationStatusResponse);

        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder().build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder()
                        .keyId("sampleId")
                        .build())
                .build();
        final CallbackContext callbackContext = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).getKeyPolicy(any(GetKeyPolicyRequest.class));
        verify(proxyKmsClient.client()).getKeyRotationStatus(any(GetKeyRotationStatusRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
    }

    @Test
    public void handleRequest_GetPolicyAccessDenied() {

        final KeyMetadata keyMetadata = KeyMetadata.builder()
                .keyId("sampleId")
                .arn("sampleArn")
                .build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final KmsException exception = (KmsException) KmsException.builder().awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build()).build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().getKeyPolicy(any(GetKeyPolicyRequest.class)))
                .thenThrow(exception);

        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(true).build();
        when(proxyKmsClient.client().getKeyRotationStatus(any(GetKeyRotationStatusRequest.class))).thenReturn(getKeyRotationStatusResponse);

        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder().build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenReturn(listTagsForResourceResponse);


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder()
                        .keyId("sampleId")
                        .build())
                .build();
        final CallbackContext callbackContext = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).getKeyPolicy(any(GetKeyPolicyRequest.class));
        verify(proxyKmsClient.client()).getKeyRotationStatus(any(GetKeyRotationStatusRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
    }

    @Test
    public void handleRequest_GetPolicyUnknownKmsException() {

        final KeyMetadata keyMetadata = KeyMetadata.builder()
                .keyId("sampleId")
                .arn("sampleArn")
                .build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final KmsException exception = (KmsException) KmsException.builder().awsErrorDetails(AwsErrorDetails.builder().errorCode("Unknown").build()).build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().getKeyPolicy(any(GetKeyPolicyRequest.class)))
                .thenThrow(exception);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder()
                        .keyId("sampleId")
                        .build())
                .build();
        final CallbackContext callbackContext = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isNotNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).getKeyPolicy(any(GetKeyPolicyRequest.class));
    }

    @Test
    public void handleRequest_GetPolicyUnknownError() {

        final KeyMetadata keyMetadata = KeyMetadata.builder()
                .keyId("sampleId")
                .arn("sampleArn")
                .build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final AwsServiceException exception = AwsServiceException.builder().build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().getKeyPolicy(any(GetKeyPolicyRequest.class)))
                .thenThrow(exception);


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder()
                        .keyId("sampleId")
                        .build())
                .build();
        final CallbackContext callbackContext = new CallbackContext();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isNotNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).getKeyPolicy(any(GetKeyPolicyRequest.class));
    }
}
