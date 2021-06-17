package software.amazon.kms.replicakey;

import software.amazon.kms.common.KeyCallbackContext;

/**
 * The Key and ReplicaKey resources share the same callback context.
 * This class is required since the CFN java plugin expects it to exist here and with this name.
 */
public class CallbackContext extends KeyCallbackContext {

}
