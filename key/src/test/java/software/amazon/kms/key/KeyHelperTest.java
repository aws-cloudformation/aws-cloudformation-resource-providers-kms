package software.amazon.kms.key;

import static junit.framework.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static software.amazon.kms.key.AbstractTestBase.MOCK_PROXY;
import static software.amazon.kms.key.KeyHelper.ACCESS_DENIED_ERROR_CODE;
import static software.amazon.kms.key.KeyHelper.THROTTLING_ERROR_CODE;
import static software.amazon.kms.key.KeyHelper.VALIDATION_ERROR_CODE;


import com.amazonaws.AmazonServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AlreadyExistsException;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DependencyTimeoutException;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.DisabledException;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.InvalidMarkerException;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.LimitExceededException;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.awssdk.services.kms.model.TagException;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.TagResourceResponse;
import software.amazon.awssdk.services.kms.model.UnsupportedOperationException;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceResponse;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionResponse;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

@ExtendWith(MockitoExtension.class)
public class KeyHelperTest {

    @Mock
    private KmsClient kms;

    @Mock
    private AmazonWebServicesClientProxy proxy;

    private KeyHelper keyHelper;
    private ProxyClient<KmsClient> proxyKmsClient;

    @BeforeEach
    public void setup() {
        keyHelper = new KeyHelper();
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @Test
    public void testCreateKey() {
        final CreateKeyRequest createKeyRequest = CreateKeyRequest.builder().build();
        final CreateKeyResponse createKeyResponse = CreateKeyResponse.builder().build();

        doReturn(createKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(createKeyRequest), any());

        assertEquals(createKeyResponse, keyHelper.createKey(createKeyRequest, proxyKmsClient));
    }

    @Test
    public void testDescribeKey() {
        final DescribeKeyRequest describeKeyRequest = DescribeKeyRequest.builder().build();
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(describeKeyRequest), any());

        assertEquals(describeKeyResponse,
            keyHelper.describeKey(describeKeyRequest, proxyKmsClient));
    }

    @Test
    public void testDisableKey() {
        final DisableKeyRequest disableKeyRequest = DisableKeyRequest.builder().build();
        final DisableKeyResponse disableKeyResponse = DisableKeyResponse.builder().build();

        doReturn(disableKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(disableKeyRequest), any());

        assertEquals(disableKeyResponse, keyHelper.disableKey(disableKeyRequest, proxyKmsClient));
    }

    @Test
    public void testEnableKey() {
        final EnableKeyRequest enableKeyRequest = EnableKeyRequest.builder().build();
        final EnableKeyResponse enableKeyResponse = EnableKeyResponse.builder().build();

        doReturn(enableKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(enableKeyRequest), any());

        assertEquals(enableKeyResponse, keyHelper.enableKey(enableKeyRequest, proxyKmsClient));
    }

    @Test
    public void testDisableKeyRotation() {
        final DisableKeyRotationRequest disableKeyRotationRequest =
            DisableKeyRotationRequest.builder().build();
        final DisableKeyRotationResponse disableKeyRotationResponse =
            DisableKeyRotationResponse.builder().build();

        doReturn(disableKeyRotationResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(disableKeyRotationRequest), any());

        assertEquals(disableKeyRotationResponse,
            keyHelper.disableKeyRotation(disableKeyRotationRequest, proxyKmsClient));
    }

    @Test
    public void testEnableKeyRotation() {
        final EnableKeyRotationRequest enableKeyRotationRequest =
            EnableKeyRotationRequest.builder().build();
        final EnableKeyRotationResponse enableKeyRotationResponse =
            EnableKeyRotationResponse.builder().build();

        doReturn(enableKeyRotationResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(enableKeyRotationRequest), any());

        assertEquals(enableKeyRotationResponse,
            keyHelper.enableKeyRotation(enableKeyRotationRequest, proxyKmsClient));
    }

    @Test
    public void testGetKeyPolicy() {
        final GetKeyPolicyRequest getKeyPolicyRequest = GetKeyPolicyRequest.builder().build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();

        doReturn(getKeyPolicyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(getKeyPolicyRequest), any());

        assertEquals(getKeyPolicyResponse,
            keyHelper.getKeyPolicy(getKeyPolicyRequest, proxyKmsClient));
    }

