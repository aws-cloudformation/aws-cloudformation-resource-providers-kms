package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusRequest;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnUnauthorizedTaggingOperationException;
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
public class ReadHandlerTest {
    private static final ResourceModel.ResourceModelBuilder KEY_MODEL_BUILDER =
        ResourceModel.builder()
            .keyId("mock-key-id")
            .arn("mock-arn")
            .description("mock-description")
            .enabled(true)
            .enableKeyRotation(true)
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .tags(ImmutableSet.of(Tag.builder()
                .key("Key")
                .value("Value")
                .build()));
    private static final ResourceModel KEY_MODEL_REDACTED = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER
        .pendingWindowInDays(7)
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

    private ReadHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new ReadHandler(clientBuilder, translator, keyApiHelper,
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
        when(keyHandlerHelper.describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
            eq(callbackContext), eq(true))).thenReturn(inProgressEvent);
        when(keyHandlerHelper.getKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
            eq(callbackContext))).thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(true))).thenReturn(inProgressEvent);

        // Mock out our rotation status check
        final GetKeyRotationStatusResponse getKeyRotationStatusResponse =
            GetKeyRotationStatusResponse.builder().keyRotationEnabled(true).build();
        when(
            keyApiHelper.getKeyRotationStatus(any(GetKeyRotationStatusRequest.class),
                eq(proxyKmsClient)))
            .thenReturn(getKeyRotationStatusResponse);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(KEY_MODEL).build();

        // Execute our read handler and make sure the expected results are returned
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_REDACTED));

        // Make sure we called get key rotation status
        verify(keyApiHelper).getKeyRotationStatus(any(GetKeyRotationStatusRequest.class),
            eq(proxyKmsClient));

        // Make sure the expected helpers were called with the correct parameters
        verify(keyHandlerHelper)
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(true));
        verify(keyHandlerHelper)
            .getKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
                eq(callbackContext), eq(true));

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_SoftFailAccessDenied() {
        // Mock out delegation to our helpers and make them return an IN_PROGRESS event
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
            ProgressEvent.progress(KEY_MODEL, callbackContext);
        when(keyHandlerHelper.describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
            eq(callbackContext), eq(true))).thenReturn(inProgressEvent);

        // The rest of our helpers & the rotation status check should soft fail CfnAccessDeniedException
        when(keyHandlerHelper.getKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
            eq(callbackContext))).thenThrow(CfnAccessDeniedException.class);
        when(keyHandlerHelper.retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
            eq(callbackContext), eq(true))).thenThrow(CfnUnauthorizedTaggingOperationException.class);
        when(keyApiHelper.getKeyRotationStatus(any(GetKeyRotationStatusRequest.class),
            eq(proxyKmsClient))).thenThrow(CfnAccessDeniedException.class);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(KEY_MODEL).build();

        // Execute our read handler, our soft failing should ignore the CfnAccessDeniedExceptions
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_REDACTED));

        // Make sure we called get key rotation status
        verify(keyApiHelper).getKeyRotationStatus(any(GetKeyRotationStatusRequest.class),
            eq(proxyKmsClient));

        // Make sure the expected helpers were called with the correct parameters
        verify(keyHandlerHelper)
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(true));
        verify(keyHandlerHelper)
            .getKeyPolicy(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext));
        verify(keyHandlerHelper)
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL),
                eq(callbackContext), eq(true));

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
