package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


import java.time.Duration;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CustomerMasterKeySpec;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.key.ResourceModel.ResourceModelBuilder;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {
    private static final String KEY_POLICY = "{\"foo\":\"bar\"}";
    private static final ResourceModelBuilder KEY_MODEL_BUILDER = ResourceModel.builder()
        .keyId("mock-key-id")
        .arn("mock-arn")
        .enableKeyRotation(true)
        .keySpec(CustomerMasterKeySpec.SYMMETRIC_DEFAULT.toString())
        .keyUsage(KeyUsageType.ENCRYPT_DECRYPT.toString())
        .description("mock-description")
        .enabled(true);
    private static final ResourceModel KEY_MODEL_NULL_TAGS_NULL_POLICY = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER
        .keyPolicy(ReadHandler.deserializeKeyPolicy(KEY_POLICY))
        .tags(SDK_TAGS.stream().map(t -> new Tag(t.tagKey(), t.tagValue()))
            .collect(Collectors.toSet()))
        .build();
    private static final KeyMetadata.Builder KEY_METADATA_BUILDER = KeyMetadata.builder()
        .keyId(KEY_MODEL.getKeyId())
        .arn(KEY_MODEL.getArn())
        .customerMasterKeySpec(KEY_MODEL.getKeySpec())
        .keyUsage(KEY_MODEL.getKeyUsage())
        .description(KEY_MODEL.getDescription())
        .enabled(KEY_MODEL.getEnabled());
    private static final KeyMetadata KEY_METADATA = KEY_METADATA_BUILDER.build();
    private static final KeyMetadata KEY_METADATA_PENDING_DELETION = KEY_METADATA_BUILDER
        .keyState(KeyState.PENDING_DELETION)
        .build();

    @Mock
    KmsClient kms;

    @Mock
    private KeyHelper keyHelper;

    private ReadHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;

    @BeforeEach
    public void setup() {
        handler = new ReadHandler(keyHelper);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS,
            () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @AfterEach
    public void post_execute() {
        verify(kms, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(proxyKmsClient.client());
        verifyNoMoreInteractions(keyHelper);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DescribeKeyResponse describeKeyResponse =
            DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        when(keyHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        final GetKeyPolicyResponse getKeyPolicyResponse =
            GetKeyPolicyResponse.builder().policy(KEY_POLICY).build();
        when(keyHelper.getKeyPolicy(any(GetKeyPolicyRequest.class), eq(proxyKmsClient)))
            .thenReturn(getKeyPolicyResponse);

        final GetKeyRotationStatusResponse getKeyRotationStatusResponse =
            GetKeyRotationStatusResponse.builder()
                .keyRotationEnabled(KEY_MODEL.getEnableKeyRotation())
                .build();
        when(keyHelper
            .getKeyRotationStatus(any(GetKeyRotationStatusRequest.class), eq(proxyKmsClient)))
            .thenReturn(getKeyRotationStatusResponse);

        final ListResourceTagsResponse listTagsForResourceResponse =
            ListResourceTagsResponse.builder()
                .tags(SDK_TAGS)
                .build();
        when(keyHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().build())
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request,
                new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(KEY_MODEL);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(keyHelper).describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient));
        verify(keyHelper).getKeyPolicy(any(GetKeyPolicyRequest.class), eq(proxyKmsClient));
        verify(keyHelper)
            .getKeyRotationStatus(any(GetKeyRotationStatusRequest.class), eq(proxyKmsClient));
        verify(keyHelper).listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void handleRequest_GetPolicyAccessDenied() {
        final DescribeKeyResponse describeKeyResponse =
            DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        when(keyHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        final CfnAccessDeniedException exception =
            new CfnAccessDeniedException(KmsException.builder()
                .awsErrorDetails(AwsErrorDetails.builder()
                    .errorCode("AccessDeniedException").build())
                .build());
        when(keyHelper.getKeyPolicy(any(GetKeyPolicyRequest.class), eq(proxyKmsClient)))
            .thenThrow(exception);

        final GetKeyRotationStatusResponse getKeyRotationStatusResponse =
            GetKeyRotationStatusResponse.builder()
                .keyRotationEnabled(KEY_MODEL.getEnableKeyRotation())
                .build();
        when(keyHelper
            .getKeyRotationStatus(any(GetKeyRotationStatusRequest.class), eq(proxyKmsClient)))
            .thenReturn(getKeyRotationStatusResponse);

        when(keyHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenThrow(exception);

        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().build())
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request,
                new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(KEY_MODEL_NULL_TAGS_NULL_POLICY);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(keyHelper).describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient));
        verify(keyHelper).getKeyPolicy(any(GetKeyPolicyRequest.class), eq(proxyKmsClient));
        verify(keyHelper)
            .getKeyRotationStatus(any(GetKeyRotationStatusRequest.class), eq(proxyKmsClient));
        verify(keyHelper).listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void handleRequest_KeyDeleted_NotFound() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder()
            .keyMetadata(KEY_METADATA_PENDING_DELETION)
            .build();
        when(keyHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().build())
                .build();

        assertThrows(CfnNotFoundException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(),
                proxyKmsClient, logger));

        verify(keyHelper).describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient));
    }
}
