package software.amazon.kms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsRequest;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.awssdk.services.kms.model.PutKeyPolicyRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionResponse;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UpdateKeyDescriptionRequest;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

@ExtendWith(MockitoExtension.class)
public class KeyHandlerHelperTest {
    private static final Object MOCK_MODEL = new Object();
    private static final String UPDATED_KEY_POLICY = "{\"foo\":\"bar2\"}";
    private static final Map<String, String> UPDATED_TAGS =
        new ImmutableMap.Builder<String, String>()
            .put("Key2", "Value2")
            .build();
    private static final KeyMetadata.Builder KEY_METADATA_BUILDER = KeyMetadata.builder()
        .keyId("mock-key-id")
        .arn("mock-arn")
        .keySpec(KeySpec.SYMMETRIC_DEFAULT.toString())
        .origin(OriginType.AWS_KMS.toString())
        .keyUsage(KeyUsageType.ENCRYPT_DECRYPT.toString())
        .description("mock-description")
        .keyState(KeyState.ENABLED)
        .enabled(true);
    private static final KeyMetadata KEY_METADATA = KEY_METADATA_BUILDER.build();
    private static final KeyMetadata KEY_METADATA_PENDING_DELETION = KEY_METADATA_BUILDER
        .keyState(KeyState.PENDING_DELETION)
        .build();
    private static final KeyMetadata KEY_METADATA_PENDING_REPLICA_DELETION = KEY_METADATA_BUILDER
        .keyState(KeyState.PENDING_REPLICA_DELETION)
        .build();

    @Mock
    private KmsClient kms;

    @Mock
    private KeyApiHelper keyApiHelper;

    @Mock
    private EventualConsistencyHandlerHelper<Object, KeyCallbackContext>
        eventualConsistencyHandlerHelper;

    @Spy
    private MockKeyTranslator keyTranslator;

    private AmazonWebServicesClientProxy proxy;
    private KeyHandlerHelper<Object, KeyCallbackContext, KeyTranslator<Object>> keyHandlerHelper;
    private ProxyClient<KmsClient> proxyKmsClient;
    private KeyCallbackContext keyCallbackContext;

