package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    private UpdateHandler handler;
    private ResourceModel model;
    private ResourceHandlerRequest<ResourceModel> request;


    @BeforeEach
    public void setup() {
        handler = new UpdateHandler();
        model = ResourceModel.builder()
            .aliasName("alias/aliasName1")
            .targetKeyId("keyId")
            .build();
        request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
        kms = mock(KmsClient.class);
        proxy = mock(AmazonWebServicesClientProxy.class);
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.updateAliasRequest(model)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_KmsInternal() {
        doThrow(KmsInternalException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInternalFailureException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));
    }

    @Test
    public void handleRequest_KmsInvalid() {
        doThrow(KmsInvalidStateException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInternalFailureException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));
    }

    @Test
    public void handleRequest_NotFound() {
        doThrow(NotFoundException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnNotFoundException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));
    }
}
