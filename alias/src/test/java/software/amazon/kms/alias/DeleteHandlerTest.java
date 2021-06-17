package software.amazon.kms.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
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
public class DeleteHandlerTest {
    private static final ResourceModel MODEL = ResourceModel.builder()
        .aliasName("alias/testDeleteAlias")
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

    private DeleteHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler =
            new DeleteHandler(clientBuilder, aliasApiHelper, eventualConsistencyHandlerHelper);
        proxy = new AmazonWebServicesClientProxy(TestConstants.LOGGER,
            TestConstants.MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Set up our expected in progress event and mock out the delegation to our EC helper
        final ProgressEvent<ResourceModel, CallbackContext> expectedInProgressEvent =
            ProgressEvent.progress(MODEL, callbackContext);
        when(
            eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(expectedInProgressEvent)))
            .thenReturn(expectedInProgressEvent);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Execute the delete handler and make sure it returns the expected result
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(null));

        // Make sure we called on our alias helper to delete the alias with the correct parameters
        verify(aliasApiHelper)
            .deleteAlias(eq(Translator.deleteAliasRequest(MODEL)), eq(proxyKmsClient));

        // Make sure we called on our EC helper to perform the final propagation delay
        verify(eventualConsistencyHandlerHelper)
            .waitForChangesToPropagate(eq(expectedInProgressEvent));

        // We shouldn't call anything else
        verifyZeroInteractions(aliasApiHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