    @BeforeEach
    public void setup() {
        keyHandlerHelper =
            new KeyHandlerHelper<>(TestConstants.MOCK_TYPE_NAME, keyApiHelper,
                eventualConsistencyHandlerHelper, keyTranslator);
        proxy =
            new AmazonWebServicesClientProxy(TestConstants.LOGGER, TestConstants.MOCK_CREDENTIALS,
                () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        keyCallbackContext = new KeyCallbackContext();
    }

    @Test
    public void testDescribeKey() {
        final DescribeKeyResponse describeKeyResponse =
            DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        assertThat(keyHandlerHelper
            .describeKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, true))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyTranslator).setKeyMetadata(eq(MOCK_MODEL), eq(KEY_METADATA));
    }

    @ParameterizedTest
    @MethodSource("pendingDeletionMetadataProvider")
    public void testDescribeKeyPendingDeletion(final KeyMetadata pendingDeletionMetadata) {
        final DescribeKeyResponse describeKeyResponse =
            DescribeKeyResponse.builder().keyMetadata(pendingDeletionMetadata).build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        assertThatExceptionOfType(CfnNotFoundException.class).isThrownBy(() -> keyHandlerHelper
            .describeKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, true));
    }

    @Test
    public void testDescribeKeyNoUpdate() {
        final DescribeKeyResponse describeKeyResponse =
            DescribeKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        assertThat(keyHandlerHelper
            .describeKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, false))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyTranslator, never()).setKeyMetadata(eq(MOCK_MODEL), eq(KEY_METADATA));
    }

    @Test
    public void testGetKeyPolicy() {
        final GetKeyPolicyResponse getKeyPolicyResponse = GetKeyPolicyResponse.builder()
            .policy(TestConstants.KEY_POLICY).build();
        when(keyApiHelper.getKeyPolicy(any(GetKeyPolicyRequest.class), eq(proxyKmsClient)))
            .thenReturn(getKeyPolicyResponse);

        assertThat(
            keyHandlerHelper.getKeyPolicy(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyTranslator)
            .setKeyPolicy(eq(MOCK_MODEL), eq(TestConstants.DESERIALIZED_KEY_POLICY));
    }

    @Test
    public void testUpdateKeyDescription() {
        when(keyTranslator.getKeyDescription(MOCK_MODEL)).thenReturn("Desc 1").thenReturn("Desc 2");

        assertThat(keyHandlerHelper
            .updateKeyDescription(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL,
                keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyApiHelper)
            .updateKeyDescription(any(UpdateKeyDescriptionRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testUpdateKeyDescriptionNotRequired() {
        assertThat(keyHandlerHelper
            .updateKeyDescription(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL,
                keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyApiHelper, never())
            .updateKeyDescription(any(UpdateKeyDescriptionRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testUpdateKeyPolicy() {
        when(keyTranslator.getKeyPolicy(MOCK_MODEL)).thenReturn(TestConstants.KEY_POLICY)
            .thenReturn(
                UPDATED_KEY_POLICY);

        assertThat(keyHandlerHelper
            .updateKeyPolicy(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL, keyCallbackContext))
            .isEqualTo(ProgressEvent.defaultInProgressHandler(keyCallbackContext,
                EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS, MOCK_MODEL));

        verify(keyApiHelper).putKeyPolicy(any(PutKeyPolicyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testUpdateKeyPolicyNotRequired() {
        assertThat(keyHandlerHelper
            .updateKeyPolicy(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL, keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.isKeyPolicyUpdated()).isFalse();

        verify(keyApiHelper, never())
            .putKeyPolicy(any(PutKeyPolicyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testUpdateKeyPolicyAlreadyUpdated() {
        keyCallbackContext.setKeyPolicyUpdated(true);
        when(keyTranslator.getKeyPolicy(MOCK_MODEL)).thenReturn(TestConstants.KEY_POLICY)
            .thenReturn(
                UPDATED_KEY_POLICY);

        assertThat(keyHandlerHelper
            .updateKeyPolicy(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL, keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.isKeyPolicyUpdated()).isTrue();

        verify(keyApiHelper, never())
            .putKeyPolicy(any(PutKeyPolicyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testEnableKeyIfNecessary() {
        when(keyTranslator.getKeyEnabled(MOCK_MODEL)).thenReturn(true).thenReturn(false);

        assertThat(keyHandlerHelper
            .enableKeyIfNecessary(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL, keyCallbackContext,
                true)).isEqualTo(ProgressEvent.defaultInProgressHandler(keyCallbackContext,
            EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS, MOCK_MODEL));
        assertThat(keyCallbackContext.isKeyEnabled()).isTrue();

        verify(keyApiHelper).enableKey(any(EnableKeyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testEnableKeyIfNecessaryNoDelay() {
        when(keyTranslator.getKeyEnabled(MOCK_MODEL)).thenReturn(true).thenReturn(false);

        assertThat(keyHandlerHelper
            .enableKeyIfNecessary(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL, keyCallbackContext,
                false)).isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.isKeyEnabled()).isTrue();

        verify(keyApiHelper).enableKey(any(EnableKeyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testEnableKeyIfNecessaryNotRequired() {
        assertThat(keyHandlerHelper
            .enableKeyIfNecessary(proxy, proxyKmsClient, null, MOCK_MODEL, keyCallbackContext,
                true)).isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.isKeyPolicyUpdated()).isFalse();

        verify(keyApiHelper, never()).enableKey(any(EnableKeyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testEnableKeyIfNecessaryAlreadyEnabled() {
        keyCallbackContext.setKeyEnabled(true);
        when(keyTranslator.getKeyEnabled(eq(MOCK_MODEL))).thenReturn(true).thenReturn(false);

        assertThat(keyHandlerHelper
            .enableKeyIfNecessary(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL, keyCallbackContext,
                true)).isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.isKeyPolicyUpdated()).isFalse();

        verify(keyApiHelper, never()).enableKey(any(EnableKeyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testDisableKeyIfNecessary() {
        when(keyTranslator.getKeyEnabled(eq(MOCK_MODEL))).thenReturn(false);

        assertThat(keyHandlerHelper
            .disableKeyIfNecessary(proxy, proxyKmsClient, null, MOCK_MODEL,
                keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyApiHelper).disableKey(any(DisableKeyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testDisableKeyIfNecessaryNotRequired() {
        assertThat(keyHandlerHelper
            .disableKeyIfNecessary(proxy, proxyKmsClient, MOCK_MODEL, MOCK_MODEL,
                keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyApiHelper, never()).disableKey(any(DisableKeyRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testRetrieveResourceTags() {
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder()
            .tags(TestConstants.SDK_TAGS)
            .nextMarker(null)
            .build();
        when(keyApiHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenReturn(listResourceTagsResponse);

        assertThat(keyHandlerHelper
            .retrieveResourceTags(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, true))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.getExistingTags()).isEqualTo(TestConstants.SDK_TAGS);
        assertThat(keyCallbackContext.getTagMarker()).isNull();

        verify(keyTranslator).setTags(eq(MOCK_MODEL), eq(TestConstants.SDK_TAGS));
    }

    @Test
    public void testRetrieveResourceTagsNoUpdate() {
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder()
            .tags(TestConstants.SDK_TAGS)
            .nextMarker(null)
            .build();
        when(keyApiHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenReturn(listResourceTagsResponse);

        assertThat(keyHandlerHelper
            .retrieveResourceTags(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, false))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.getExistingTags()).isEqualTo(TestConstants.SDK_TAGS);
        assertThat(keyCallbackContext.getTagMarker()).isNull();

        verify(keyTranslator, never()).setTags(eq(MOCK_MODEL), eq(TestConstants.SDK_TAGS));
    }

    @Test
    public void testRetrieveResourceTagsAccessDenied() {
        when(keyApiHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenThrow(CfnAccessDeniedException.class);

        assertThatExceptionOfType(CfnAccessDeniedException.class).isThrownBy(() -> keyHandlerHelper
            .retrieveResourceTags(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, false));

        assertThat(keyCallbackContext.getExistingTags()).isNull();
        assertThat(keyCallbackContext.getTagMarker()).isNull();

        verify(keyTranslator, never()).setTags(eq(MOCK_MODEL), eq(TestConstants.SDK_TAGS));
    }

    @Test
    public void testUpdateKeyTags() {
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder()
            .tags(TestConstants.SDK_TAGS)
            .nextMarker(null)
            .build();

        when(keyApiHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenReturn(listResourceTagsResponse);

        assertThat(keyHandlerHelper
            .updateKeyTags(proxy, proxyKmsClient, MOCK_MODEL, UPDATED_TAGS, keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.getExistingTags()).isEqualTo(TestConstants.SDK_TAGS);
        assertThat(keyCallbackContext.getTagMarker()).isNull();

        verify(keyApiHelper).tagResource(any(TagResourceRequest.class), eq(proxyKmsClient));
        verify(keyApiHelper).untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testUpdateKeyTagsNotRequired() {
        final ListResourceTagsResponse listResourceTagsResponse = ListResourceTagsResponse.builder()
            .tags(TestConstants.SDK_TAGS)
            .nextMarker(null)
            .build();
        when(keyApiHelper.listResourceTags(any(ListResourceTagsRequest.class), eq(proxyKmsClient)))
            .thenReturn(listResourceTagsResponse);

        assertThat(keyHandlerHelper
            .updateKeyTags(proxy, proxyKmsClient, MOCK_MODEL, TestConstants.TAGS,
                keyCallbackContext))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));
        assertThat(keyCallbackContext.getExistingTags()).isEqualTo(TestConstants.SDK_TAGS);
        assertThat(keyCallbackContext.getTagMarker()).isNull();

        verify(keyApiHelper, never())
            .tagResource(any(TagResourceRequest.class), eq(proxyKmsClient));
        verify(keyApiHelper, never())
            .untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient));
    }

    @ParameterizedTest
    @EnumSource(value = KeyState.class, names = {"PENDING_DELETION", "PENDING_REPLICA_DELETION"})
    public void testDeleteKey(final KeyState deletedKeyState) {
        final ScheduleKeyDeletionResponse scheduleKeyDeletionResponse =
            ScheduleKeyDeletionResponse.builder().build();
        when(keyApiHelper
            .scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class), eq(proxyKmsClient)))
            .thenReturn(scheduleKeyDeletionResponse);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder()
            .keyMetadata(KeyMetadata.builder().keyState(deletedKeyState).build())
            .build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        final ProgressEvent<Object, KeyCallbackContext> expectedProgressEvent =
            ProgressEvent.progress(MOCK_MODEL, keyCallbackContext);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(expectedProgressEvent)))
            .thenReturn(expectedProgressEvent);

        assertThat(
            keyHandlerHelper.deleteKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(null));

        verify(keyApiHelper)
            .scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class), eq(proxyKmsClient));
        verify(keyApiHelper).describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient));
        verify(eventualConsistencyHandlerHelper)
            .waitForChangesToPropagate(eq(expectedProgressEvent));
    }

    @Test
    public void testDeleteKeyInvalidState() {
        when(keyApiHelper
            .scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class), eq(proxyKmsClient)))
            .thenThrow(new CfnInvalidRequestException(KmsInvalidStateException.builder().build()));


        assertThat(
            keyHandlerHelper.deleteKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext))
            .isEqualTo(ProgressEvent.builder()
                .status(OperationStatus.FAILED)
                .errorCode(HandlerErrorCode.NotFound)
                .build());

        verify(keyApiHelper)
            .scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testDeleteKeyInvalidRequest() {
        when(keyApiHelper
            .scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class), eq(proxyKmsClient)))
            .thenThrow(CfnInvalidRequestException.class);

        assertThatExceptionOfType(CfnInvalidRequestException.class).isThrownBy(
            () -> keyHandlerHelper
                .deleteKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext));
    }

    @Test
    public void testListKeysAndFilterByMetadata() {
        final ListKeysResponse listKeysResponse = ListKeysResponse.builder()
            .keys(Collections
                .singletonList(KeyListEntry.builder().keyArn(KEY_METADATA.arn()).build()))
            .nextMarker(TestConstants.NEXT_MARKER).build();
        when(keyApiHelper.listKeys(any(ListKeysRequest.class), eq(proxyKmsClient)))
            .thenReturn(listKeysResponse);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder()
            .keyMetadata(KEY_METADATA)
            .build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        when(keyTranslator.translateKeyListEntry(eq(listKeysResponse.keys().iterator().next())))
            .thenReturn(MOCK_MODEL);

        assertThat(keyHandlerHelper.listKeysAndFilterByMetadata(proxyKmsClient, null, k -> true))
            .isEqualTo(ProgressEvent.builder()
                .status(OperationStatus.SUCCESS)
                .resourceModels(Collections.singletonList(MOCK_MODEL))
                .nextToken(TestConstants.NEXT_MARKER)
                .build());
    }

    @ParameterizedTest
    @MethodSource("pendingDeletionMetadataProvider")
    public void testListKeysAndFilterByMetadataPendingDeletion(
        final KeyMetadata pendingDeletionMetadata) {
        final ListKeysResponse listKeysResponse = ListKeysResponse.builder()
            .keys(Collections
                .singletonList(KeyListEntry.builder().keyArn(KEY_METADATA.arn()).build()))
            .nextMarker(TestConstants.NEXT_MARKER).build();
        when(keyApiHelper.listKeys(any(ListKeysRequest.class), eq(proxyKmsClient)))
            .thenReturn(listKeysResponse);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder()
            .keyMetadata(pendingDeletionMetadata)
            .build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        assertThat(keyHandlerHelper.listKeysAndFilterByMetadata(proxyKmsClient, null, k -> true))
            .isEqualTo(ProgressEvent.builder()
                .status(OperationStatus.SUCCESS)
                .resourceModels(Collections.emptyList())
                .nextToken(TestConstants.NEXT_MARKER)
                .build());
    }

    @Test
    public void testListKeysAndFilterByMetadataFilterOut() {
        final ListKeysResponse listKeysResponse = ListKeysResponse.builder()
            .keys(Collections
                .singletonList(KeyListEntry.builder().keyArn(KEY_METADATA.arn()).build()))
            .nextMarker(TestConstants.NEXT_MARKER).build();
        when(keyApiHelper.listKeys(any(ListKeysRequest.class), eq(proxyKmsClient)))
            .thenReturn(listKeysResponse);

        final DescribeKeyResponse describeKeyResponse = DescribeKeyResponse.builder()
            .keyMetadata(KEY_METADATA)
            .build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(describeKeyResponse);

        assertThat(keyHandlerHelper.listKeysAndFilterByMetadata(proxyKmsClient, null, k -> false))
            .isEqualTo(ProgressEvent.builder()
                .status(OperationStatus.SUCCESS)
                .resourceModels(Collections.emptyList())
                .nextToken(TestConstants.NEXT_MARKER)
                .build());
    }

    private static KeyMetadata[] pendingDeletionMetadataProvider() {
        return new KeyMetadata[] {
            KEY_METADATA_PENDING_DELETION,
            KEY_METADATA_PENDING_REPLICA_DELETION
        };
    }
}
