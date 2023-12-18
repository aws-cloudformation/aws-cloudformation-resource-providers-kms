package software.amazon.kms.alias;

import software.amazon.kms.common.EventualConsistencyCallbackContext;

/**
 * The only context needed for the Alias handlers is the common eventual consistency context.
 * This class is still required since the CFN java plugin expects it to be here, with this name.
 */
@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class CallbackContext extends EventualConsistencyCallbackContext {
    private boolean isPreCreateCheckDone;
}
