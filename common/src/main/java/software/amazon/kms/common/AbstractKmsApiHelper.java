package software.amazon.kms.common;

import java.util.function.Supplier;
import software.amazon.awssdk.services.kms.model.AlreadyExistsException;
import software.amazon.awssdk.services.kms.model.DependencyTimeoutException;
import software.amazon.awssdk.services.kms.model.DisabledException;
import software.amazon.awssdk.services.kms.model.InvalidAliasNameException;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.awssdk.services.kms.model.InvalidMarkerException;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.KmsInternalException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.LimitExceededException;
import software.amazon.awssdk.services.kms.model.MalformedPolicyDocumentException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.TagException;
import software.amazon.awssdk.services.kms.model.UnsupportedOperationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;

/**
 * Abstract Helper class for calling KMS APIs. The primary function of this class
 * is to wrap KMS service exceptions with the appropriate CloudFormation exception.
 * This is necessary so that CloudFormation can determine whether or not it should
 * retry a failed request.
 */
public class AbstractKmsApiHelper {
    public static final String THROTTLING_ERROR_CODE = "ThrottlingException";
    public static final String ACCESS_DENIED_ERROR_CODE = "AccessDeniedException";
    public static final String VALIDATION_ERROR_CODE = "ValidationException";

    protected <T> T wrapKmsExceptions(final String operation, final Supplier<T> serviceCall) {
        try {
            return serviceCall.get();
        } catch (final AlreadyExistsException e) {
            throw new CfnAlreadyExistsException(e);
        } catch (final InvalidAliasNameException | KmsInvalidStateException | InvalidArnException |
            MalformedPolicyDocumentException | TagException | UnsupportedOperationException |
            DisabledException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final LimitExceededException e) {
            throw new CfnServiceLimitExceededException(e);
        } catch (final InvalidMarkerException e) {
            // We should never make a call with an invalid marker, if we did, there is an issue
            throw new CfnInternalFailureException(e);
        } catch (final KmsInternalException | DependencyTimeoutException e) {
            throw new CfnServiceInternalErrorException(operation, e);
        } catch (final NotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final KmsException e) {
            if (ACCESS_DENIED_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnAccessDeniedException(operation, e);
            } else if (VALIDATION_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnInvalidRequestException(e);
            } else if (THROTTLING_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnThrottlingException(operation, e);
            }

            throw new CfnGeneralServiceException(operation, e);
        }
    }
}
