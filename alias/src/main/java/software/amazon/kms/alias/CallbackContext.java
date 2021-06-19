package software.amazon.kms.alias;

import software.amazon.kms.common.EventualConsistencyCallbackContext;

/**
 * The only context needed for the Alias handlers is the common eventual consistency context.
 * This class is still required since the CFN java plugin expects it to be here, with this name.
 */
public class CallbackContext extends EventualConsistencyCallbackContext {

}
