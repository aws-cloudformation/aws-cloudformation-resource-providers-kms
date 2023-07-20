package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationRequest;
import software.amazon.awssdk.services.kms.model.EnableKeyRotationResponse;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.CreatableKeyHandlerHelper;
import software.amazon.kms.common.CreatableKeyTranslator;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.TagHelper;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {
    private static final ResourceModel.ResourceModelBuilder KEY_MODEL_BUILDER =
        ResourceModel.builder()
            .keyId("mock-key-id")
            .arn("mock-arn")
            .enableKeyRotation(true)
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .origin(OriginType.AWS_KMS.toString())
            .pendingWindowInDays(7)
            .tags(ImmutableSet.of(Tag.builder()
                .key("Key")
                .value("Value")
                .build()));
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL_WITH_DEFAULTS_SET = KEY_MODEL_BUILDER
        .description("")
        .enabled(true)
        .keyUsage(KeyUsageType.ENCRYPT_DECRYPT.toString())
        .keySpec(KeySpec.SYMMETRIC_DEFAULT.toString())
        .multiRegion(false)
        .build();
    private static final ResourceModel KEY_MODEL_REDACTED = KEY_MODEL_BUILDER
        .pendingWindowInDays(null)
        .build();
    private static final ResourceModel KEY_MODEL_ASYMMETRIC_ROTATION_ENABLED = KEY_MODEL_BUILDER
        .keySpec(KeySpec.RSA_4096.toString())
        .build();
    private static final ResourceModel KEY_MODEL_EXTERNAL_ROTATION_ENABLED = KEY_MODEL_BUILDER
        .origin(OriginType.EXTERNAL.toString())
        .build();
    private static final ResourceModel KEY_MODEL_EXTERNAL_ROTATION_DISABLED = KEY_MODEL_BUILDER
        .enableKeyRotation(false)
        .origin(OriginType.EXTERNAL.toString())
        .build();
    private static final ResourceModel KEY_MODEL_EXTERNAL_REDACTED = KEY_MODEL_BUILDER
        .origin(OriginType.EXTERNAL.toString())
        .enableKeyRotation(false)
        .build();

    @Mock
    KmsClient kms;

    @Mock
    private ClientBuilder clientBuilder;

    @Spy
    private Translator translator;

    @Mock
    private KeyApiHelper keyApiHelper;

    @Mock
    private CreatableKeyHandlerHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>>
        keyHandlerHelper;

    @Mock
    private EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;

    private CreateHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;
    private TagHelper<ResourceModel, CallbackContext, CreatableKeyTranslator<ResourceModel>> tagHelper;

    @BeforeEach
    public void setup() {
        tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
        handler = new CreateHandler(clientBuilder, translator, keyApiHelper,
                eventualConsistencyHandlerHelper, keyHandlerHelper, tagHelper);
        proxy = spy(
            new AmazonWebServicesClientProxy(TestConstants.LOGGER, TestConstants.MOCK_CREDENTIALS,
                () -> Duration.ofSeconds(600).toMillis()));
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Mock out our rotation status update
        final EnableKeyRotationResponse enableKeyRotationResponse =
            EnableKeyRotationResponse.builder().build();
        when(
            keyApiHelper
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
            .thenReturn(enableKeyRotationResponse);

        // Mock our create key call, disable key call, and final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
            ProgressEvent.progress(KEY_MODEL_WITH_DEFAULTS_SET, callbackContext);
        when(keyHandlerHelper
            .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                eq(callbackContext),
                eq(TestConstants.TAGS))).thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                eq(KEY_MODEL_WITH_DEFAULTS_SET),
                eq(callbackContext))).thenReturn(inProgressEvent);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(inProgressEvent)))
            .thenReturn(inProgressEvent);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL)
                .desiredResourceTags(TestConstants.TAGS)
                .build();

        // Execute the create handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_REDACTED));

        // Make sure we enabled rotation
        verify(keyApiHelper)
            .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient));

        // Make sure we called our helpers to create the key, disable the key if needed,
        // and to complete the final propagation
        verify(keyHandlerHelper)
            .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                eq(callbackContext), eq(TestConstants.TAGS));
        verify(keyHandlerHelper).disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
            eq(KEY_MODEL_WITH_DEFAULTS_SET), eq(callbackContext));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_AsymmetricRotationEnabled() {
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL_ASYMMETRIC_ROTATION_ENABLED)
                .build();

        assertThatExceptionOfType(CfnInvalidRequestException.class).isThrownBy(() -> handler
            .handleRequest(proxy, request, new CallbackContext(), proxyKmsClient,
                TestConstants.LOGGER))
            .withMessage(
                "Invalid request provided: You cannot set the EnableKeyRotation property to true on asymmetric or external keys.");
    }

    @Test
    public void handleRequest_ExternalRotationEnabled() {
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL_EXTERNAL_ROTATION_ENABLED)
                .build();

        assertThatExceptionOfType(CfnInvalidRequestException.class).isThrownBy(() -> handler
            .handleRequest(proxy, request, new CallbackContext(), proxyKmsClient,
                TestConstants.LOGGER))
            .withMessage(
                "Invalid request provided: You cannot set the EnableKeyRotation property to true on asymmetric or external keys.");
    }

    @Test
    public void handleRequest_ExternalRotationDisabled() {
        // Mock our create key call, disable key call, and final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
                ProgressEvent.progress(KEY_MODEL_EXTERNAL_ROTATION_DISABLED, callbackContext);
        when(keyHandlerHelper
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_EXTERNAL_ROTATION_DISABLED),
                        eq(callbackContext),
                        eq(TestConstants.TAGS))).thenReturn(inProgressEvent);
        when(keyHandlerHelper
                .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                        eq(KEY_MODEL_EXTERNAL_ROTATION_DISABLED),
                        eq(callbackContext))).thenReturn(inProgressEvent);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(inProgressEvent)))
                .thenReturn(inProgressEvent);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder()
                        .desiredResourceState(KEY_MODEL_EXTERNAL_ROTATION_DISABLED)
                        .desiredResourceTags(TestConstants.TAGS)
                        .build();

        // Execute the create handler and make sure it returns the expected results
        assertThat(handler
                .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
                .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_EXTERNAL_REDACTED));

        // Make sure we called our helpers to create the key, disable the key if needed,
        // and to complete the final propagation
        verify(keyHandlerHelper)
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_EXTERNAL_ROTATION_DISABLED),
                        eq(callbackContext), eq(TestConstants.TAGS));
        verify(keyHandlerHelper).disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                eq(KEY_MODEL_EXTERNAL_ROTATION_DISABLED), eq(callbackContext));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
