package software.amazon.kms.key;

import java.util.Set;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    protected boolean keyEnabled;
    protected boolean propagated;
    protected boolean keyPolicyUpdated;
    protected String marker;
    protected Set<Tag> existingTags;
}
