package software.amazon.kms.alias;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    private KmsClient kms;

    @Mock
    private AliasHelper aliasHelper;

    private ReadHandler handler;
    private ResourceModel model;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private ResourceHandlerRequest<ResourceModel> request;

    private final static String ALIAS_NAME_BASE = "alias/aliasName1";
    private final static String ALIAS_NAME_REQ1 = "alias/aliasName2";
    private final static String ALIAS_NAME_REQ2 = "alias/aliasName3";
    private final static String KEY_ID = "keyId";
    private final static String NEXT_MARKER = "f251beae-00ff-4393";

    @BeforeEach
    public void setup() {
        handler = new ReadHandler(aliasHelper);
        model = ResourceModel.builder()
            .aliasName(ALIAS_NAME_BASE)
            .targetKeyId(KEY_ID)
            .build();
        request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @Test
    public void handleRequest() {
        List<AliasListEntry> aliases = Lists.newArrayList(AliasListEntry.builder()
                .aliasName(ALIAS_NAME_BASE)
                .targetKeyId(KEY_ID).build());

        final ListAliasesResponse listAliasesResponse = ListAliasesResponse.builder().aliases(aliases).build();
        doReturn(listAliasesResponse).when(aliasHelper).listAliases(any(ListAliasesRequest.class), eq(proxyKmsClient));

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

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
    public void handleRequestNotFound() {
        final List<AliasListEntry> aliasesPage1 = Lists.newArrayList(AliasListEntry.builder()
            .aliasName(ALIAS_NAME_REQ1)
            .targetKeyId(KEY_ID).build());
        final List<AliasListEntry> aliasesPage2 = Lists.newArrayList(AliasListEntry.builder()
            .aliasName(ALIAS_NAME_REQ2)
            .targetKeyId(KEY_ID).build());

        final ListAliasesResponse listAliasesResponsePage1 = ListAliasesResponse.builder()
                .aliases(aliasesPage1)
                .nextMarker(NEXT_MARKER)
                .build();
        final ListAliasesResponse listAliasesResponsePage2 = ListAliasesResponse.builder()
                .aliases(aliasesPage2)
                .build();
        doReturn(listAliasesResponsePage1,
            listAliasesResponsePage2).when(aliasHelper).listAliases(any(ListAliasesRequest.class), eq(proxyKmsClient));

        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        verify(aliasHelper).listAliases(eq(Translator.listAliasesRequest(model, null)), eq(proxyKmsClient));
        verify(aliasHelper).listAliases(eq(Translator.listAliasesRequest(model, NEXT_MARKER)), eq(proxyKmsClient));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
