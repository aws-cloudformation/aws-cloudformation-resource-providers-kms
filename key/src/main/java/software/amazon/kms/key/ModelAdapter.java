package software.amazon.kms.key;

import com.amazonaws.util.StringUtils;

public class ModelAdapter {
    protected static final String DEFAULT_DESCRIPTION = "";
    protected static final Boolean DEFAULT_ENABLED = true;
    protected static final Boolean DEFAULT_ENABLE_KEY_ROTATION = false;
    protected static final String DEFAULT_KEY_USAGE = "ENCRYPT_DECRYPT";
    public static ResourceModel setDefaults(final ResourceModel resourceModel) {

        final String description = resourceModel.getDescription();
        final Boolean enabled = resourceModel.getEnabled();
        final Boolean enabledKeyRotation = resourceModel.getEnableKeyRotation();
        final String keyUsage = resourceModel.getKeyUsage();

        resourceModel.setDescription(StringUtils.isNullOrEmpty(description) ? DEFAULT_DESCRIPTION : description);
        resourceModel.setEnabled(enabled == null ? DEFAULT_ENABLED : enabled);
        resourceModel.setEnableKeyRotation(enabledKeyRotation == null ? DEFAULT_ENABLE_KEY_ROTATION : enabledKeyRotation);
        resourceModel.setKeyUsage(StringUtils.isNullOrEmpty(keyUsage) ? DEFAULT_KEY_USAGE : keyUsage);
        return resourceModel;
    }
}
