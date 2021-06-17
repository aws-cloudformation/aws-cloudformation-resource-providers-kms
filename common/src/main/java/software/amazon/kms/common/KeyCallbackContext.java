package software.amazon.kms.common;

import java.util.Set;
import software.amazon.awssdk.services.kms.model.Tag;

/**
 * Callback context for KMS key operations. Used for AWS::KMS::Key and AWS::KMS::ReplicaKey.
 */
@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class KeyCallbackContext extends EventualConsistencyCallbackContext {
    protected boolean keyEnabled;
    protected boolean keyPolicyUpdated;
    protected String tagMarker;
    protected Set<Tag> existingTags;
}
