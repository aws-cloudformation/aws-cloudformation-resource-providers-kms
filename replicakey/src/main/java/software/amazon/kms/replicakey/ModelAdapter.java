package software.amazon.kms.replicakey;

import com.amazonaws.util.StringUtils;

public class ModelAdapter {
    private static final String DEFAULT_DESCRIPTION = "";
    private static final Boolean DEFAULT_ENABLED = true;

    private ModelAdapter() {
        // Prevent Instantiation
    }

    /**
     * Applies default values to the key resource model.
     *
     * @param model the model to apply the defaults to
     * @return a ResourceModel with defaults set
     */
    public static ResourceModel setDefaults(final ResourceModel model) {

        final String description = model.getDescription();
        final Boolean enabled = model.getEnabled();

        model.setDescription(StringUtils.isNullOrEmpty(description) ? DEFAULT_DESCRIPTION
            : description);
        model.setEnabled(enabled == null ? DEFAULT_ENABLED : enabled);

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
            .primaryKeyArn(model.getPrimaryKeyArn())
            .arn(model.getArn())
            .description(model.getDescription())
            .enabled(model.getEnabled())
            .keyId(model.getKeyId())
            .keyPolicy(model.getKeyPolicy())
            .tags(model.getTags())
            .build();
    }
}
