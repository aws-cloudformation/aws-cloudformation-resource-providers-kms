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
    public static final Credentials MOCK_CREDENTIALS =
        new Credentials("accessKey", "secretKey", "token");
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