    @Test
    public void testGetKeyRotationStatus() {
        final GetKeyRotationStatusRequest getKeyRotationStatusRequest =
            GetKeyRotationStatusRequest.builder().build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse =
            GetKeyRotationStatusResponse.builder().build();

        doReturn(getKeyRotationStatusResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(getKeyRotationStatusRequest), any());

        assertEquals(getKeyRotationStatusResponse,
            keyHelper.getKeyRotationStatus(getKeyRotationStatusRequest, proxyKmsClient));
    }

    @Test
    public void testListKeys() {
        final ListKeysRequest listKeysRequest = ListKeysRequest.builder().build();
        final ListKeysResponse listKeysResponse = ListKeysResponse.builder().build();

        doReturn(listKeysResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(listKeysRequest), any());

        assertEquals(listKeysResponse, keyHelper.listKeys(listKeysRequest, proxyKmsClient));
    }

    @Test
    public void testListResourceTags() {
        final ListResourceTagsRequest listResourceTagsRequest =
            ListResourceTagsRequest.builder().build();
        final ListResourceTagsResponse listResourceTagsResponse =
            ListResourceTagsResponse.builder().build();

        doReturn(listResourceTagsResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(listResourceTagsRequest), any());

        assertEquals(listResourceTagsResponse,
            keyHelper.listResourceTags(listResourceTagsRequest, proxyKmsClient));
    }

    @Test
    public void testPutKeyPolicy() {
        final PutKeyPolicyRequest putKeyPolicyRequest = PutKeyPolicyRequest.builder().build();
        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();

        doReturn(putKeyPolicyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(putKeyPolicyRequest), any());

        assertEquals(putKeyPolicyResponse,
            keyHelper.putKeyPolicy(putKeyPolicyRequest, proxyKmsClient));
    }

    @Test
    public void testScheduleKeyDeletion() {
        final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest =
            ScheduleKeyDeletionRequest.builder().build();
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse =
            ScheduleKeyDeletionResponse.builder().build();

        doReturn(scheduleKeyDeletionResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(scheduleKeyDeletionRequest), any());

        assertEquals(scheduleKeyDeletionResponse,
            keyHelper.scheduleKeyDeletion(scheduleKeyDeletionRequest, proxyKmsClient));
    }

    @Test
    public void testTagResource() {
        final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();

        doReturn(tagResourceResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(tagResourceRequest), any());

        assertEquals(tagResourceResponse,
            keyHelper.tagResource(tagResourceRequest, proxyKmsClient));
    }

    @Test
    public void testUntagResource() {
        final UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder().build();
        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();

        doReturn(untagResourceResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(untagResourceRequest), any());

        assertEquals(untagResourceResponse,
            keyHelper.untagResource(untagResourceRequest, proxyKmsClient));
    }

    @Test
    public void testUpdateKeyDescription() {
        final UpdateKeyDescriptionRequest updateKeyDescriptionRequest =
            UpdateKeyDescriptionRequest.builder().build();
        final UpdateKeyDescriptionResponse updateKeyDescriptionResponse =
            UpdateKeyDescriptionResponse.builder().build();

        doReturn(updateKeyDescriptionResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(updateKeyDescriptionRequest), any());

        assertEquals(updateKeyDescriptionResponse,
            keyHelper.updateKeyDescription(updateKeyDescriptionRequest, proxyKmsClient));
    }

