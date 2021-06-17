package software.amazon.kms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.DisableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyResponse;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.ReplicateKeyRequest;
import software.amazon.awssdk.services.kms.model.ReplicateKeyResponse;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.TagResourceResponse;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceResponse;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

@ExtendWith(MockitoExtension.class)
public class KeyApiHelperTest {
    @Mock
    private KmsClient kms;

    @Mock
    private AmazonWebServicesClientProxy proxy;

    private KeyApiHelper keyApiHelper;
    private ProxyClient<KmsClient> proxyKmsClient;

    @BeforeEach
    public void setup() {
        keyApiHelper = new KeyApiHelper();
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
    }

    @Test
    public void testCreateKey() {
        final CreateKeyRequest createKeyRequest = CreateKeyRequest.builder().build();
        final CreateKeyResponse createKeyResponse = CreateKeyResponse.builder().build();

        doReturn(createKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(createKeyRequest), any());

        assertThat(keyApiHelper.createKey(createKeyRequest, proxyKmsClient))
            .isEqualTo(createKeyResponse);
    }

    @Test
    public void testReplicateKey() {
        final ReplicateKeyRequest replicateKeyRequest = ReplicateKeyRequest.builder().build();
        final ReplicateKeyResponse replicateKeyResponse = ReplicateKeyResponse.builder().build();

        doReturn(replicateKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(replicateKeyRequest), any());

        assertThat(keyApiHelper.replicateKey(replicateKeyRequest, proxyKmsClient))
            .isEqualTo(replicateKeyResponse);
    }

    @Test
    public void testDescribeKey() {
        final DescribeKeyRequest describeKeyRequest = DescribeKeyRequest.builder().build();
        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder().build();

        doReturn(describeKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(describeKeyRequest), any());

        assertThat(keyApiHelper.describeKey(describeKeyRequest, proxyKmsClient))
            .isEqualTo(describeKeyResponse);
    }

    @Test
    public void testDisableKey() {
        final DisableKeyRequest disableKeyRequest = DisableKeyRequest.builder().build();
        final DisableKeyResponse disableKeyResponse = DisableKeyResponse.builder().build();

        doReturn(disableKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(disableKeyRequest), any());

        assertThat(keyApiHelper.disableKey(disableKeyRequest, proxyKmsClient))
            .isEqualTo(disableKeyResponse);
    }

    @Test
    public void testEnableKey() {
        final EnableKeyRequest enableKeyRequest = EnableKeyRequest.builder().build();
        final EnableKeyResponse enableKeyResponse = EnableKeyResponse.builder().build();

        doReturn(enableKeyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(enableKeyRequest), any());

        assertThat(keyApiHelper.enableKey(enableKeyRequest, proxyKmsClient))
            .isEqualTo(enableKeyResponse);
    }

    @Test
    public void testDisableKeyRotation() {
        final DisableKeyRotationRequest disableKeyRotationRequest =
            DisableKeyRotationRequest.builder().build();
        final DisableKeyRotationResponse disableKeyRotationResponse =
            DisableKeyRotationResponse.builder().build();

        doReturn(disableKeyRotationResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(disableKeyRotationRequest), any());

        assertThat(keyApiHelper.disableKeyRotation(disableKeyRotationRequest, proxyKmsClient))
            .isEqualTo(disableKeyRotationResponse);
    }

    @Test
    public void testEnableKeyRotation() {
        final EnableKeyRotationRequest enableKeyRotationRequest =
            EnableKeyRotationRequest.builder().build();
        final EnableKeyRotationResponse enableKeyRotationResponse =
            EnableKeyRotationResponse.builder().build();

        doReturn(enableKeyRotationResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(enableKeyRotationRequest), any());

        assertThat(keyApiHelper.enableKeyRotation(enableKeyRotationRequest, proxyKmsClient))
            .isEqualTo(enableKeyRotationResponse);
    }

