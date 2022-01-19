package software.amazon.kms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

@ExtendWith(MockitoExtension.class)
public class CreatableKeyHandlerHelperTest {
    private static final Object MOCK_MODEL = new Object();
    private static final KeyMetadata KEY_METADATA = KeyMetadata.builder()
        .keyId("mock-key-id")
        .arn("mock-arn")
        .keySpec(KeySpec.SYMMETRIC_DEFAULT.toString())
        .keyUsage(KeyUsageType.ENCRYPT_DECRYPT.toString())
        .description("mock-description")
        .keyState(KeyState.ENABLED)
        .enabled(true)
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
    private CreatableKeyHandlerHelper<Object, KeyCallbackContext, CreatableKeyTranslator<Object>>
        keyHandlerHelper;
    private ProxyClient<KmsClient> proxyKmsClient;
    private KeyCallbackContext keyCallbackContext;

    @BeforeEach
    public void setup() {
        keyHandlerHelper =
            new CreatableKeyHandlerHelper<>(TestConstants.MOCK_TYPE_NAME, keyApiHelper,
                eventualConsistencyHandlerHelper, keyTranslator);
        proxy =
            new AmazonWebServicesClientProxy(TestConstants.LOGGER, TestConstants.MOCK_CREDENTIALS,
                () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        keyCallbackContext = new KeyCallbackContext();
    }

    @Test
    public void testCreateKey() {
        final CreateKeyResponse createKeyResponse =
            CreateKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        when(keyApiHelper.createKey(any(CreateKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(createKeyResponse);
        when(keyTranslator.getKeyId(eq(MOCK_MODEL))).thenReturn(null);

        assertThat(keyHandlerHelper
            .createKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, TestConstants.TAGS))
            .isEqualTo(ProgressEvent.defaultInProgressHandler(keyCallbackContext,
                EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS, MOCK_MODEL));

        verify(keyTranslator).setReadOnlyKeyMetadata(eq(MOCK_MODEL), eq(KEY_METADATA));
    }

    @Test
    public void testCreateKeyAlreadyCreated() {
        final CreateKeyResponse createKeyResponse =
            CreateKeyResponse.builder().keyMetadata(KEY_METADATA).build();
        when(keyApiHelper.createKey(any(CreateKeyRequest.class), eq(proxyKmsClient)))
            .thenReturn(createKeyResponse);

        assertThat(keyHandlerHelper
            .createKey(proxy, proxyKmsClient, MOCK_MODEL, keyCallbackContext, TestConstants.TAGS))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, keyCallbackContext));

        verify(keyTranslator, never()).setReadOnlyKeyMetadata(eq(MOCK_MODEL), eq(KEY_METADATA));
    }
}
