package software.amazon.kms.common;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;

public class TestConstants {
    // General test constants
    public static final String MOCK_TYPE_NAME = "MockType";
    public final static String NEXT_MARKER = "f251beae-00ff-4393";
    public static final String AWS_PARTITION = "aws";
    public static final String ACCOUNT_ID = "123456789";
    public static final Credentials MOCK_CREDENTIALS =
        new Credentials("accessKey", "secretKey", "token");
    public static final String KEY_ORIGIN = "AWS_KMS";
    public static final LoggerProxy LOGGER = new LoggerProxy();

    // Key related test constants
    public static final String KEY_POLICY =
        "{\"Sid\":\"Enable IAM User Permissions\",\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"kms:*\",\"Resource\":\"*\"}";
    public static final Map<String, Object> DESERIALIZED_KEY_POLICY =
        new ImmutableMap.Builder<String, Object>()
            .put("Sid", "Enable IAM User Permissions")
            .put("Effect", "Allow")
            .put("Principal", "*")
            .put("Action", "kms:*")
            .put("Resource", "*")
            .build();
    public final static String DEFAULT_KEY_POLICY = "{\n" +
            "    \"Version\": \"2012-10-17\",\n" +
            "    \"Id\": \"key-default-1\",\n" +
            "    \"Statement\": [\n" +
            "        {\n" +
            "            \"Sid\": \"Enable IAM User Permissions\",\n" +
            "            \"Effect\": \"Allow\",\n" +
            "            \"Principal\": {\n" +
            "                \"AWS\": \"arn:" + AWS_PARTITION + ":iam::" + ACCOUNT_ID + ":root\"\n" +
            "            },\n" +
            "            \"Action\": \"kms:*\",\n" +
            "            \"Resource\": \"*\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";

    public static String DEFAULT_KEY_POLICY_FROM_JSON = "{" +
            "\n" +
            "    \"Version\": \"2012-10-17\",\n" +
            "    \"Id\": \"key-default\",\n" +
            "    \"Statement\": [\n" +
            "        {\n" +
            "            \"Sid\": \"Enable IAM User Permissions\",\n" +
            "            \"Effect\": \"Allow\",\n" +
            "            \"Principal\": {\n" +
            "                \"AWS\": \"arn:<partition>:iam::<account-id>:root\"\n" +
            "            },\n" +
            "            \"Action\": \"kms:*\",\n" +
            "            \"Resource\": \"*\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";

    // Tag related test constants
    public static final Map<String, String> TAGS = new ImmutableMap.Builder<String, String>()
        .put("Key1", "Value1")
        .build();
    public static final Map<String, String> PREVIOUS_TAGS = new ImmutableMap.Builder<String, String>()
            .put("Key2", "Value2")
            .build();
    public static final Set<Tag> SDK_TAGS = new ImmutableSet.Builder<Tag>()
        .add(Tag.builder().tagKey("Key1").tagValue("Value1").build())
        .build();
    public static final Set<Tag> SDK_TAGS_NEW = new ImmutableSet.Builder<Tag>()
            .add(Tag.builder().tagKey("Key2").tagValue("Value2").build())
            .build();

    private TestConstants() {
        // Prevent Instantiation
    }
}
