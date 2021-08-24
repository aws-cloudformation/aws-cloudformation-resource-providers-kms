package software.amazon.kms.common;

import java.util.function.Supplier;

import com.google.common.base.Strings;

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
            throw new CfnAlreadyExistsException(addMessageIfNull(operation, e));
        } catch (final InvalidAliasNameException | KmsInvalidStateException | InvalidArnException |
            MalformedPolicyDocumentException | TagException | UnsupportedOperationException |
            DisabledException e) {
            throw new CfnInvalidRequestException(addMessageIfNull(operation, e));
        } catch (final LimitExceededException e) {
            throw new CfnServiceLimitExceededException(addMessageIfNull(operation, e));
        } catch (final InvalidMarkerException e) {
            // We should never make a call with an invalid marker, if we did, there is an issue
            throw new CfnInternalFailureException(addMessageIfNull(operation, e));
        } catch (final KmsInternalException | DependencyTimeoutException e) {
            throw new CfnServiceInternalErrorException(operation, e);
        } catch (final NotFoundException e) {
            throw new CfnNotFoundException(addMessageIfNull(operation, e));
        } catch (final KmsException e) {
            if (ACCESS_DENIED_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnAccessDeniedException(operation, e);
            } else if (VALIDATION_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnInvalidRequestException(addMessageIfNull(operation, e));
            } else if (THROTTLING_ERROR_CODE.equals(e.awsErrorDetails().errorCode())) {
                throw new CfnThrottlingException(operation, e);
            }

            throw new CfnGeneralServiceException(operation, e);
        }
    }

    /**
     * Generates exception messages for exceptions that didn't have one.
     *
     * @param operation Operation that caused the exception
     * @param e Exception that occurred
     * @return Exception
     */
    private Exception addMessageIfNull(final String operation, final Exception e) {
        // CloudFormation users only see the message of an exception that resulted in a failure,
        // and do not see its type. Some KMS exceptions (Like MalformedPolicyDocumentException)
        // do not have exception messages, so we need to add one that mentions the exception type and operation.
        final String defaultExceptionMessage = String.format("%s failed due to %s", operation,
                e.getClass().getSimpleName());
        if (!Strings.isNullOrEmpty(e.getMessage()) && e.getMessage().startsWith("null (")) {
            // Replace 'null', while keeping the request id that the SDK adds on
            return new Exception(e.getMessage().replaceFirst("null", defaultExceptionMessage), e);
        }

        return e;
    }
}
