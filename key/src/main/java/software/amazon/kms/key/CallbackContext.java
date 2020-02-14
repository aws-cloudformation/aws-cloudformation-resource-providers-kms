package software.amazon.kms.key;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import software.amazon.kms.key.KeyStatus.KeyProgress;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallbackContext {
    private KeyProgress keyProgress;
    private int stabilizationRetriesRemaining;
}
