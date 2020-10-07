package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.kms.model.CustomerMasterKeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;

public class ModelAdapter {
    private static final String DEFAULT_DESCRIPTION = "";
    private static final Boolean DEFAULT_ENABLED = true;
    private static final Boolean DEFAULT_ENABLE_KEY_ROTATION = false;
    private static final String DEFAULT_KEY_USAGE = KeyUsageType.ENCRYPT_DECRYPT.toString();
    private static final String DEFAULT_KEY_SPEC = CustomerMasterKeySpec.SYMMETRIC_DEFAULT
            .toString();

    /**
     * Applies default values to the key resource model.
     *
     * @param model the model to apply the defaults to
     * @return a ResourceModel with defaults set
     */
    public static ResourceModel setDefaults(final ResourceModel model) {

        final String description = model.getDescription();
        final Boolean enabled = model.getEnabled();
        final Boolean enabledKeyRotation = model.getEnableKeyRotation();
        final String keyUsage = model.getKeyUsage();
        final String keySpec = model.getKeySpec();

        model.setDescription(StringUtils.isNullOrEmpty(description) ? DEFAULT_DESCRIPTION
                : description);
        model.setEnabled(enabled == null ? DEFAULT_ENABLED : enabled);
        model.setEnableKeyRotation(enabledKeyRotation == null ? DEFAULT_ENABLE_KEY_ROTATION
                : enabledKeyRotation);
        model.setKeyUsage(StringUtils.isNullOrEmpty(keyUsage) ? DEFAULT_KEY_USAGE : keyUsage);
        model.setKeySpec(StringUtils.isNullOrEmpty(keySpec) ? DEFAULT_KEY_SPEC : keySpec);
        return model;
    }

    /**
     * Redacts any write-only properties from the key resource model.
     *
     * @param model the model to redact
     * @return a ResourceModel with write-only properties redacted
     */
    public static ResourceModel unsetWriteOnly(final ResourceModel model) {
        return ResourceModel.builder()
                .arn(model.getArn())
                .description(model.getDescription())
                .enabled(model.getEnabled())
                .enableKeyRotation(model.getEnableKeyRotation())
                .keyId(model.getKeyId())
                .keyPolicy(model.getKeyPolicy())
                .keyUsage(model.getKeyUsage())
                .keySpec(model.getKeySpec())
                .tags(model.getTags())
                .build();
    }
}
