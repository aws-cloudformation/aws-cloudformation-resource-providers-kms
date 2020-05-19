package software.amazon.kms.alias;

import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AlreadyExistsException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.InvalidAliasNameException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<KmsClient> proxyKmsClient;

    @Mock
    KmsClient kms;

    private CreateHandler handler;
    private ResourceModel model;
    private ResourceHandlerRequest<ResourceModel> request;

    @BeforeEach
    public void setup() {
        handler = new CreateHandler();
        model = ResourceModel.builder()
            .aliasName("alias/sampleAlias")
            .targetKeyId("sampleKeyId")
            .build();
        request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
        kms = mock(KmsClient.class);
        proxy = mock(AmazonWebServicesClientProxy.class);
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @AfterEach
    public void post_execute() {
        verifyNoMoreInteractions(proxy);
        verifyNoMoreInteractions(proxyKmsClient.client());
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.createAliasRequest(model)), any());

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
    public void handleRequest_AlreadyExists() {
        doThrow(AlreadyExistsException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnAlreadyExistsException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));
    }

    @Test
    public void handleRequest_KmsInternal() {
        doThrow(KmsInternalException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));
    }

    @Test
    public void handleRequest_KmsInvalidState() {
        doThrow(KmsInvalidStateException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class,
            () -> handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger));
    }

    @Test
    public void handleRequest_InvalidAliasName() {
        doThrow(InvalidAliasNameException.class)
            .when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnInvalidRequestException.class,
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
