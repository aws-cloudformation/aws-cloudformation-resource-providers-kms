package software.amazon.kms.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


import java.time.Duration;
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
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {
    private static final ResourceModel MODEL = ResourceModel.builder()
        .aliasName("alias/testReadAlias")
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

    private ReadHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new ReadHandler(clientBuilder, aliasApiHelper, eventualConsistencyHandlerHelper);
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
            .aliases(com.google.common.collect.Lists.newArrayList(AliasListEntry.builder()
                .aliasName(MODEL.getAliasName())
                .targetKeyId(MODEL.getTargetKeyId())
                .build()))
            .build();
        doReturn(listAliasesResponse).when(aliasApiHelper)
            .listAliases(eq(expectedListAliasesRequest), eq(proxyKmsClient));

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Execute the read handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(MODEL));

        // Make sure we called our helper to list the aliases
        verify(aliasApiHelper).listAliases(eq(expectedListAliasesRequest), eq(proxyKmsClient));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(aliasApiHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_NotFound() {
        // Mock out the list aliases responses
        final ListAliasesRequest expectedListAliasesRequestPage1 =
            Translator.listAliasesRequest(MODEL, null);
        final ListAliasesRequest expectedListAliasesRequestPage2 =
            Translator.listAliasesRequest(MODEL, TestConstants.NEXT_MARKER);
        final ListAliasesResponse listAliasesResponsePage1 = ListAliasesResponse.builder()
            .aliases(com.google.common.collect.Lists.newArrayList(AliasListEntry.builder()
                .aliasName(MODEL.getAliasName() + "Page1")
                .targetKeyId(MODEL.getTargetKeyId())
                .build()))
            .nextMarker(TestConstants.NEXT_MARKER)
            .build();
        final ListAliasesResponse listAliasesResponsePage2 = ListAliasesResponse.builder()
            .aliases(com.google.common.collect.Lists.newArrayList(AliasListEntry.builder()
                .aliasName(MODEL.getAliasName() + "Page2")
                .targetKeyId(MODEL.getTargetKeyId())
                .build()))
            .build();
        doReturn(listAliasesResponsePage1).when(aliasApiHelper)
            .listAliases(eq(expectedListAliasesRequestPage1), eq(proxyKmsClient));
        doReturn(listAliasesResponsePage2).when(aliasApiHelper)
            .listAliases(eq(expectedListAliasesRequestPage2), eq(proxyKmsClient));

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Execute the read handler and make sure it returns the expected results
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.<ResourceModel, CallbackContext>builder()
                .status(OperationStatus.FAILED)
                .errorCode(HandlerErrorCode.NotFound)
                .build());

        // Make sure we called our helper to list the aliases
        verify(aliasApiHelper)
            .listAliases(eq(Translator.listAliasesRequest(MODEL, null)), eq(proxyKmsClient));
        verify(aliasApiHelper)
            .listAliases(eq(Translator.listAliasesRequest(MODEL, TestConstants.NEXT_MARKER)),
                eq(proxyKmsClient));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(aliasApiHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }
}
