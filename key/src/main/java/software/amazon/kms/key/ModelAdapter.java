package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.kms.model.KeyUsageType;

public class ModelAdapter {
    protected static final String DEFAULT_DESCRIPTION = "";
    protected static final Boolean DEFAULT_ENABLED = true;
    protected static final Boolean DEFAULT_ENABLE_KEY_ROTATION = false;
    public static ResourceModel setDefaults(final ResourceModel model) {

        final String description = model.getDescription();
        final Boolean enabled = model.getEnabled();
        final Boolean enabledKeyRotation = model.getEnableKeyRotation();
        final String keyUsage = model.getKeyUsage();

        model.setDescription(StringUtils.isNullOrEmpty(description) ? DEFAULT_DESCRIPTION : description);
        model.setEnabled(enabled == null ? DEFAULT_ENABLED : enabled);
        model.setEnableKeyRotation(enabledKeyRotation == null ? DEFAULT_ENABLE_KEY_ROTATION : enabledKeyRotation);
        model.setKeyUsage(StringUtils.isNullOrEmpty(keyUsage) ? KeyUsageType.ENCRYPT_DECRYPT.toString() : keyUsage);
        return model;
    }

    public static ResourceModel unsetWriteOnly(final ResourceModel model) {
        return ResourceModel.builder()
                .arn(model.getArn())
                .description(model.getDescription())
                .enabled(model.getEnabled())
                .enableKeyRotation(model.getEnableKeyRotation())
                .keyId(model.getKeyId())
                .keyPolicy(model.getKeyPolicy())
                .keyUsage(model.getKeyUsage())
                .tags(model.getTags())
                .build();
    }
}