    @Test
    public void testAlreadyExists() {
        doThrow(AlreadyExistsException.class).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnAlreadyExistsException.class);
    }

    @Test
    public void testInternalFailure() {
        doThrow(InvalidMarkerException.class).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnInternalFailureException.class);
    }

    @Test
    public void testLimitExceeded() {
        doThrow(LimitExceededException.class).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnServiceLimitExceededException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {KmsInvalidStateException.class, InvalidArnException.class,
        MalformedPolicyDocumentException.class, TagException.class,
        UnsupportedOperationException.class,
        DisabledException.class})
    public void testInvalidRequest(final Class<? extends Throwable> kmsException) {
        doThrow(kmsException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnInvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {KmsInternalException.class, DependencyTimeoutException.class})
    public void testServiceInternalError(final Class<? extends Throwable> kmsException) {
        doThrow(kmsException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnServiceInternalErrorException.class);
    }

    @Test
    public void testNotFound() {
        doThrow(NotFoundException.class).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnNotFoundException.class);
    }

    @Test
    public void testThrottling() {
        final AmazonServiceException throttlingException = new AmazonServiceException("");
        throttlingException.setErrorCode(THROTTLING_ERROR_CODE);
        doThrow(throttlingException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnThrottlingException.class);
    }

    @Test
    public void testAccessDenied() {
        final AwsServiceException accessDeniedException = KmsException.builder().awsErrorDetails(
            AwsErrorDetails.builder()
                .sdkHttpResponse(SdkHttpResponse.builder()
                    .statusCode(400)
                    .build())
                .errorCode(ACCESS_DENIED_ERROR_CODE)
                .build())
            .build();
        doThrow(accessDeniedException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnAccessDeniedException.class);
    }

    @Test
    public void testValidationException() {
        final AwsServiceException validationException = KmsException.builder().awsErrorDetails(
            AwsErrorDetails.builder()
                .sdkHttpResponse(SdkHttpResponse.builder()
                    .statusCode(400)
                    .build())
                .errorCode(VALIDATION_ERROR_CODE)
                .build())
            .build();
        doThrow(validationException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnInvalidRequestException.class);
    }

    @Test
    public void testGeneralKmsException() {
        final AwsServiceException generalKmsException =
            KmsException.builder().awsErrorDetails(AwsErrorDetails.builder()
                .build()).build();
        doThrow(generalKmsException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnGeneralServiceException.class);
    }

    @Test
    public void testGeneralAmazonServiceException() {
        final AmazonServiceException generalAmazonServiceException = new AmazonServiceException("");
        doThrow(generalAmazonServiceException).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnGeneralServiceException.class);
    }

    private void assertAllRequestsThrow(final Class<? extends Throwable> cfnException) {
        final CreateKeyRequest createKeyRequest = CreateKeyRequest.builder().build();
        assertThrows(cfnException, () -> keyHelper.createKey(createKeyRequest, proxyKmsClient));

        final DescribeKeyRequest describeKeyRequest = DescribeKeyRequest.builder().build();
        assertThrows(cfnException, () -> keyHelper.describeKey(describeKeyRequest, proxyKmsClient));

        final DisableKeyRequest disableKeyRequest = DisableKeyRequest.builder().build();
        assertThrows(cfnException, () -> keyHelper.disableKey(disableKeyRequest, proxyKmsClient));

        final EnableKeyRequest enableKeyRequest = EnableKeyRequest.builder().build();
        assertThrows(cfnException, () -> keyHelper.enableKey(enableKeyRequest, proxyKmsClient));

        final DisableKeyRotationRequest disableKeyRotationRequest =
            DisableKeyRotationRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.disableKeyRotation(disableKeyRotationRequest, proxyKmsClient));

        final EnableKeyRotationRequest enableKeyRotationRequest =
            EnableKeyRotationRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.enableKeyRotation(enableKeyRotationRequest, proxyKmsClient));

        final GetKeyPolicyRequest getKeyPolicyRequest = GetKeyPolicyRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.getKeyPolicy(getKeyPolicyRequest, proxyKmsClient));

        final GetKeyRotationStatusRequest getKeyRotationStatusRequest =
            GetKeyRotationStatusRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.getKeyRotationStatus(getKeyRotationStatusRequest, proxyKmsClient));

        final ListKeysRequest listKeysRequest = ListKeysRequest.builder().build();
        assertThrows(cfnException, () -> keyHelper.listKeys(listKeysRequest, proxyKmsClient));

        final ListResourceTagsRequest listResourceTagsRequest =
            ListResourceTagsRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.listResourceTags(listResourceTagsRequest, proxyKmsClient));

        final PutKeyPolicyRequest putKeyPolicyRequest = PutKeyPolicyRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.putKeyPolicy(putKeyPolicyRequest, proxyKmsClient));

        final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest =
            ScheduleKeyDeletionRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.scheduleKeyDeletion(scheduleKeyDeletionRequest, proxyKmsClient));

        final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().build();
        assertThrows(cfnException, () -> keyHelper.tagResource(tagResourceRequest, proxyKmsClient));

        final UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.untagResource(untagResourceRequest, proxyKmsClient));

        final UpdateKeyDescriptionRequest updateKeyDescriptionRequest =
            UpdateKeyDescriptionRequest.builder().build();
        assertThrows(cfnException,
            () -> keyHelper.updateKeyDescription(updateKeyDescriptionRequest, proxyKmsClient));
    }
}
