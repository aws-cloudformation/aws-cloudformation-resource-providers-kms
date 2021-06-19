package software.amazon.kms.alias;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateAliasResponse;
import software.amazon.awssdk.services.kms.model.DeleteAliasRequest;
import software.amazon.awssdk.services.kms.model.DeleteAliasResponse;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.UpdateAliasRequest;
import software.amazon.awssdk.services.kms.model.UpdateAliasResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.kms.common.TestUtils;

@ExtendWith(MockitoExtension.class)
public class AliasApiHelperTest {

    @Mock
    private KmsClient kms;

    @Mock
    private AmazonWebServicesClientProxy proxy;

    private AliasApiHelper aliasApiHelper;
    private ProxyClient<KmsClient> proxyKmsClient;

    @BeforeEach
    public void setup() {
        aliasApiHelper = new AliasApiHelper();
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
    }

    @Test
    public void testCreateAlias() {
        final CreateAliasRequest createAliasRequest = CreateAliasRequest.builder().build();
        final CreateAliasResponse createAliasResponse = CreateAliasResponse.builder().build();

        doReturn(createAliasResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(createAliasRequest), any());

        assertEquals(createAliasResponse,
            aliasApiHelper.createAlias(createAliasRequest, proxyKmsClient));
    }

    @Test
    public void testDeleteAlias() {
        final DeleteAliasRequest deleteAliasRequest = DeleteAliasRequest.builder().build();
        final DeleteAliasResponse deleteAliasResponse = DeleteAliasResponse.builder().build();

        doReturn(deleteAliasResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(deleteAliasRequest), any());

        assertEquals(deleteAliasResponse,
            aliasApiHelper.deleteAlias(deleteAliasRequest, proxyKmsClient));
    }

    @Test
    public void testListAliases() {
        final ListAliasesRequest listAliasesRequest = ListAliasesRequest.builder().build();
        final ListAliasesResponse listAliasesResponse = ListAliasesResponse.builder().build();

        doReturn(listAliasesResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(listAliasesRequest), any());

        assertEquals(listAliasesResponse,
            aliasApiHelper.listAliases(listAliasesRequest, proxyKmsClient));
    }

    @Test
    public void testUpdateAlias() {
        final UpdateAliasRequest updateAliasRequest = UpdateAliasRequest.builder().build();
        final UpdateAliasResponse updateAliasResponse = UpdateAliasResponse.builder().build();

        doReturn(updateAliasResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(updateAliasRequest), any());

        assertEquals(updateAliasResponse,
            aliasApiHelper.updateAlias(updateAliasRequest, proxyKmsClient));
    }
}
