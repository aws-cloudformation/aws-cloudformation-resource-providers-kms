package software.amazon.kms.key;

import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    protected boolean partiallyPropagated;
    protected boolean fullyPropagated;
    protected boolean keyStatusRotationUpdated;
}
