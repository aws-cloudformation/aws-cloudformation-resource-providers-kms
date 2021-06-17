package software.amazon.kms.replicakey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.ReplicateKeyRequest;
import software.amazon.awssdk.services.kms.model.ReplicateKeyResponse;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.KeyHandlerHelper;
import software.amazon.kms.common.KeyTranslator;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {
    private static final ResourceModel.ResourceModelBuilder KEY_MODEL_BUILDER =
        ResourceModel.builder()
            .primaryKeyArn("mock-primary-arn")
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .pendingWindowInDays(7)
            .tags(ImmutableSet.of(Tag.builder()
                .key("Key")
                .value("Value")
                .build()));
    private static final ResourceModel KEY_MODEL = KEY_MODEL_BUILDER.build();
    private static final ResourceModel KEY_MODEL_CREATED = KEY_MODEL_BUILDER
        .keyId("mock-key-id")
        .arn("mock-arn")
        .description("")
        .enabled(true)
        .build();
    private static final ResourceModel KEY_MODEL_REDACTED = KEY_MODEL_BUILDER
        .pendingWindowInDays(null)
        .build();

    @Mock
    KmsClient kms;

    @Mock
    KmsClient primaryKms;

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

    private CreateHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private ProxyClient<KmsClient> primaryProxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new CreateHandler(clientBuilder, translator, keyApiHelper,
            eventualConsistencyHandlerHelper, keyHandlerHelper);
        proxy = spy(
            new AmazonWebServicesClientProxy(TestConstants.LOGGER, TestConstants.MOCK_CREDENTIALS,
                () -> Duration.ofSeconds(600).toMillis()));
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        primaryProxyKmsClient = TestUtils.buildMockProxy(proxy, primaryKms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_PartiallyPropagate() {
        // Return our mock primary client when we try to get the primary client
        when(clientBuilder.getClientForArnRegion(eq(KEY_MODEL.getPrimaryKeyArn()))).thenReturn(
            Suppliers.ofInstance(primaryKms));
        when(proxy.newProxy(ArgumentMatchers.<Supplier<KmsClient>>any()))
            .thenReturn(primaryProxyKmsClient);

        // Mock out our replicate key response
        final ReplicateKeyResponse replicateKeyResponse = ReplicateKeyResponse.builder()
            .replicaKeyMetadata(KeyMetadata.builder()
                .arn(KEY_MODEL_CREATED.getArn())
                .keyId(KEY_MODEL_CREATED.getKeyId())
                .build())
            .build();
        when(keyApiHelper.replicateKey(any(ReplicateKeyRequest.class), eq(primaryProxyKmsClient)))
            .thenReturn(replicateKeyResponse);

        // Setup our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL)
                .desiredResourceTags(TestConstants.TAGS)
                .build();

        // Execute the create handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultInProgressHandler(callbackContext,
                EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS,
                KEY_MODEL_CREATED));

        // Make sure we called replicate key with the correct parameters
        verify(keyApiHelper)
            .replicateKey(any(ReplicateKeyRequest.class), eq(primaryProxyKmsClient));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_FullyPropagate() {
        // Return our mock primary client when we try to get the primary client
        when(clientBuilder.getClientForArnRegion(eq(KEY_MODEL.getPrimaryKeyArn()))).thenReturn(
            Suppliers.ofInstance(primaryKms));
        when(proxy.newProxy(ArgumentMatchers.<Supplier<KmsClient>>any()))
            .thenReturn(primaryProxyKmsClient);

        // Mock out our replicate key response
        final ReplicateKeyResponse replicateKeyResponse = ReplicateKeyResponse.builder()
            .replicaKeyMetadata(KeyMetadata.builder()
                .arn(KEY_MODEL_CREATED.getArn())
                .keyId(KEY_MODEL_CREATED.getKeyId())
                .build())
            .build();
        when(keyApiHelper.replicateKey(any(ReplicateKeyRequest.class), eq(primaryProxyKmsClient)))
            .thenReturn(replicateKeyResponse);

        // Mock our describe key responses so that the resource appears to stabilize
        final DescribeKeyResponse creatingResponse = DescribeKeyResponse.builder()
            .keyMetadata(KeyMetadata.builder().keyState(KeyState.CREATING).build())
            .build();
        final DescribeKeyResponse enabledResponse = DescribeKeyResponse.builder()
            .keyMetadata(KeyMetadata.builder().keyState(KeyState.ENABLED).build())
            .build();
        when(keyApiHelper.describeKey(any(DescribeKeyRequest.class), eq(proxyKmsClient)))
            .thenThrow(CfnNotFoundException.class)
            .thenReturn(creatingResponse)
            .thenReturn(enabledResponse);

        // Mock our disable key call & final propagation
        final ProgressEvent<ResourceModel, CallbackContext> inProgressEvent =
            ProgressEvent.progress(KEY_MODEL_CREATED, callbackContext);
        when(keyHandlerHelper
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(), eq(KEY_MODEL_CREATED),
                eq(callbackContext))).thenReturn(inProgressEvent);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(inProgressEvent)))
            .thenReturn(inProgressEvent);

        // Setup our request, in this case the key has already been created, so we will skip
        // the initial propagation
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(KEY_MODEL_CREATED)
                .desiredResourceTags(TestConstants.TAGS)
                .build();

        // Execute the create handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(KEY_MODEL_REDACTED));

        // Make sure we called our helpers to disable the key if needed and complete the
        // final propagation
        verify(keyHandlerHelper)
            .disableKeyIfNecessary(eq(proxy), eq(proxyKmsClient), isNull(), eq(KEY_MODEL_CREATED),
                eq(callbackContext));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(inProgressEvent));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(keyApiHelper);
        verifyZeroInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
