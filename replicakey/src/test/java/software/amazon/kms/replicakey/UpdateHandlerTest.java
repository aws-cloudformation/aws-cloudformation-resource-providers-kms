package software.amazon.kms.replicakey;

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
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.TagResourceResponse;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.KeyHandlerHelper;
import software.amazon.kms.common.KeyTranslator;
import software.amazon.kms.common.TagHelper;
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
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .tags(ImmutableSet.of(Tag.builder()
                .key("Key")
                .value("Value")
                .build()));
    private static final ResourceModel KEY_MODEL_REDACTED = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER
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
    private KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>>
        keyHandlerHelper;

    @Mock
    private EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;

    private UpdateHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;
    private TagHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> tagHelper;

    @BeforeEach
    public void setup() {
        tagHelper = new TagHelper<>(translator, keyApiHelper, keyHandlerHelper);
        handler = new UpdateHandler(clientBuilder, translator, keyApiHelper,
                eventualConsistencyHandlerHelper, keyHandlerHelper, tagHelper);
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
        final UntagResourceResponse untagResourceResponse = UntagResourceResponse.builder().build();
        final TagResourceResponse tagResourceResponse = TagResourceResponse.builder().build();
        when(keyHandlerHelper
            .describeKey(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false)))
            .thenReturn(inProgressEvent);
        when(keyHandlerHelper
            .enableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL_PREVIOUS),
                eq(KEY_MODEL), eq(callbackContext), eq(false)))
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
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext),
                eq(false)))
            .thenReturn(inProgressEvent);
        when(keyApiHelper.untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient)))
            .thenReturn(untagResourceResponse);
        when(keyApiHelper.tagResource(any(TagResourceRequest.class), eq(proxyKmsClient)))
            .thenReturn(tagResourceResponse);
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
                eq(KEY_MODEL), eq(callbackContext), eq(false));
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
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(KEY_MODEL), eq(callbackContext), eq(false));
        verify(keyApiHelper)
            .tagResource(any(TagResourceRequest.class), eq(proxyKmsClient));
        verify(keyApiHelper)
            .untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