    @Test
    public void testGetKeyPolicy() {
        final GetKeyPolicyRequest getKeyPolicyRequest = GetKeyPolicyRequest.builder().build();
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder().build();

        doReturn(getKeyPolicyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(getKeyPolicyRequest), any());

        assertThat(keyApiHelper.getKeyPolicy(getKeyPolicyRequest, proxyKmsClient))
            .isEqualTo(getKeyPolicyResponse);
    }

    @Test
    public void testGetKeyRotationStatus() {
        final GetKeyRotationStatusRequest getKeyRotationStatusRequest =
            GetKeyRotationStatusRequest.builder().build();
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse =
            GetKeyRotationStatusResponse.builder().build();

        doReturn(getKeyRotationStatusResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(getKeyRotationStatusRequest), any());

        assertThat(keyApiHelper.getKeyRotationStatus(getKeyRotationStatusRequest, proxyKmsClient))
            .isEqualTo(getKeyRotationStatusResponse);
    }

    @Test
    public void testListKeys() {
        final ListKeysRequest listKeysRequest = ListKeysRequest.builder().build();
        final ListKeysResponse listKeysResponse = ListKeysResponse.builder().build();

        doReturn(listKeysResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(listKeysRequest), any());

        assertThat(keyApiHelper.listKeys(listKeysRequest, proxyKmsClient))
            .isEqualTo(listKeysResponse);
    }

    @Test
    public void testListResourceTags() {
        final ListResourceTagsRequest listResourceTagsRequest =
            ListResourceTagsRequest.builder().build();
        final ListResourceTagsResponse listResourceTagsResponse =
            ListResourceTagsResponse.builder().build();

        doReturn(listResourceTagsResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(listResourceTagsRequest), any());

        assertThat(keyApiHelper.listResourceTags(listResourceTagsRequest, proxyKmsClient))
            .isEqualTo(listResourceTagsResponse);
    }

    @Test
    public void testPutKeyPolicy() {
        final PutKeyPolicyRequest putKeyPolicyRequest = PutKeyPolicyRequest.builder().build();
        final PutKeyPolicyResponse putKeyPolicyResponse = PutKeyPolicyResponse.builder().build();

        doReturn(putKeyPolicyResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(putKeyPolicyRequest), any());

        assertThat(keyApiHelper.putKeyPolicy(putKeyPolicyRequest, proxyKmsClient))
            .isEqualTo(putKeyPolicyResponse);
    }

    @Test
    public void testScheduleKeyDeletion() {
        final ScheduleKeyDeletionRequest scheduleKeyDeletionRequest =
            ScheduleKeyDeletionRequest.builder().build();
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse =
            ScheduleKeyDeletionResponse.builder().build();

        doReturn(scheduleKeyDeletionResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(scheduleKeyDeletionRequest), any());

        assertThat(keyApiHelper.scheduleKeyDeletion(scheduleKeyDeletionRequest, proxyKmsClient))
            .isEqualTo(scheduleKeyDeletionResponse);
    }

    @Test
    public void testTagResource() {
        final TagResourceRequest tagResourceRequest = TagResourceRequest.builder().build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();

        doReturn(tagResourceResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(tagResourceRequest), any());

        assertThat(keyApiHelper.tagResource(tagResourceRequest, proxyKmsClient))
            .isEqualTo(tagResourceResponse);
    }

    @Test
    public void testUntagResource() {
        final UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder().build();
        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();

        doReturn(untagResourceResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(untagResourceRequest), any());

        assertThat(keyApiHelper.untagResource(untagResourceRequest, proxyKmsClient))
            .isEqualTo(untagResourceResponse);
    }

    @Test
    public void testUpdateKeyDescription() {
        final UpdateKeyDescriptionRequest updateKeyDescriptionRequest =
            UpdateKeyDescriptionRequest.builder().build();
        final UpdateKeyDescriptionResponse updateKeyDescriptionResponse =
            UpdateKeyDescriptionResponse.builder().build();

        doReturn(updateKeyDescriptionResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(updateKeyDescriptionRequest), any());

        assertThat(keyApiHelper.updateKeyDescription(updateKeyDescriptionRequest, proxyKmsClient))
            .isEqualTo(updateKeyDescriptionResponse);
    }
}
