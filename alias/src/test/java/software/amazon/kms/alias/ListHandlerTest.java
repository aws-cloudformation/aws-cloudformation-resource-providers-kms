package software.amazon.kms.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest {
    private static final ResourceModel MODEL = ResourceModel.builder()
        .aliasName("alias/testListAlias")
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

    private ListHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new ListHandler(clientBuilder, aliasApiHelper, eventualConsistencyHandlerHelper);
        proxy = new AmazonWebServicesClientProxy(TestConstants.LOGGER,
            TestConstants.MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Mock out the list aliases response
        final ListAliasesRequest expectedListAliasesRequest =
            Translator.listAliasesRequest(MODEL, null);
        final ListAliasesResponse listAliasesResponse = ListAliasesResponse.builder()
            .aliases(Lists.newArrayList(AliasListEntry.builder()
                .aliasName(MODEL.getAliasName())
                .targetKeyId(MODEL.getTargetKeyId())
                .build()))
            .nextMarker(TestConstants.NEXT_MARKER)
            .build();
        doReturn(listAliasesResponse).when(aliasApiHelper)
            .listAliases(eq(expectedListAliasesRequest), eq(proxyKmsClient));

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Execute the list handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.builder()
                .status(OperationStatus.SUCCESS)
                .resourceModels(Collections.singletonList(MODEL))
                .nextToken(TestConstants.NEXT_MARKER)
                .build());

        // Make sure we called our helper to list the aliases
        verify(aliasApiHelper).listAliases(eq(expectedListAliasesRequest), eq(proxyKmsClient));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(aliasApiHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
