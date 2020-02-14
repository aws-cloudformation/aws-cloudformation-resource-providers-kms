package software.amazon.kms.key;

import com.google.common.collect.ImmutableMap;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private CreateHandler handler;
    private ResourceModel modelWithUpdates;
    private ResourceModel modelWithNoUpdates;
    private ResourceHandlerRequest<ResourceModel> requestWithUpdates;
    private ResourceHandlerRequest<ResourceModel> requestWithNoUpdates;

    private static final String ARN = "samplearn";
    private static final String DESCRIPTION = "DESCRIPTION";
    private static final String KEY_ID = "sampleKeyId";
    private static final String KEY_USAGE = KeyUsageType.ENCRYPT_DECRYPT.toString();
    private static final Map<String, Object> KEY_POLICY = ImmutableMap.of("foo", "bar");

    @BeforeEach
    public void setup() {
        handler = new CreateHandler();
        modelWithUpdates = ResourceModel.builder()
                .arn(ARN)
                .keyId(KEY_ID)
                .description(DESCRIPTION)
                .enableKeyRotation(true)
                .enabled(false)
                .keyUsage(KEY_USAGE)
                .keyPolicy(KEY_POLICY)
                .build();
        modelWithNoUpdates = ResourceModel.builder()
                .keyId(KEY_ID)
                .build();

        requestWithUpdates = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(modelWithUpdates).build();
        requestWithNoUpdates = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(modelWithNoUpdates).build();
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CallbackContext context = CallbackContext.builder().build();
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithUpdates, context, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.enableKeyRotationRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.disableKeyRequest(KEY_ID)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(requestWithUpdates.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_CreateKeyNotYetStabilizedSuccess() {
        final CreateKeyResponse createKeyResponse = CreateKeyResponse.builder()
                .keyMetadata(KeyMetadata.builder().keyId(KEY_ID).build()).build();

        doReturn(createKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(CreateKeyRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithUpdates, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getResourceModel()).isEqualTo(requestWithUpdates.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleSuccessWithNoUpdates() {
        final CallbackContext context = CallbackContext.builder().build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithNoUpdates, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(requestWithNoUpdates.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_CreateKeyStabilizedSuccess() {
        final EnableKeyRotationResponse enableKeyRotationResponse = EnableKeyRotationResponse.builder().build();
        final DisableKeyRotationResponse disableKeyRotationResponse = DisableKeyRotationResponse.builder().build();

        doReturn(enableKeyRotationResponse,
                disableKeyRotationResponse).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        final CallbackContext context = CallbackContext.builder().build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithUpdates, context, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(requestWithUpdates.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_MalformedPolicy() {
        doThrow(MalformedPolicyDocumentException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
    }

    @Test
    public void handleRequest_KmsInternal() {
        doThrow(KmsInternalException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
    }
}
