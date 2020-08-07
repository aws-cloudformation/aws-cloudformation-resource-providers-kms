package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.proxy.StdCallbackContext;

import java.util.Set;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    protected boolean keyEnabled;
    protected boolean propagated;
    protected boolean keyStatusRotationUpdated;
    protected String marker;
    protected Set<Tag> existingTags;
}
