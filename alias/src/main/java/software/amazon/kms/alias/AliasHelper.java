package software.amazon.kms.alias;

import com.amazonaws.AmazonServiceException;
import java.util.function.Supplier;
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
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.LimitExceededException;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.UpdateAliasRequest;
import software.amazon.awssdk.services.kms.model.UpdateAliasResponse;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.ProxyClient;

/**
 * Helper class for calling KMS alias APIs. The primary function of this class
 * is to wrap KMS service exceptions with the appropriate CloudFormation exception.
 * This is necessary so that CloudFormation can determine whether or not it should
 * retry a failed request.
 */
public class AliasHelper {
    static final String THROTTLING_ERROR_CODE = "ThrottlingException";
    static final String ACCESS_DENIED_ERROR_CODE = "AccessDeniedException";
    static final String VALIDATION_ERROR_CODE = "ValidationException";

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

    public ListAliasesResponse listAliases(final ListAliasesRequest deleteAliasRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(LIST_ALIASES,
            () -> proxyClient.injectCredentialsAndInvokeV2(deleteAliasRequest,
                proxyClient.client()::listAliases));
    }

    public UpdateAliasResponse updateAlias(final UpdateAliasRequest updateAliasRequest,
                                           final ProxyClient<KmsClient> proxyClient) {
        return wrapKmsExceptions(UPDATE_ALIAS,
            () -> proxyClient.injectCredentialsAndInvokeV2(updateAliasRequest,
                proxyClient.client()::updateAlias));
    }

    private <T> T wrapKmsExceptions(final String operation, final Supplier<T> serviceCall) {
        try {
            return serviceCall.get();
        } catch (final AlreadyExistsException e) {
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (final InvalidAliasNameException | KmsInvalidStateException | InvalidArnException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final LimitExceededException e) {
            throw new CfnServiceLimitExceededException(ResourceModel.TYPE_NAME, e.getMessage());
        } catch (final InvalidMarkerException e) {
            // We should never make a call with an invalid marker, if we did, there is an issue
            throw new CfnInternalFailureException(e);
        } catch (final KmsInternalException | DependencyTimeoutException e) {
            throw new CfnServiceInternalErrorException(e);
        } catch (final NotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final KmsException e) {
            if (ACCESS_DENIED_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnAccessDeniedException(operation, e);
            } else if (VALIDATION_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnInvalidRequestException(e);
            }

            throw new CfnGeneralServiceException(operation, e);
        } catch (final AmazonServiceException exception) {
            if (THROTTLING_ERROR_CODE.equals(exception.getErrorCode())) {
                throw new CfnThrottlingException(operation, exception);
            }

            throw new CfnGeneralServiceException(operation, exception);
        }
    }
}
