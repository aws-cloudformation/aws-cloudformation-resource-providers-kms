package software.amazon.kms.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.Tag;

/**
 * Basic implementation of CreatableKeyTranslator that allows us to test
 * the components implemented at the abstract class level.
 */
public class MockKeyTranslator extends CreatableKeyTranslator<Object> {
    protected MockKeyTranslator() {
        super();
    }

    protected MockKeyTranslator(final ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String getKeyId(final Object model) {
        return "mock-key-id";
    }

    @Override
    public String getKeyDescription(final Object model) {
        return "Mock description";
    }

    @Override
    public Object getKeyPolicy(final Object model) {
        return TestConstants.KEY_POLICY;
    }

    @Override
    public boolean getKeyEnabled(final Object model) {
        return false;
    }

    @Override
    public Integer getPendingWindowInDays(final Object model) {
        return 7;
    }

    @Override
    public KeyUsageType getKeyUsage(final Object model) {
        return KeyUsageType.ENCRYPT_DECRYPT;
    }

    @Override
    public KeySpec getKeySpec(final Object model) {
        return KeySpec.SYMMETRIC_DEFAULT;
    }

    @Override
    public Boolean isMultiRegion(final Object model) {
        return false;
    }

    @Override
    public Boolean isBypassPolicyLockoutSafetyCheck(Object model) {
        return false;
    }

    @Override
    public void setReadOnlyKeyMetadata(final Object model, final KeyMetadata keyMetadata) {

    }

    @Override
    public void setKeyMetadata(final Object model, final KeyMetadata describeKeyResponse) {

    }

    @Override
    public void setKeyPolicy(final Object model, final Object keyPolicy) {

    }

    @Override
    public void setTags(final Object model, final Set<Tag> tags) {

    }

    @Override
    public Object translateKeyListEntry(final KeyListEntry keyListEntry) {
        return new Object();
    }
}
