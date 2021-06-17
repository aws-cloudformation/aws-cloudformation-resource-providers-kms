package software.amazon.kms.alias;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateAliasResponse;
import software.amazon.awssdk.services.kms.model.DeleteAliasRequest;
import software.amazon.awssdk.services.kms.model.DeleteAliasResponse;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.UpdateAliasRequest;
import software.amazon.awssdk.services.kms.model.UpdateAliasResponse;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.kms.common.AbstractKmsApiHelper;

/**
 * Helper class for calling KMS alias APIs. The primary function of this class
 * is to wrap KMS service exceptions with the appropriate CloudFormation exception.
 * This is necessary so that CloudFormation can determine whether or not it should
 * retry a failed request.
 */
public class AliasApiHelper extends AbstractKmsApiHelper {
    private static final String CREATE_ALIAS = "CreateAlias";
    private static final String DELETE_ALIAS = "DeleteAlias";
    private static final String LIST_ALIASES = "ListAliases";
    private static final String UPDATE_ALIAS = "UpdateAlias";

    public CreateAliasResponse createAlias(final CreateAliasRequest createAliasRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(CREATE_ALIAS,
            () -> proxyClient.injectCredentialsAndInvokeV2(createAliasRequest,
                proxyClient.client()::createAlias));
    }

    public DeleteAliasResponse deleteAlias(final DeleteAliasRequest deleteAliasRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(DELETE_ALIAS,
            () -> proxyClient.injectCredentialsAndInvokeV2(deleteAliasRequest,
                proxyClient.client()::deleteAlias));
    }

    public ListAliasesResponse listAliases(final ListAliasesRequest listAliasesRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(LIST_ALIASES,
            () -> proxyClient.injectCredentialsAndInvokeV2(listAliasesRequest,
                proxyClient.client()::listAliases));
    }

    public UpdateAliasResponse updateAlias(final UpdateAliasRequest updateAliasRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(UPDATE_ALIAS,
            () -> proxyClient.injectCredentialsAndInvokeV2(updateAliasRequest,
                proxyClient.client()::updateAlias));
    }
}
