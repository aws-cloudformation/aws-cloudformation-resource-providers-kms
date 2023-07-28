package software.amazon.kms.key;

import static org.assertj.core.api.Assertions.assertThat;


import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.kms.common.TestConstants;

public class TranslatorTest {
    private static final ResourceModel KEY_MODEL =
        ResourceModel.builder()
            .keyId("mock-key-id")
            .arn("mock-arn")
            .bypassPolicyLockoutSafetyCheck(false)
            .description("mock-description")
            .enabled(true)
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .pendingWindowInDays(7)
            .multiRegion(true)
            .tags(TestConstants.SDK_TAGS.stream().map(t -> Tag.builder()
                .key(t.tagKey())
                .value(t.tagValue())
                .build()).collect(Collectors.toSet()))
            .build();

    private final Translator translator = new Translator();

    @Test
    public void testGetters() {
        assertThat(translator.getKeyId(KEY_MODEL)).isEqualTo(KEY_MODEL.getKeyId());
        assertThat(translator.getKeyDescription(KEY_MODEL)).isEqualTo(KEY_MODEL.getDescription());
        assertThat(translator.getKeyPolicy(KEY_MODEL)).isEqualTo(KEY_MODEL.getKeyPolicy());
        assertThat(translator.getKeyEnabled(KEY_MODEL)).isEqualTo(KEY_MODEL.getEnabled());
        assertThat(translator.isBypassPolicyLockoutSafetyCheck(KEY_MODEL))
            .isEqualTo(KEY_MODEL.getBypassPolicyLockoutSafetyCheck());
        assertThat(translator.getPendingWindowInDays(KEY_MODEL))
            .isEqualTo(KEY_MODEL.getPendingWindowInDays());
        assertThat(translator.getKeyUsage(KEY_MODEL)).isEqualTo(KEY_MODEL.getKeyUsage());
        assertThat(translator.getOrigin(KEY_MODEL)).isEqualTo(KEY_MODEL.getOrigin());
        assertThat(translator.getKeySpec(KEY_MODEL)).isEqualTo(KEY_MODEL.getKeySpec());
        assertThat(translator.isMultiRegion(KEY_MODEL)).isEqualTo(KEY_MODEL.getMultiRegion());
    }

    @Test
    public void testSetKeyMetadata() {
        final ResourceModel model = ResourceModel.builder().build();
        final KeyMetadata keyMetadata = KeyMetadata.builder()
            .arn(KEY_MODEL.getArn())
            .keyId(KEY_MODEL.getKeyId())
            .description(KEY_MODEL.getDescription())
            .enabled(KEY_MODEL.getEnabled())
            .multiRegion(KEY_MODEL.getMultiRegion())
            .build();

        translator.setKeyMetadata(model, keyMetadata);

        assertThat(model.getArn()).isEqualTo(KEY_MODEL.getArn());
        assertThat(model.getKeyId()).isEqualTo(KEY_MODEL.getKeyId());
        assertThat(model.getDescription()).isEqualTo(KEY_MODEL.getDescription());
        assertThat(model.getEnabled()).isEqualTo(KEY_MODEL.getEnabled());
        assertThat(model.getKeyUsage()).isEqualTo(KEY_MODEL.getKeyUsage());
        assertThat(model.getOrigin()).isEqualTo(KEY_MODEL.getOrigin());
        assertThat(model.getKeySpec()).isEqualTo(KEY_MODEL.getKeySpec());
        assertThat(model.getMultiRegion()).isEqualTo(KEY_MODEL.getMultiRegion());
    }

    @Test
    public void testSetKeyPolicy() {
        final ResourceModel model = ResourceModel.builder().build();

        translator.setKeyPolicy(model, KEY_MODEL.getKeyPolicy());

        assertThat(model.getKeyPolicy()).isEqualTo(KEY_MODEL.getKeyPolicy());
    }

    @Test
    public void testSetTags() {
        final ResourceModel model = ResourceModel.builder().build();

        translator.setTags(model, TestConstants.SDK_TAGS);

        assertThat(model.getTags()).isEqualTo(KEY_MODEL.getTags());
    }

    /**
     * The request translators are relatively trivial and the code to test them
     * would look a lot like their implementations. Instead of duplicating their code to test
     * them in depth, we will just call them and make sure they return a non null result and
     * do not cause any exceptions.
     */
    @Test
    public void testRequestTranslators() {
        assertThat(translator.getKeyRotationStatusRequest(KEY_MODEL)).isNotNull();
        assertThat(translator.enableKeyRotationRequest(KEY_MODEL)).isNotNull();
        assertThat(translator.disableKeyRotationRequest(KEY_MODEL)).isNotNull();
        assertThat(translator
            .translateKeyListEntry(KeyListEntry.builder().keyId(KEY_MODEL.getKeyId()).build()))
            .isNotNull();
    }
}
