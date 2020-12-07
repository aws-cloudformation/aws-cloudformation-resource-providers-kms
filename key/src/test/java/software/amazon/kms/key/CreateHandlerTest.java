package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.CustomerMasterKeySpec;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.key.ResourceModel.ResourceModelBuilder;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {
    private static final String KEY_POLICY = "{\"foo\":\"bar\"}";
    private static final ResourceModelBuilder KEY_MODEL_BUILDER = ResourceModel.builder()
        .enableKeyRotation(false)
        .keySpec(CustomerMasterKeySpec.SYMMETRIC_DEFAULT.toString())
        .keyUsage(KeyUsageType.ENCRYPT_DECRYPT.toString())
        .description("mock-description")
        .enabled(true)
        .keyPolicy(ReadHandler.deserializeKeyPolicy(KEY_POLICY));
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL_CREATED = KEY_MODEL_BUILDER
        .keyId("mock-key-id")
        .arn("mock-arn")
        .build();
    private static final ResourceModel KEY_MODEL_DISABLED_ROTATION_ENABLED = KEY_MODEL_BUILDER
        .enabled(false)
        .enableKeyRotation(true)
        .build();
    private static final ResourceModel KEY_MODEL_ASYMMETRIC_ROTATION_ENABLED = KEY_MODEL_BUILDER
        .keySpec(CustomerMasterKeySpec.RSA_4096.toString())
        .keyUsage(KeyUsageType.SIGN_VERIFY.toString())
        .enableKeyRotation(true)
        .build();
    private static final CreateKeyRequest EXPECTED_CREATE_KEY_REQUEST = CreateKeyRequest.builder()
        .customerMasterKeySpec(KEY_MODEL.getKeySpec())
        .keyUsage(KEY_MODEL.getKeyUsage())
        .description(KEY_MODEL.getDescription())
        .policy(KEY_POLICY)
        .tags(Translator.translateTagsToSdk(MODEL_TAGS))
        .build();

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    @Mock
    private KeyHelper keyHelper;

    private CreateHandler handler;

    @BeforeEach
    public void setup() {
        handler = new CreateHandler(keyHelper);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS,
            () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    // Key has been created, waiting on propagation
    @Test
    public void handleRequest_PartiallyPropagate() {
        final CreateKeyResponse createKeyResponse =
            CreateKeyResponse.builder().keyMetadata(KeyMetadata.builder().build()).build();
        when(keyHelper.createKey(any(CreateKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(createKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL)
                .desiredResourceTags(MODEL_TAGS)
                .build();
        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getCallbackContext().propagated).isEqualTo(false);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verifyCreateKey();
        verifyServiceNameCalledAtLeastOnce();
    }

    // Key has been created and provisioned, waiting on final propagation
    @Test
    public void handleRequest_FullyPropagate() {
        final CreateKeyResponse createKeyResponse = CreateKeyResponse.builder()
            .keyMetadata(KeyMetadata.builder().build())
            .build();
        when(keyHelper.createKey(any(CreateKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(createKeyResponse);

        final EnableKeyRotationResponse enableKeyRotationResponse =
            EnableKeyRotationResponse.builder().build();
        when(keyHelper.enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
            .thenReturn(enableKeyRotationResponse);

        final DisableKeyResponse disableKeyResponse = DisableKeyResponse.builder().build();
        when(keyHelper.disableKey(any(DisableKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(disableKeyResponse);


        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL_DISABLED_ROTATION_ENABLED)
                .desiredResourceTags(MODEL_TAGS)
                .build();
        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getCallbackContext().propagated).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(keyHelper)
            .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient));
        verify(keyHelper).disableKey(any(DisableKeyRequest.class), eq(proxyKmsClient));
        verifyCreateKey();
        verifyServiceNameCalledAtLeastOnce();
    }

    // Key has been created and fully provisioned, success
    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateKeyResponse createKeyResponse =
            CreateKeyResponse.builder().keyMetadata(KeyMetadata.builder().build()).build();
        when(keyHelper.createKey(any(CreateKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(createKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL_CREATED)
                .desiredResourceTags(MODEL_TAGS)
                .build();
        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setPropagated(true);
        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verifyCreateKey();
        verifyServiceNameCalledAtLeastOnce();
    }

    @Test
    public void handleRequest_AsymmetricRotationEnabled() {
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL_ASYMMETRIC_ROTATION_ENABLED)
                .build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);
        } catch (CfnInvalidRequestException e) {
            assertThat(e.getMessage()).isEqualTo(
                "Invalid request provided: You cannot set the EnableKeyRotation property to true on asymmetric keys.");
        }
    }

    private void verifyCreateKey() {
        final ArgumentCaptor<CreateKeyRequest> requestCaptor =
            ArgumentCaptor.forClass(CreateKeyRequest.class);
        verify(keyHelper).createKey(requestCaptor.capture(), eq(proxyKmsClient));
        assertThat(EXPECTED_CREATE_KEY_REQUEST.equalsBySdkFields(requestCaptor.getValue()))
            .isTrue();
    }

    private void verifyServiceNameCalledAtLeastOnce() {
        verify(kms, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(proxyKmsClient.client());
    }
}
