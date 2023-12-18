package software.amazon.kms.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.TestConstants;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {
    private static final ResourceModel MODEL = ResourceModel.builder()
        .aliasName("alias/testCreateAlias")
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

    private CreateHandler handler;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private CallbackContext callbackContext;

    @BeforeEach
    public void setup() {
        handler = new CreateHandler(clientBuilder, aliasApiHelper, eventualConsistencyHandlerHelper);
        proxy = new AmazonWebServicesClientProxy(TestConstants.LOGGER,
            TestConstants.MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        callbackContext = new CallbackContext();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // Mock out the final propagation delay
        final ProgressEvent<ResourceModel, CallbackContext> expectedEcProgressEvent =
            ProgressEvent.progress(MODEL, callbackContext);
        when(eventualConsistencyHandlerHelper.waitForChangesToPropagate(eq(expectedEcProgressEvent)))
            .thenReturn(expectedEcProgressEvent);

        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
            ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Return not found from DescribeKey to indicate alias doesn't already exist
        doThrow(new CfnNotFoundException("", "")).when(aliasApiHelper).describeKey(
                eq(DescribeKeyRequest.builder().keyId(MODEL.getAliasName()).build()), any());

        // Expect first invocation to retry after pre-create check
        assertThat(handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
                .isEqualTo(ProgressEvent.defaultInProgressHandler(callbackContext, 1, MODEL));
        assertThat(callbackContext.isPreCreateCheckDone()).isTrue();
        verify(aliasApiHelper).describeKey(
                eq(DescribeKeyRequest.builder().keyId(MODEL.getAliasName()).build()), eq(proxyKmsClient));

        // Re-run handler and succeed (since pre-create check is already done)
        assertThat(handler
            .handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER))
            .isEqualTo(ProgressEvent.defaultSuccessHandler(MODEL));

        // Make sure we called our helpers to create the alias and complete the final propagation
        verify(aliasApiHelper)
            .createAlias(eq(Translator.createAliasRequest(MODEL)), eq(proxyKmsClient));
        verify(eventualConsistencyHandlerHelper).waitForChangesToPropagate(eq(expectedEcProgressEvent));

        // We shouldn't make any other calls
        verifyNoMoreInteractions(aliasApiHelper);
        verifyNoMoreInteractions(eventualConsistencyHandlerHelper);
    }

    @Test
    public void handleRequest_AliasAlreadyExists() {
        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        Assertions.assertThrows(CfnAlreadyExistsException.class, () ->
                handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER));
        assertThat(callbackContext.isPreCreateCheckDone()).isFalse();

        verify(aliasApiHelper).describeKey(
                eq(DescribeKeyRequest.builder().keyId(MODEL.getAliasName()).build()), eq(proxyKmsClient));
        verifyNoMoreInteractions(aliasApiHelper);
    }

    @Test
    public void handleRequest_AliasAlreadyExistsWithoutDescribeKeyAccess() {
        // Set up our request
        final ResourceHandlerRequest<ResourceModel> request =
                ResourceHandlerRequest.<ResourceModel>builder().desiredResourceState(MODEL).build();

        // Throw access denied when trying to lookup the alias
        doThrow(new CfnAccessDeniedException("")).when(aliasApiHelper).describeKey(
                eq(DescribeKeyRequest.builder().keyId(MODEL.getAliasName()).build()), any());

        Assertions.assertThrows(CfnAlreadyExistsException.class, () ->
                handler.handleRequest(proxy, request, callbackContext, proxyKmsClient, TestConstants.LOGGER));
        assertThat(callbackContext.isPreCreateCheckDone()).isFalse();

        verify(aliasApiHelper).describeKey(
                eq(DescribeKeyRequest.builder().keyId(MODEL.getAliasName()).build()), eq(proxyKmsClient));
        verifyNoMoreInteractions(aliasApiHelper);
    }
}
