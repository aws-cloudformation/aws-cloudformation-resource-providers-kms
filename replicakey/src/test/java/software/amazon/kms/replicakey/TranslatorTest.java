package software.amazon.kms.replicakey;

import static org.assertj.core.api.Assertions.assertThat;


import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.MultiRegionConfiguration;
import software.amazon.awssdk.services.kms.model.MultiRegionKey;
import software.amazon.kms.common.TestConstants;

public class TranslatorTest {
    private static final ResourceModel KEY_MODEL =
        ResourceModel.builder()
            .keyId("mock-key-id")
            .arn("mock-arn")
            .primaryKeyArn("mock-primary-arn")
            .description("mock-description")
            .enabled(true)
            .keyPolicy(TestConstants.DESERIALIZED_KEY_POLICY)
            .pendingWindowInDays(7)
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
        assertThat(translator.getPendingWindowInDays(KEY_MODEL))
            .isEqualTo(KEY_MODEL.getPendingWindowInDays());
    }

    @Test
    public void testSetKeyMetadata() {
        final ResourceModel model = ResourceModel.builder().build();
        final KeyMetadata keyMetadata = KeyMetadata.builder()
            .arn(KEY_MODEL.getArn())
            .keyId(KEY_MODEL.getKeyId())
            .description(KEY_MODEL.getDescription())
            .enabled(KEY_MODEL.getEnabled())
            .multiRegionConfiguration(MultiRegionConfiguration.builder()
                .primaryKey(MultiRegionKey.builder()
                    .arn(KEY_MODEL.getPrimaryKeyArn())
                    .build())
                .build())
            .build();

        translator.setKeyMetadata(model, keyMetadata);

        assertThat(model.getArn()).isEqualTo(KEY_MODEL.getArn());
        assertThat(model.getKeyId()).isEqualTo(KEY_MODEL.getKeyId());
        assertThat(model.getDescription()).isEqualTo(KEY_MODEL.getDescription());
        assertThat(model.getEnabled()).isEqualTo(KEY_MODEL.getEnabled());
        assertThat(model.getPrimaryKeyArn()).isEqualTo(KEY_MODEL.getPrimaryKeyArn());
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
        assertThat(
            translator.replicateKeyRequest(KEY_MODEL, Region.US_EAST_1.id(), TestConstants.TAGS))
            .isNotNull();
        assertThat(translator
            .translateKeyListEntry(KeyListEntry.builder().keyId(KEY_MODEL.getKeyId()).build()))
            .isNotNull();
    }
}
