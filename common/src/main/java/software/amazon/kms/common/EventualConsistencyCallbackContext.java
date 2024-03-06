package software.amazon.kms.common;

import software.amazon.cloudformation.proxy.StdCallbackContext;

/**
 * Callback context for eventually consistent operations. Used for all KMS resources.
 */
@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class EventualConsistencyCallbackContext extends StdCallbackContext {
    protected boolean propagationComplete;
    protected boolean updateRequest = true;
}
