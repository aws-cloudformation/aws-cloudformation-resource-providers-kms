package software.amazon.kms.replicakey;

import static org.assertj.core.api.Assertions.assertThat;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationTest {
    @Test
    public void testMergeDuplicateKeys() {
        final ResourceModel model = ResourceModel.builder()
            .tags(ImmutableSet.of(new Tag("sameKey", "value1"), new Tag("sameKey", "value2")))
            .build();

        final Configuration
            configuration = new Configuration();

        final Map<String, String> tags = configuration.resourceDefinedTags(model);

        assertThat(tags).isEqualTo(ImmutableMap.of("sameKey", "value2"));
    }
}
