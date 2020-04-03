package software.amazon.kms.key;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.services.cloudwatch.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
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

import java.util.Map;

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
public class CreateHandlerTest extends AbstractTestBase{

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    private CreateHandler handler;

    @BeforeEach
    public void setup() {
        handler = new CreateHandler();
        kms = mock(KmsClient.class);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @AfterEach
    public void post_execute() {
        verifyNoMoreInteractions(proxyKmsClient.client());
    }

    @Test
    public void handleRequest_PartiallyPropagate() {
        final CreateKeyResponse createKeyResponse = CreateKeyResponse
            .builder()
            .keyMetadata(KeyMetadata.builder().keyId("sampleId").build())
            .build();
        when(proxyKmsClient.client().createKey(any(CreateKeyRequest.class))).thenReturn(createKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder().build())
            .build();
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getCallbackContext().partiallyPropagated).isEqualTo(true);
        assertThat(response.getCallbackContext().fullyPropagated).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).createKey(any(CreateKeyRequest.class));
    }

    @Test
    public void handleRequest_FullyPropagate() {
        final CreateKeyResponse createKeyResponse = CreateKeyResponse
            .builder()
            .keyMetadata(KeyMetadata.builder().keyId("sampleId").build())
            .build();
        when(proxyKmsClient.client().createKey(any(CreateKeyRequest.class))).thenReturn(createKeyResponse);

        final EnableKeyRotationResponse enableKeyRotationResponse = EnableKeyRotationResponse.builder().build();
        when(proxyKmsClient.client().enableKeyRotation(any(EnableKeyRotationRequest.class))).thenReturn(enableKeyRotationResponse);

        final DisableKeyResponse disableKeyResponse = DisableKeyResponse.builder().build();
        when(proxyKmsClient.client().disableKey(any(DisableKeyRequest.class))).thenReturn(disableKeyResponse);


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(ResourceModel.builder()
                .enableKeyRotation(true)
                .enabled(false)
                .build())
            .build();
        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setPartiallyPropagated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getCallbackContext().partiallyPropagated).isEqualTo(true);
        assertThat(response.getCallbackContext().fullyPropagated).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).createKey(any(CreateKeyRequest.class));
        verify(proxyKmsClient.client()).enableKeyRotation(any(EnableKeyRotationRequest.class));
        verify(proxyKmsClient.client()).disableKey(any(DisableKeyRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final KeyMetadata keyMetadata = KeyMetadata.builder()
            .keyId("sampleId")
            .arn("sampleArn")
            .build();
        final CreateKeyResponse createKeyResponse = CreateKeyResponse
            .builder()
            .keyMetadata(keyMetadata)
            .build();
        when(proxyKmsClient.client().createKey(any(CreateKeyRequest.class))).thenReturn(createKeyResponse);

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
                .build())
            .build();
        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setPartiallyPropagated(true);
        callbackContext.setFullyPropagated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getCallbackContext().partiallyPropagated).isEqualTo(true);
        assertThat(response.getCallbackContext().fullyPropagated).isEqualTo(true);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).createKey(any(CreateKeyRequest.class));
        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).getKeyPolicy(any(GetKeyPolicyRequest.class));
        verify(proxyKmsClient.client()).getKeyRotationStatus(any(GetKeyRotationStatusRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
    }
}
