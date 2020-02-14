package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.DisabledException;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionResponse;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.TerminalException;
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
public class UpdateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private UpdateHandler handler;
    private ResourceModel modelWithUpdates;
    private ResourceModel modelWithUpdatesAlt;
    private ResourceHandlerRequest<ResourceModel> requestWithUpdates;
    private ResourceHandlerRequest<ResourceModel> requestWithUpdatesAlt;

    private static final String DESCRIPTION = "DESCRIPTION";
    private static final String UPDATED_DESCRIPTION = "DESCRIPTION.";
    private static final Boolean DISABLED = false;
    private static final Boolean ENABLED = true;
    private static final String KEY_ID = "samplearn";
    private static final String KEY_USAGE = KeyUsageType.ENCRYPT_DECRYPT.toString();
    private static final KeyMetadata KEY_METADATA_REQ1 = KeyMetadata.builder()
            .description(DESCRIPTION)
            .enabled(DISABLED)
            .keyUsage(KEY_USAGE).build();

    private static final KeyMetadata KEY_METADATA_REQ2 = KeyMetadata.builder()
            .description(DESCRIPTION)
            .enabled(ENABLED)
            .keyUsage(KEY_USAGE).build();

    @BeforeEach
    public void setup() {
        handler = new UpdateHandler();
        modelWithUpdates = ResourceModel.builder()
                .keyId(KEY_ID)
                .description(DESCRIPTION)
                .enabled(ENABLED)
                .enableKeyRotation(ENABLED)
                .keyUsage(KEY_USAGE)
                .build();
        modelWithUpdatesAlt = ResourceModel.builder()
                .keyId(KEY_ID)
                .description(UPDATED_DESCRIPTION)
                .enabled(DISABLED)
                .enableKeyRotation(DISABLED)
                .keyUsage(KEY_USAGE)
                .build();
        requestWithUpdates = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(modelWithUpdates)
                .build();
        requestWithUpdatesAlt = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(modelWithUpdatesAlt)
                .build();
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(ENABLED).build();
        final EnableKeyResponse enableKeyResponse = EnableKeyResponse.builder().build();
        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doReturn(enableKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(EnableKeyRequest.class), any());
        doReturn(putKeyPolicyResponse).when(proxy).injectCredentialsAndInvokeV2(any(PutKeyPolicyRequest.class), any());
        doReturn(listResourceTagsResponse).when(proxy).injectCredentialsAndInvokeV2(any(ListResourceTagsRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithUpdates, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.enableKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.putKeyPolicyRequest(modelWithUpdates)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listResourceTagsRequest(KEY_ID)), any());

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
    public void handleRequest_DisabledSuccess() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ2).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(ENABLED).build();
        final DisableKeyResponse disableKeyResponse = DisableKeyResponse.builder().build();
        final DisableKeyRotationResponse disableKeyRotationResponse = DisableKeyRotationResponse.builder().build();
        final UpdateKeyDescriptionResponse updateKeyDescriptionResponse = UpdateKeyDescriptionResponse.builder().build();
        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doReturn(disableKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DisableKeyRequest.class), any());
        doReturn(disableKeyRotationResponse).when(proxy).injectCredentialsAndInvokeV2(any(DisableKeyRotationRequest.class), any());
        doReturn(updateKeyDescriptionResponse).when(proxy).injectCredentialsAndInvokeV2(any(UpdateKeyDescriptionRequest.class), any());
        doReturn(putKeyPolicyResponse).when(proxy).injectCredentialsAndInvokeV2(any(PutKeyPolicyRequest.class), any());
        doReturn(listResourceTagsResponse).when(proxy).injectCredentialsAndInvokeV2(any(ListResourceTagsRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithUpdatesAlt, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.disableKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.disableKeyRotationRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.updateKeyDescriptionRequest(KEY_ID, UPDATED_DESCRIPTION)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.putKeyPolicyRequest(modelWithUpdates)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listResourceTagsRequest(KEY_ID)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(requestWithUpdatesAlt.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_NoKeyUpdateSuccess() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ2).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(ENABLED).build();
        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doReturn(putKeyPolicyResponse).when(proxy).injectCredentialsAndInvokeV2(any(PutKeyPolicyRequest.class), any());
        doReturn(listResourceTagsResponse).when(proxy).injectCredentialsAndInvokeV2(any(ListResourceTagsRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, requestWithUpdates, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.putKeyPolicyRequest(modelWithUpdates)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listResourceTagsRequest(KEY_ID)), any());

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
    public void handleRequest_TerminalTimeout() {

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();
        doReturn(describeKeyResponse,
                getKeyRotationStatusResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        final ResourceModel model = ResourceModel.builder()
                .description(DESCRIPTION)
                .enabled(DISABLED)
                .enableKeyRotation(ENABLED)
                .keyUsage(KEY_USAGE)
                .build();

        final ResourceHandlerRequest<ResourceModel> requestWithUpdates = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        try {
            handler.handleRequest(proxy, requestWithUpdates, null, logger);
        } catch (TerminalException e) {
            assertThat(e.getMessage()).isEqualTo("You cannot modify the EnableKeyRotation property when the Enabled property is false. Set Enabled to true to modify the EnableKeyRotation property.");
        }
    }

    @Test
    public void handleRequest_KeyNotFound() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(ENABLED).build();

        doReturn(describeKeyResponse,
                getKeyRotationStatusResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());
        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(EnableKeyRequest.class), any());
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_KeyInvalidArn() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(ENABLED).build();

        doReturn(describeKeyResponse,
                getKeyRotationStatusResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());
        doThrow(InvalidArnException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(EnableKeyRequest.class), any());
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_KeyKmsInternal() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(ENABLED).build();

        doReturn(describeKeyResponse,
                getKeyRotationStatusResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());
        doThrow(KmsInternalException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(EnableKeyRequest.class), any());
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_KeyMalformedPolicy() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();
        final EnableKeyResponse enableKeyResponse = EnableKeyResponse.builder().build();
        final EnableKeyRotationResponse enableKeyRotationResponse = EnableKeyRotationResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doReturn(enableKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(EnableKeyRequest.class), any());
        doReturn(enableKeyRotationResponse).when(proxy).injectCredentialsAndInvokeV2(any(EnableKeyRotationRequest.class), any());
        doThrow(MalformedPolicyDocumentException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(PutKeyPolicyRequest.class), any());
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_PolicyNotFound() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ1).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();
        final EnableKeyResponse enableKeyResponse = EnableKeyResponse.builder().build();
        final EnableKeyRotationResponse enableKeyRotationResponse = EnableKeyRotationResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doReturn(enableKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(EnableKeyRequest.class), any());
        doReturn(enableKeyRotationResponse).when(proxy).injectCredentialsAndInvokeV2(any(EnableKeyRotationRequest.class), any());
        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(PutKeyPolicyRequest.class), any());
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_DescriptionInvalidArn() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ2).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();
        final DisableKeyResponse disableKeyRequest = DisableKeyResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doReturn(disableKeyRequest).when(proxy).injectCredentialsAndInvokeV2(any(DisableKeyRequest.class), any());
        doThrow(InvalidArnException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(UpdateKeyDescriptionRequest.class), any());
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, requestWithUpdatesAlt, null, logger));
    }

    @Test
    public void handleRequest_RotationNotFound() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ2).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(EnableKeyRotationRequest.class), any());
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_RotationDisabled() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ2).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doThrow(DisabledException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(EnableKeyRotationRequest.class), any());
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }

    @Test
    public void handleRequest_RotationKmsInternalException() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA_REQ2).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(DISABLED).build();

        doReturn(describeKeyResponse).when(proxy).injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());
        doReturn(getKeyRotationStatusResponse).when(proxy).injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());
        doThrow(KmsInternalException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(EnableKeyRotationRequest.class), any());
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, requestWithUpdates, null, logger));
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
    }
}
