package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Delay;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.CappedExponential;
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
    private static final ResourceModel KEY_MODEL_ROTATION_IN_PERIOD_DAYS = KEY_MODEL_BUILDER
        .rotationPeriodInDays(100)
        .build();

    private static final ResourceModel KEY_MODEL_ROTATION_IN_PERIOD_DAYS_REDACTED = KEY_MODEL_BUILDER
        .rotationPeriodInDays(null)
        .build();
    private static final ResourceModel KEY_MODEL_ASYMMETRIC_ROTATION_ENABLED = KEY_MODEL_BUILDER
        .keySpec(KeySpec.RSA_4096.toString())
        .build();
    private static final ResourceModel KEY_MODEL_EXTERNAL_ROTATION_ENABLED = KEY_MODEL_BUILDER
        .origin(OriginType.EXTERNAL.toString())
        .keySpec(KeySpec.SYMMETRIC_DEFAULT.toString())
        .build();
    private static final ResourceModel KEY_MODEL_EXTERNAL_ROTATION_DISABLED = KEY_MODEL_BUILDER
        .enableKeyRotation(false)
        .origin(OriginType.EXTERNAL.toString())
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

    Delay BACKOFF_STRATEGY =
            CappedExponential.of()
                    .minDelay(Duration.ofSeconds(1))
                    .maxDelay(Duration.ofSeconds(2))
                    .powerBy(1.3)
                    .timeout(Duration.ofSeconds(5))
                    .build();

    @BeforeEach
    public void setup() {
        tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
        handler = new CreateHandler(clientBuilder, translator, keyApiHelper,
                eventualConsistencyHandlerHelper, keyHandlerHelper, tagHelper, BACKOFF_STRATEGY);
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
        when(eventualConsistencyHandlerHelper.setRequestType(eq(inProgressEvent), eq(false)))
                .thenReturn(inProgressEvent);
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
    public void handleRequest_SimpleSuccess_RetryEnableKeyRotation() {

        // Mock out our rotation status update
        final EnableKeyRotationResponse enableKeyRotationResponse =
                EnableKeyRotationResponse.builder().build();
        when(keyApiHelper
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
                .thenThrow(new CfnNotFoundException("AWS::KMS::Key", "keyId"))
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
        when(eventualConsistencyHandlerHelper.setRequestType(eq(inProgressEvent), eq(false)))
                .thenReturn(inProgressEvent);
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
        verify(keyApiHelper, times(2))
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
    public void handleRequest_FailedEventualConsistency_EnableKeyRotation() {

        // Mock out our rotation status update
        final EnableKeyRotationResponse enableKeyRotationResponse =
                EnableKeyRotationResponse.builder().build();
        when(keyApiHelper
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
                .thenThrow(new CfnNotFoundException("AWS::KMS::Key", "keyId"));

        // Mock our create key call, disable key call, and final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
                ProgressEvent.progress(KEY_MODEL_WITH_DEFAULTS_SET, callbackContext);
        when(keyHandlerHelper
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext),
                        eq(TestConstants.TAGS))).thenReturn(inProgressEvent);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder()
                        .desiredResourceState(KEY_MODEL)
                        .desiredResourceTags(TestConstants.TAGS)
                        .build();

        // Execute the create handler and make sure it returns the expected results
        assertThat(handler
                .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
                .isEqualTo(ProgressEvent.failed(KEY_MODEL, callbackContext, HandlerErrorCode.NotStabilized,
                        "Exceeded attempts to wait"));

        // Make sure we enabled rotation
        verify(keyApiHelper, atLeast(1))
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient));

        // Make sure we called our helpers to create the key, disable the key if needed,
        // and to complete the final propagation
        verify(keyHandlerHelper)
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext), eq(TestConstants.TAGS));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_Failed_EnableKeyRotation() {

        // Mock out our rotation status update
        final EnableKeyRotationResponse enableKeyRotationResponse =
                EnableKeyRotationResponse.builder().build();
        when(keyApiHelper
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
                .thenThrow(new CfnInvalidRequestException("AWS::KMS::Key"));

        // Mock our create key call, disable key call, and final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
                ProgressEvent.progress(KEY_MODEL_WITH_DEFAULTS_SET, callbackContext);
        when(keyHandlerHelper
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext),
                        eq(TestConstants.TAGS))).thenReturn(inProgressEvent);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder()
                        .desiredResourceState(KEY_MODEL)
                        .desiredResourceTags(TestConstants.TAGS)
                        .build();

        // Execute the create handler and make sure it returns the expected results
        try {
            handler
                    .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER);
        } catch (Exception e) {
            assertThat(e instanceof CfnInvalidRequestException).isTrue();
        }

        // Make sure we enabled rotation
        verify(keyApiHelper, atLeast(1))
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient));

        // Make sure we called our helpers to create the key, disable the key if needed,
        // and to complete the final propagation
        verify(keyHandlerHelper)
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext), eq(TestConstants.TAGS));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_FailedDisableKey() {

        // Mock out our rotation status update
        final EnableKeyRotationResponse enableKeyRotationResponse =
                EnableKeyRotationResponse.builder().build();
        when(keyApiHelper
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
                .thenThrow(CfnNotFoundException.class)
                .thenReturn(enableKeyRotationResponse);

        // Mock our create key call, disable key call, and final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
                ProgressEvent.progress(KEY_MODEL_WITH_DEFAULTS_SET, callbackContext);

        final ProgressEvent<ResourceModel, CallbackContext> failedProgressEvent =
                ProgressEvent.failed(KEY_MODEL_WITH_DEFAULTS_SET, callbackContext, HandlerErrorCode.NotStabilized, "Key Id not found");
        when(keyHandlerHelper
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext),
                        eq(TestConstants.TAGS))).thenReturn(inProgressEvent);
        when(keyHandlerHelper
                .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                        eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext))).thenReturn(failedProgressEvent);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder()
                        .desiredResourceState(KEY_MODEL)
                        .desiredResourceTags(TestConstants.TAGS)
                        .build();

        // Execute the create handler and make sure it returns the expected results
        final ProgressEvent<ResourceModel, CallbackContext> response = handler
                .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);

        // Make sure we enabled rotation
        verify(keyApiHelper, times(2))
                .enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient));

        // Make sure we called our helpers to create the key, disable the key if needed,
        // and to complete the final propagation
        verify(keyHandlerHelper)
                .createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_WITH_DEFAULTS_SET),
                        eq(callbackContext), eq(TestConstants.TAGS));
        verify(keyHandlerHelper).disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                eq(KEY_MODEL_WITH_DEFAULTS_SET), eq(callbackContext));

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
        when(eventualConsistencyHandlerHelper.setRequestType(eq(inProgressEvent), eq(false)))
                .thenReturn(inProgressEvent);
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
                .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_EXTERNAL_ROTATION_DISABLED));

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

    @Test
    public void handleRequest_RotationInPeriodDays() {
        // Mock out our rotation status update
        final EnableKeyRotationResponse enableKeyRotationResponse =
                EnableKeyRotationResponse.builder().build();
        when(keyApiHelper.enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient)))
                .thenReturn(enableKeyRotationResponse);

        // Mock our create key call, disable key call, and final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
                ProgressEvent.progress(KEY_MODEL_ROTATION_IN_PERIOD_DAYS, callbackContext);
        when(keyHandlerHelper.createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_ROTATION_IN_PERIOD_DAYS),
                eq(callbackContext), eq(TestConstants.TAGS))).thenReturn(inProgressEvent);
        when(keyHandlerHelper.disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                eq(KEY_MODEL_ROTATION_IN_PERIOD_DAYS), eq(callbackContext))).thenReturn(inProgressEvent);
        when(eventualConsistencyHandlerHelper.setRequestType(eq(inProgressEvent), eq(false)))
                .thenReturn(inProgressEvent);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(inProgressEvent)))
                .thenReturn(inProgressEvent);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder()
                        .desiredResourceState(KEY_MODEL_ROTATION_IN_PERIOD_DAYS)
                        .desiredResourceTags(TestConstants.TAGS)
                        .build();

        // Execute the create handler and make sure it returns the expected results
        assertThat(handler
                .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
                .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_ROTATION_IN_PERIOD_DAYS_REDACTED));

        // Make sure we enabled rotation
        verify(keyApiHelper).enableKeyRotation(any(EnableKeyRotationRequest.class), eq(proxyKmsClient));

        // Make sure we called our helpers to create the key, disable the key if needed,
        // and to complete the final propagation
        verify(keyHandlerHelper).createKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_ROTATION_IN_PERIOD_DAYS),
                eq(callbackContext), eq(TestConstants.TAGS));
        verify(keyHandlerHelper).disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(),
                eq(KEY_MODEL_ROTATION_IN_PERIOD_DAYS), eq(callbackContext));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
