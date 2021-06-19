package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.MultiRegionConfiguration;
import software.amazon.awssdk.services.kms.model.MultiRegionKeyType;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
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
public class ListHandlerTest {
    private static final ResourceModel MODEL = ResourceModel.builder().build();
    private static final String NEXT_TOKEN = "4b90a7e4-b790-456b";
    private static final KeyMetadata PRIMARY_KEY_METADATA = KeyMetadata.builder()
        .multiRegion(true)
        .multiRegionConfiguration(MultiRegionConfiguration.builder()
            .multiRegionKeyType(MultiRegionKeyType.PRIMARY)
            .build())
        .build();
    private static final KeyMetadata REPLICA_KEY_METADATA = KeyMetadata.builder()
        .multiRegion(true)
        .multiRegionConfiguration(MultiRegionConfiguration.builder()
            .multiRegionKeyType(MultiRegionKeyType.REPLICA)
            .build())
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

    @Captor
    private ArgumentCaptor<Function<KeyMetadata, Boolean>> filterCaptor;

    private ListHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new ListHandler(clientBuilder, translator, keyApiHelper,
            eventualConsistencyHandlerHelper, keyHandlerHelper);
        proxy =
            new AmazonWebServicesClientProxy(TestConstants.LOGGER, TestConstants.MOCK_CREDENTIALS,
                () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Set up our expected result and mock out the delegation to our helper
        final ProgressEvent<ResourceModel, CallbackContext> expectedProgressEvent =
            ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModels(ImmutableList.of(MODEL))
                .nextToken(null)
                .status(OperationStatus.SUCCESS)
                .build();
        when(keyHandlerHelper.listKeysAndFilterByMetadata(eq(proxyKmsClient), eq(NEXT_TOKEN),
            any())).thenReturn(expectedProgressEvent);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL)
                .nextToken(NEXT_TOKEN).build();

        // Execute the list handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(expectedProgressEvent);

        // Capture our filter so we can test it properly
        verify(keyHandlerHelper).listKeysAndFilterByMetadata(eq(proxyKmsClient), eq(NEXT_TOKEN),
            filterCaptor.capture());

        // Make sure we exclude replica keys, and include everything else
        assertThat(filterCaptor.getValue().apply(PRIMARY_KEY_METADATA)).isTrue();
        assertThat(filterCaptor.getValue().apply(REPLICA_KEY_METADATA)).isFalse();

        // We shouldn't call anything else
        verifyZeroInteractions(keyApiHelper);
        verifyNoMoreInteractions(keyHandlerHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
