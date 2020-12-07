package software.amazon.kms.alias;

import com.google.common.collect.Lists;

import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    @Mock
    private KmsClient kms;

    @Mock
    private AliasHelper aliasHelper;

    private ListHandler handler;
    private ResourceModel model;
    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<KmsClient> proxyKmsClient;
    private ResourceHandlerRequest<ResourceModel> request;

    private final static String ALIAS_NAME = "alias/aliasName1";
    private final static String KEY_ID = "keyId";

    @BeforeEach
    public void setup() {
        handler = new ListHandler(aliasHelper);
        model = ResourceModel.builder()
            .aliasName(ALIAS_NAME)
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
        final ListAliasesResponse listAliasesResponse = ListAliasesResponse.builder()
            .aliases(Lists.newArrayList(AliasListEntry.builder().aliasName(ALIAS_NAME).targetKeyId(KEY_ID).build()))
            .build();

        doReturn(listAliasesResponse).when(aliasHelper).listAliases(eq(Translator.listAliasesRequest(model, null)),
                eq(proxyKmsClient));

        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, new CallbackContext(), proxyKmsClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).containsExactly(model);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
