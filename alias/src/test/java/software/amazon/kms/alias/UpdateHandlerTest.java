package software.amazon.kms.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest {
    private static final ResourceModel MODEL = ResourceModel.builder()
        .aliasName("alias/testUpdateAlias")
        .targetKeyId("keyId")
        .build();

    @Mock
    private KmsClient kms;

    @Mock
    private ClientBuilder clientBuilder;

    @Mock
    private AliasApiHelper aliasApiHelper;

    @Mock
    private EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
        eventualConsistencyHandlerHelper;

    private UpdateHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler =
            new UpdateHandler(clientBuilder, aliasApiHelper, eventualConsistencyHandlerHelper);
        proxy = new AmazonWebServicesClientProxy(TestConstants.LOGGER,
            TestConstants.MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Mock out the final propagation delay
        final ProgressEvent<ResourceModel, CallbackContext> expectedProgressEvent =
            ProgressEvent.progress(MODEL, callbackContext);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(expectedProgressEvent)))
            .thenReturn(expectedProgressEvent);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Execute the update handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(MODEL));

        // Make sure we called our helpers to update the alias and complete the final propagation
        verify(aliasApiHelper)
            .updateAlias(eq(Translator.updateAliasRequest(MODEL)), eq(proxyKmsClient));
        verify(eventualConsistencyHandlerHelper)
            .waitForChangesToPropagate(eq(expectedProgressEvent));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(aliasApiHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
