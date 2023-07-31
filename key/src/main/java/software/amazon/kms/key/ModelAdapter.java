package software.amazon.kms.key;

import java.util.Map;

import com.amazonaws.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.cloudformation.exceptions.TerminalException;

public class ModelAdapter {
    private static final String DEFAULT_DESCRIPTION = "";
    private static final Boolean DEFAULT_ENABLED = true;
    private static final Boolean DEFAULT_ENABLE_KEY_ROTATION = false;
    private static final String DEFAULT_KEY_USAGE = KeyUsageType.ENCRYPT_DECRYPT.toString();
    private static final String DEFAULT_ORIGIN = OriginType.AWS_KMS.toString();
    private static final String DEFAULT_KEY_SPEC = KeySpec.SYMMETRIC_DEFAULT.toString();
    private static final Boolean DEFAULT_MULTI_REGION = false;

    private ModelAdapter() {
        // Prevent Instantiation
    }
    private static String getDefaultPolicy(final String awsPartition, final String accountId) {
        return "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Id\": \"key-default-1\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Sid\": \"Enable IAM User Permissions\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Principal\": {\n" +
                "                \"AWS\": \"arn:" + awsPartition + ":iam::" + accountId + ":root\"\n" +
                "            },\n" +
                "            \"Action\": \"kms:*\",\n" +
                "            \"Resource\": \"*\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
    }

    /**
     * Applies default values to the key resource model.
     *
     * @param model the model to apply the defaults to
     * @return a ResourceModel with defaults set
     */
    public static ResourceModel setDefaultsForCreateKey(final ResourceModel model) {

        final String description = model.getDescription();
        final Boolean enabled = model.getEnabled();
        final Boolean enabledKeyRotation = model.getEnableKeyRotation();
        final String keyUsage = model.getKeyUsage();
        final String keySpec = model.getKeySpec();
        final String origin = model.getOrigin();
        final Boolean multiRegion = model.getMultiRegion();

        model.setDescription(StringUtils.isNullOrEmpty(description) ? DEFAULT_DESCRIPTION
            : description);
        model.setEnabled(enabled == null ? DEFAULT_ENABLED : enabled);
        model.setEnableKeyRotation(enabledKeyRotation == null ? DEFAULT_ENABLE_KEY_ROTATION
            : enabledKeyRotation);
        model.setKeyUsage(StringUtils.isNullOrEmpty(keyUsage) ? DEFAULT_KEY_USAGE : keyUsage);
        model.setOrigin(StringUtils.isNullOrEmpty(origin) ? DEFAULT_ORIGIN : origin);
        model.setKeySpec(StringUtils.isNullOrEmpty(keySpec) ? DEFAULT_KEY_SPEC : keySpec);
        model.setMultiRegion(multiRegion == null ? DEFAULT_MULTI_REGION : multiRegion);
        return model;
    }

    public static ResourceModel setDefaults(final ResourceModel model,
                                            final String awsPartition, final String accountId ) {
        setDefaultsForCreateKey(model);
        final String keyPolicy = translatePolicyInput(model.getKeyPolicy());
        model.setKeyPolicy(StringUtils.isNullOrEmpty(keyPolicy) ? getDefaultPolicy(awsPartition, accountId) : keyPolicy);
        return model;
    }

    public static String translatePolicyInput(final Object policy) {
        // Key Policy can be specified as either a string or an object (JSON)
        // Convert it to a string, so it can be used in our API calls
        if (policy instanceof Map) {
            try {
                return new ObjectMapper().writeValueAsString(policy);
            } catch (final JsonProcessingException e) {
                throw new TerminalException(e);
            }
        }
        return (String) policy;
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
            .origin(model.getOrigin())
            .keySpec(model.getKeySpec())
            .multiRegion(model.getMultiRegion())
            .tags(model.getTags())
            .build();
    }
}
