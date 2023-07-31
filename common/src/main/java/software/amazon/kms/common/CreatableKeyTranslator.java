package software.amazon.kms.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.OriginType;

/**
 * A KeyTranslator with all of the translation logic necessary for key creation.
 *
 * @param <M> The CFN resource type's resource model type
 */
public abstract class CreatableKeyTranslator<M> extends KeyTranslator<M> {

    protected CreatableKeyTranslator() {
        this(new ObjectMapper());
    }

    protected CreatableKeyTranslator(final ObjectMapper objectMapper) {
        super(objectMapper);
    }

    public abstract KeyUsageType getKeyUsage(final M model);

    public abstract OriginType getOrigin(final M model);

    public abstract KeySpec getKeySpec(final M model);

    public abstract Boolean isMultiRegion(final M model);

    public abstract Boolean isBypassPolicyLockoutSafetyCheck(final M model);

    public abstract void setReadOnlyKeyMetadata(final M model, final KeyMetadata keyMetadata);

    public CreateKeyRequest createKeyRequest(final M model, final Map<String, String> tags) {
        return CreateKeyRequest.builder()
            .bypassPolicyLockoutSafetyCheck(isBypassPolicyLockoutSafetyCheck(model))
            .description(getKeyDescription(model))
            .keyUsage(getKeyUsage(model))
            .origin(getOrigin(model))
            .keySpec(getKeySpec(model))
            .policy(translatePolicyInput(getKeyPolicy(model)))
            .multiRegion(isMultiRegion(model))
            .tags(translateTagsToSdk(tags))
            .build();
    }
}
