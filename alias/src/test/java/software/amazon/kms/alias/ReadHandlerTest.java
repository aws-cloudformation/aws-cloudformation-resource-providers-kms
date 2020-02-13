package software.amazon.kms.alias;

import org.assertj.core.util.Lists;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private ReadHandler handler;
    private ResourceModel model;
    private ResourceHandlerRequest<ResourceModel> request;

    private final static String ALIAS_NAME_BASE = "alias/aliasName1";
    private final static String ALIAS_NAME_REQ1 = "alias/aliasName2";
    private final static String ALIAS_NAME_REQ2 = "alias/aliasName3";
    private final static String KEY_ID = "keyId";
    private final static String NEXT_MARKER = "f251beae-00ff-4393";

    @BeforeEach
    public void setup() {
        handler = new ReadHandler();
        model = ResourceModel.builder()
                .aliasName(ALIAS_NAME_BASE)
                .targetKeyId(KEY_ID)
                .build();
        request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        List<AliasListEntry> aliases = Lists.newArrayList(AliasListEntry.builder()
                .aliasName(ALIAS_NAME_BASE)
                .targetKeyId(KEY_ID).build());

        final ListAliasesResponse listAliasesResponse = ListAliasesResponse.builder().aliases(aliases).build();
        doReturn(listAliasesResponse).when(proxy).injectCredentialsAndInvokeV2(any(ListAliasesRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

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
    public void handleRequest_SimpleFail() {
        List<AliasListEntry> aliasesPage1 = Lists.newArrayList(AliasListEntry.builder()
                .aliasName(ALIAS_NAME_REQ1)
                .targetKeyId(KEY_ID).build());
        List<AliasListEntry> aliasesPage2 = Lists.newArrayList(AliasListEntry.builder()
                .aliasName(ALIAS_NAME_REQ2)
                .targetKeyId(KEY_ID).build());

        final ListAliasesResponse listAliasesResponsePage1 = ListAliasesResponse.builder().aliases(aliasesPage1).nextMarker(NEXT_MARKER).build();
        final ListAliasesResponse listAliasesResponsePage2 = ListAliasesResponse.builder().aliases(aliasesPage2).build();
        doReturn(listAliasesResponsePage1,
                listAliasesResponsePage2).when(proxy).injectCredentialsAndInvokeV2(any(ListAliasesRequest.class), any());

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listAliasesRequest(KEY_ID, null)), any());
        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listAliasesRequest(KEY_ID, NEXT_MARKER)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_NotFound() {
        doThrow(NotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }
}
