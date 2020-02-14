package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private ReadHandler handler;
    private ResourceHandlerRequest<ResourceModel> request;

    private static final String KEY_ID = "samplearn";
    private static final String DESCRIPTION = "DESCRIPTION";
    private static final Boolean ENABLED = true;
    private static final Boolean ENABLE_KEY_ROTATION = true;
    private static final Boolean KEY_ROTATION = true;
    private static final String KEY_USAGE = KeyUsageType.ENCRYPT_DECRYPT.toString();
    private static final KeyMetadata KEY_METADATA = KeyMetadata.builder()
            .keyId(KEY_ID)
            .description(DESCRIPTION)
            .enabled(ENABLED)
            .keyUsage(KEY_USAGE).build();
    private static final String POLICY = "{\"foo\": \"bar\"}";
    private static final String TAG_KEY = "key";
    private static final String TAG_VALUE = "value";

    @BeforeEach
    public void setup() {
        request = ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(ResourceModel.builder().keyId(KEY_ID).build()).build();
        handler = new ReadHandler();
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(KEY_ROTATION).build();
        final GetKeyPolicyResponse keyPolicyResponse = GetKeyPolicyResponse.builder().policy(POLICY).build();
        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder()
                .tags(Collections.singletonList(Tag.builder().tagKey(TAG_KEY).tagValue(TAG_VALUE).build()))
                .build();

        doReturn(describeKeyResponse,
                keyPolicyResponse,
                getKeyRotationStatusResponse,
                listTagsForResourceResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        final Set<software.amazon.kms.key.Tag> tags = Collections.singleton(software.amazon.kms.key.Tag.builder().key(TAG_KEY).value(TAG_VALUE).build());

        final Map<String, Object> policyObject = new HashMap<>();
        policyObject.put("foo", "bar");

        final ResourceModel expectedModel = ResourceModel.builder()
                .keyId(KEY_ID)
                .description(DESCRIPTION)
                .enabled(ENABLED)
                .keyPolicy(policyObject)
                .enableKeyRotation(ENABLE_KEY_ROTATION)
                .keyUsage(KEY_USAGE)
                .tags(tags)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.describeKeyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyRotationStatusRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.getKeyPolicyRequest(KEY_ID)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listResourceTagsRequest(KEY_ID)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(expectedModel);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_PolicyNullSuccess() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse = GetKeyRotationStatusResponse.builder().keyRotationEnabled(KEY_ROTATION).build();
        final GetKeyPolicyResponse keyPolicyResponse = GetKeyPolicyResponse.builder().policy(null).build();
        final ListResourceTagsResponse listTagsForResourceResponse = ListResourceTagsResponse.builder()
                .tags(Collections.singletonList(software.amazon.awssdk.services.kms.model.Tag.builder().tagKey(TAG_KEY).tagValue(TAG_VALUE).build()))
                .build();

        doReturn(describeKeyResponse,
                keyPolicyResponse,
                getKeyRotationStatusResponse,
                listTagsForResourceResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        final Set<software.amazon.kms.key.Tag> tags = Collections.singleton(software.amazon.kms.key.Tag.builder().key(TAG_KEY).value(TAG_VALUE).build());
        final ResourceModel expectedModel = ResourceModel.builder()
                .keyId(KEY_ID)
                .description(DESCRIPTION)
                .enabled(ENABLED)
                .keyPolicy(null)
                .enableKeyRotation(ENABLE_KEY_ROTATION)
                .keyUsage(KEY_USAGE)
                .tags(tags)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
                handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getResourceModel()).isEqualTo(expectedModel);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_PolicyNotFound() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();

        doReturn(describeKeyResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());

        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetKeyPolicyRequest.class), any());

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KeyRotationInvalidArn() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();

        doReturn(describeKeyResponse,
                getKeyPolicyResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        doThrow(InvalidArnException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());

        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KeyRotationNotFound() {
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();

        doReturn(describeKeyResponse,
                getKeyPolicyResponse).when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(GetKeyRotationStatusRequest.class), any());

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KeyInvalidArn() {
        doThrow(InvalidArnException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());

        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KeyKmsInternal() {
        doThrow(KmsInternalException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());

        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void handleRequest_KeyNotFound() {
        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(DescribeKeyRequest.class), any());


        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }
}
