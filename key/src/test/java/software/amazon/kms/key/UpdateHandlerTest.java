package software.amazon.kms.key;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.TagResourceResponse;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceResponse;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionResponse;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.TerminalException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase{

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    private UpdateHandler handler;

    private ResourceModel DESIRED_STATE_SCENARIO_1;
    private ResourceModel PREVIOUS_STATE_SCENARIO_1;

    private ResourceModel DESIRED_STATE_SCENARIO_2;
    private ResourceModel PREVIOUS_STATE_SCENARIO_2;

    private ResourceModel DESIRED_STATE_SCENARIO_3;
    private ResourceModel PREVIOUS_STATE_SCENARIO_3;

    private ResourceModel DESIRED_STATE_SCENARIO_4;
    private ResourceModel PREVIOUS_STATE_SCENARIO_4;

    @BeforeEach
    public void setup() {
        handler = new UpdateHandler();
        kms = mock(KmsClient.class);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = MOCK_PROXY(proxy, kms);

        DESIRED_STATE_SCENARIO_1 = ResourceModel.builder()
                .description("sample")
                .enabled(true)
                .enableKeyRotation(false)
                .keyPolicy("{new policy}")
                .build();
        PREVIOUS_STATE_SCENARIO_1 = ResourceModel.builder()
                .enabled(false)
                .enableKeyRotation(true)
                .keyPolicy("{old policy}")
                .build();
        DESIRED_STATE_SCENARIO_2 = ResourceModel.builder()
                .enabled(false)
                .enableKeyRotation(true)
                .keyPolicy("{new policy}")
                .build();
        PREVIOUS_STATE_SCENARIO_2 = ResourceModel.builder()
                .keyPolicy("{old policy}")
                .build();
        DESIRED_STATE_SCENARIO_3 = ResourceModel.builder()
                .keyPolicy("{new policy}")
                .build();
        PREVIOUS_STATE_SCENARIO_3 = ResourceModel.builder()
                .keyPolicy("{old policy}")
                .build();
        DESIRED_STATE_SCENARIO_4 = ResourceModel.builder()
                .enabled(false)
                .enableKeyRotation(false)
                .keyPolicy("{new policy}")
                .build();
        PREVIOUS_STATE_SCENARIO_4 = ResourceModel.builder()
                .enabled(false)
                .enableKeyRotation(false)
                .keyPolicy("{old policy}")
                .build();
    }

    @AfterEach
    public void post_execute() {
        verify(kms, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(proxyKmsClient.client());
    }


    // SCENARIO 1: Enables Key, Disables Rotation
    // Step 1: Enable Key wait for 1 min
    @Test
    public void handleRequest_UpdateCase1EnableKeyStep() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.DISABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final EnableKeyResponse enableKeyResponse = EnableKeyResponse.builder().build();
        when(proxyKmsClient.client().enableKey(any(EnableKeyRequest.class))).thenReturn(enableKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(DESIRED_STATE_SCENARIO_1)
                .previousResourceState(PREVIOUS_STATE_SCENARIO_1)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).enableKey(any(EnableKeyRequest.class));
    }

    // SCENARIO 1: Enables Key, Disables Rotation
    // Step 2: Disable Key Rotation, Update Description, Put Policy, wait for 1 min
    @Test
    public void handleRequest_UpdateCase1DisableRotationAndUpdatePolicyStep() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.DISABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final DisableKeyRotationResponse disableKeyRotationResponse = DisableKeyRotationResponse.builder().build();
        when(proxyKmsClient.client().disableKeyRotation(any(DisableKeyRotationRequest.class))).thenReturn(disableKeyRotationResponse);

        final UpdateKeyDescriptionResponse updateKeyDescriptionResponse = UpdateKeyDescriptionResponse.builder().build();
        when(proxyKmsClient.client().updateKeyDescription(any(UpdateKeyDescriptionRequest.class))).thenReturn(updateKeyDescriptionResponse);

        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().putKeyPolicy(any(PutKeyPolicyRequest.class))).thenReturn(putKeyPolicyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(DESIRED_STATE_SCENARIO_1)
                .previousResourceState(PREVIOUS_STATE_SCENARIO_1)
                .build();

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setKeyEnabled(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().isKeyStatusRotationUpdated()).isEqualTo(true);
        assertThat(response.getCallbackContext().isKeyPolicyUpdated()).isEqualTo(true);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).disableKeyRotation(any(DisableKeyRotationRequest.class));
        verify(proxyKmsClient.client()).updateKeyDescription(any(UpdateKeyDescriptionRequest.class));
        verify(proxyKmsClient.client()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    // SCENARIO 2: Disables Key, Enables Rotation
    // Step: Enable Key Rotation, Disable Key, Put Policy, wait for 1 min
    @Test
    public void handleRequest_UpdateCase2EnableRotationAndDisableKeyStep() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final EnableKeyRotationResponse enableKeyRotationResponse = EnableKeyRotationResponse.builder().build();
        when(proxyKmsClient.client().enableKeyRotation(any(EnableKeyRotationRequest.class))).thenReturn(enableKeyRotationResponse);

        final DisableKeyResponse disableKeyResponse = DisableKeyResponse.builder().build();
        when(proxyKmsClient.client().disableKey(any(DisableKeyRequest.class))).thenReturn(disableKeyResponse);

        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().putKeyPolicy(any(PutKeyPolicyRequest.class))).thenReturn(putKeyPolicyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(DESIRED_STATE_SCENARIO_2)
                .previousResourceState(PREVIOUS_STATE_SCENARIO_2)
                .build();

        CallbackContext callbackContext = new CallbackContext();
        callbackContext.setKeyStatusRotationUpdated(false);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().isKeyStatusRotationUpdated()).isEqualTo(true);
        assertThat(response.getCallbackContext().isKeyPolicyUpdated()).isEqualTo(true);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).enableKeyRotation(any(EnableKeyRotationRequest.class));
        verify(proxyKmsClient.client()).disableKey(any(DisableKeyRequest.class));
        verify(proxyKmsClient.client()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    // SCENARIO 3: No Updates: Key and Rotation are enabled
    // Step: Put Policy, wait for 1 min
    @Test
    public void handleRequest_UpdateCase3PutPolicyStep() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().putKeyPolicy(any(PutKeyPolicyRequest.class))).thenReturn(putKeyPolicyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(DESIRED_STATE_SCENARIO_3)
                .previousResourceState(PREVIOUS_STATE_SCENARIO_3)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().isKeyStatusRotationUpdated()).isEqualTo(true);
        assertThat(response.getCallbackContext().isKeyPolicyUpdated()).isEqualTo(true);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    // SCENARIO 4: No Updates: Key and Rotation are disabled
    // Step: Put Policy, wait for 1 min
    @Test
    public void handleRequest_UpdateCase4PutPolicyStep() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.DISABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();
        when(proxyKmsClient.client().putKeyPolicy(any(PutKeyPolicyRequest.class))).thenReturn(putKeyPolicyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(DESIRED_STATE_SCENARIO_4)
                .previousResourceState(PREVIOUS_STATE_SCENARIO_4)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackContext().isKeyStatusRotationUpdated()).isEqualTo(true);
        assertThat(response.getCallbackContext().isKeyPolicyUpdated()).isEqualTo(true);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).putKeyPolicy(any(PutKeyPolicyRequest.class));
    }

    // SCENARIO 5: Ivalid: Key isn't updated and disabled and Rotation is disabled
    // Step: throw exception
    @Test
    public void handleRequest_InvalidRequest() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.DISABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(
                        ResourceModel.builder()
                                .enabled(false)
                                .enableKeyRotation(false)
                                .build())
                .previousResourceState(
                        ResourceModel.builder()
                                .enabled(false)
                                .enableKeyRotation(true)
                                .build())
            .build();

        try {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);
        } catch (CfnInvalidRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid request provided: You cannot modify the EnableKeyRotation property when the Enabled property is false. Set Enabled to true to modify the EnableKeyRotation property.");
        }

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
    }

    @Test
    public void handleRequest_TagUpdate() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder().tags(SDK_TAGS).nextMarker(null).build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenReturn(listTagsForResourceResponse);

        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();
        when(proxyKmsClient.client().untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);

        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        when(proxyKmsClient.client().tagResource(any(TagResourceRequest.class))).thenReturn(tagResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceTags(MODEL_TAGS)
                .desiredResourceState(ResourceModel.builder().keyPolicy("").build())
                .previousResourceState(ResourceModel.builder().keyPolicy("").build())
                .build();

        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setKeyStatusRotationUpdated(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isNotNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(60);
        assertThat(response.getCallbackContext().keyEnabled).isEqualTo(false);
        assertThat(response.getCallbackContext().propagated).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
        verify(proxyKmsClient.client()).untagResource(any(UntagResourceRequest.class));
        verify(proxyKmsClient.client()).tagResource(any(TagResourceRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccessPaginatedTags() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ListResourceTagsResponse listTagsForResourceResponse1 = ListResourceTagsResponse.builder().tags(SDK_TAGS).nextMarker("marker").build();
        final ListResourceTagsResponse listTagsForResourceResponse2 = ListResourceTagsResponse.builder().build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenReturn(
                listTagsForResourceResponse1,
                listTagsForResourceResponse2);

        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();
        when(proxyKmsClient.client().untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().keyPolicy("new").build())
                .previousResourceState(ResourceModel.builder().keyPolicy("old").build())
                .build();

        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setKeyStatusRotationUpdated(true);
        callbackContext.setKeyPolicyUpdated(true);
        callbackContext.setPropagated(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client(), times(2)).listResourceTags(any(ListResourceTagsRequest.class));
        verify(proxyKmsClient.client()).untagResource(any(UntagResourceRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder().build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenReturn(listTagsForResourceResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().keyPolicy("new").build())
                .previousResourceState(ResourceModel.builder().keyPolicy("old").build())
                .build();

        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setKeyStatusRotationUpdated(true);
        callbackContext.setKeyPolicyUpdated(true);
        callbackContext.setPropagated(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
    }


    // error handler
    @Test
    public void handleRequest_Fail() {
        final KeyMetadata keyMetadata = KeyMetadata.builder().keyState(KeyState.ENABLED).build();

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(keyMetadata).build();
        when(proxyKmsClient.client().describeKey(any(DescribeKeyRequest.class))).thenReturn(describeKeyResponse);

        final KmsException exception = (KmsException) KmsException.builder().build();
        when(proxyKmsClient.client().listResourceTags(any(ListResourceTagsRequest.class))).thenThrow(exception);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().keyPolicy("new").build())
                .previousResourceState(ResourceModel.builder().keyPolicy("old").build())
                .build();

        final CallbackContext callbackContext = new CallbackContext();
        callbackContext.setKeyStatusRotationUpdated(true);
        callbackContext.setKeyPolicyUpdated(true);
        callbackContext.setPropagated(true);

        assertThrows(TerminalException.class,
                () ->handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, logger));

        verify(proxyKmsClient.client()).describeKey(any(DescribeKeyRequest.class));
        verify(proxyKmsClient.client()).listResourceTags(any(ListResourceTagsRequest.class));
    }

}
