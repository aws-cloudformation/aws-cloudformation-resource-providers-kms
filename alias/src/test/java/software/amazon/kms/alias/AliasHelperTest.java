package software.amazon.kms.alias;

import static junit.framework.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static software.amazon.kms.alias.AliasHelper.THROTTLING_ERROR_CODE;


import com.amazonaws.AmazonServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AlreadyExistsException;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateAliasResponse;
import software.amazon.awssdk.services.kms.model.DeleteAliasRequest;
import software.amazon.awssdk.services.kms.model.DeleteAliasResponse;
import software.amazon.awssdk.services.kms.model.DependencyTimeoutException;
import software.amazon.awssdk.services.kms.model.InvalidAliasNameException;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.InvalidMarkerException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.LimitExceededException;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.UpdateAliasRequest;
import software.amazon.awssdk.services.kms.model.UpdateAliasResponse;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

@ExtendWith(MockitoExtension.class)
public class AliasHelperTest extends AbstractTestBase {

    @Mock
    private KmsClient kms;

    @Mock
    private AmazonWebServicesClientProxy proxy;

    private AliasHelper aliasHelper;
    private ProxyClient<KmsClient> proxyKmsClient;

    @BeforeEach
    public void setup() {
        aliasHelper = new AliasHelper();
        proxyKmsClient = MOCK_PROXY(proxy, kms);
    }

    @Test
    public void testCreateAlias() {
        final CreateAliasRequest createAliasRequest = CreateAliasRequest.builder().build();
        final CreateAliasResponse createAliasResponse = CreateAliasResponse.builder().build();

        doReturn(createAliasResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(createAliasRequest), any());

        assertEquals(createAliasResponse,
            aliasHelper.createAlias(createAliasRequest, proxyKmsClient));
    }

    @Test
    public void testDeleteAlias() {
        final DeleteAliasRequest deleteAliasRequest = DeleteAliasRequest.builder().build();
        final DeleteAliasResponse deleteAliasResponse = DeleteAliasResponse.builder().build();

        doReturn(deleteAliasResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(deleteAliasRequest), any());

        assertEquals(deleteAliasResponse,
            aliasHelper.deleteAlias(deleteAliasRequest, proxyKmsClient));
    }

    @Test
    public void testListAliases() {
        final ListAliasesRequest listAliasesRequest = ListAliasesRequest.builder().build();
        final ListAliasesResponse listAliasesResponse = ListAliasesResponse.builder().build();

        doReturn(listAliasesResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(listAliasesRequest), any());

        assertEquals(listAliasesResponse,
            aliasHelper.listAliases(listAliasesRequest, proxyKmsClient));
    }

    @Test
    public void testUpdateAlias() {
        final UpdateAliasRequest updateAliasRequest = UpdateAliasRequest.builder().build();
        final UpdateAliasResponse updateAliasResponse = UpdateAliasResponse.builder().build();

        doReturn(updateAliasResponse).when(proxy)
            .injectCredentialsAndInvokeV2(same(updateAliasRequest), any());

        assertEquals(updateAliasResponse,
            aliasHelper.updateAlias(updateAliasRequest, proxyKmsClient));
    }

    @Test
    public void testAlreadyExists() {
        doThrow(AlreadyExistsException.class).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnAlreadyExistsException.class);
    }

    @Test
    public void testInternalFailure() {
        doThrow(InvalidMarkerException.class).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnInternalFailureException.class);
    }

    @Test
    public void testLimitExceeded() {
        doThrow(LimitExceededException.class).when(proxy)
            .injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnServiceLimitExceededException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {KmsInvalidStateException.class, InvalidArnException.class,
        InvalidAliasNameException.class})
    public void testInvalidRequest(final Class<? extends Throwable> kmsException) {
        doThrow(kmsException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnInvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {KmsInternalException.class, DependencyTimeoutException.class})
    public void testServiceInternalError(final Class<? extends Throwable> kmsException) {
        doThrow(kmsException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnServiceInternalErrorException.class);
    }

    @Test
    public void testNotFound() {
        doThrow(NotFoundException.class).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnNotFoundException.class);
    }

    @Test
    public void testThrottling() {
        final AmazonServiceException throttlingException = new AmazonServiceException("");
        throttlingException.setErrorCode(THROTTLING_ERROR_CODE);
        doThrow(throttlingException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnThrottlingException.class);
    }

    @Test
    public void testGeneralServiceException() {
        final AmazonServiceException throttlingException = new AmazonServiceException("");
        doThrow(throttlingException).when(proxy).injectCredentialsAndInvokeV2(any(), any());

        assertAllRequestsThrow(CfnGeneralServiceException.class);
    }

    private void assertAllRequestsThrow(final Class<? extends Throwable> cfnException) {
        final CreateAliasRequest createAliasRequest = CreateAliasRequest.builder().build();
        assertThrows(cfnException,
            () -> aliasHelper.createAlias(createAliasRequest, proxyKmsClient));

        final DeleteAliasRequest deleteAliasRequest = DeleteAliasRequest.builder().build();
        assertThrows(cfnException,
            () -> aliasHelper.deleteAlias(deleteAliasRequest, proxyKmsClient));

        final ListAliasesRequest listAliasesRequest = ListAliasesRequest.builder().build();
        assertThrows(cfnException,
            () -> aliasHelper.listAliases(listAliasesRequest, proxyKmsClient));

        final UpdateAliasRequest updateAliasRequest = UpdateAliasRequest.builder().build();
        assertThrows(cfnException,
            () -> aliasHelper.updateAlias(updateAliasRequest, proxyKmsClient));
    }
}
