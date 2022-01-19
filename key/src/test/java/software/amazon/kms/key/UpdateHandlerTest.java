package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
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
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.CreatableKeyHandlerHelper;
import software.amazon.kms.common.CreatableKeyTranslator;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest {
    private static final ResourceModel.ResourceModelBuilder KEY_MODEL_BUILDER =
        ResourceModel.builder()
            .keyId("mock-key-id")
            .arn("mock-arn")
            .description("mock-description")
            .enabled(true)
            .enableKeyRotation(false)
            .multiRegion(false)
            .keyUsage(KeyUsageType.ENCRYPT_DECRYPT.toString())
            .keySpec(KeySpec.SYMMETRIC_DEFAULT.toString())
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .tags(ImmutableSet.of(Tag.builder()
                .key("Key")
                .value("Value")
                .build()));
    private static final ResourceModel KEY_MODEL_REDACTED = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER
        .enableKeyRotation(null)
        .pendingWindowInDays(7)
        .build();
    private static final ResourceModel KEY_MODEL_PREVIOUS = KEY_MODEL_BUILDER
        .enabled(false)
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

    private UpdateHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new UpdateHandler(clientBuilder, translator, keyApiHelper,
            eventualConsistencyHandlerHelper, keyHandlerHelper);
        proxy =
            new AmazonWebServicesClientProxy(TestConstants.LOGGER, TestConstants.MOCK_CREDENTIALS,
                () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Mock out delegation to our helpers and make them return an IN_PROGRESS event
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
            ProgressEvent.progress(KEY_MODEL, callbackContext);
        when(keyHandlerHelper
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(true)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyDescription(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS), eq(KEY_MODEL),
                eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(TestConstants.TAGS),
                eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(inProgressEvent)))
            .thenReturn(inProgressEvent);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(KEY_MODEL_PREVIOUS)
                .desiredResourceState(KEY_MODEL)
                .desiredResourceTags(TestConstants.TAGS)
                .previousResourceTags(TestConstants.PREVIOUS_TAGS)
                .build();

        // Execute the update handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_REDACTED));

        // Make sure we called our helpers with the correct parameters and did the final propagation
        verify(keyHandlerHelper)
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false));
        verify(keyHandlerHelper)
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(true));
        verify(keyHandlerHelper)
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyDescription(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS), eq(KEY_MODEL),
                eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(TestConstants.TAGS),
                eq(callbackContext));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_TagUpdateAccessDenied() {
        // Mock out delegation to our helpers and make them return an IN_PROGRESS event
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
            ProgressEvent.progress(KEY_MODEL, callbackContext);
        when(keyHandlerHelper
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(true)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyDescription(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS), eq(KEY_MODEL),
                eq(callbackContext)))
            .thenReturn(inProgressEvent);
        // Throw CfnAccessDeniedException to mock no kms:TagResource permission
        when(keyHandlerHelper
            .updateKeyTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(TestConstants.TAGS),
                eq(callbackContext)))
            .thenThrow(CfnAccessDeniedException.class);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(KEY_MODEL_PREVIOUS)
                .desiredResourceState(KEY_MODEL)
                .desiredResourceTags(TestConstants.TAGS)
                .previousResourceTags(TestConstants.PREVIOUS_TAGS)
                .build();

        assertThatExceptionOfType(CfnAccessDeniedException.class).isThrownBy(() ->
                handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER));

        // Make sure we called our helpers with the correct parameters and the propagation fails at updateKeyTags
        verify(keyHandlerHelper)
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false));
        verify(keyHandlerHelper)
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(true));
        verify(keyHandlerHelper)
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyDescription(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS), eq(KEY_MODEL),
                eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(TestConstants.TAGS),
                eq(callbackContext));
        verify(eventualConsistencyHandlerHelper, times(0)).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }


    @Test
    public void handleRequest_SoftFailSucess() {
        // Mock out delegation to our helpers and make them return an IN_PROGRESS event
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
            ProgressEvent.progress(KEY_MODEL, callbackContext);
        when(keyHandlerHelper
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(true)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyDescription(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .updateKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS), eq(KEY_MODEL),
                eq(callbackContext)))
            .thenReturn(inProgressEvent);
        // Throw CfnAccessDeniedException to mock no kms:TagResource permission
        when(keyHandlerHelper
            .updateKeyTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(TestConstants.TAGS),
                eq(callbackContext)))
            .thenThrow(CfnAccessDeniedException.class);
         when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(inProgressEvent)))
             .thenReturn(inProgressEvent);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(KEY_MODEL_PREVIOUS)
                .desiredResourceState(KEY_MODEL)
                .desiredResourceTags(TestConstants.TAGS)
                .previousResourceTags(TestConstants.TAGS)
                .build();

        // Execute the update handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_REDACTED));

        // Make sure we called our helpers with the correct parameters and did the final propagation
        verify(keyHandlerHelper)
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false));
        verify(keyHandlerHelper)
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(true));
        verify(keyHandlerHelper)
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyDescription(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS), eq(KEY_MODEL),
                eq(callbackContext));
        verify(keyHandlerHelper)
            .updateKeyTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(TestConstants.TAGS),
                eq(callbackContext));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}