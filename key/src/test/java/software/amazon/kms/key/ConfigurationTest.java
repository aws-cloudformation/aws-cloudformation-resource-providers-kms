package software.amazon.kms.key;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

class ConfigurationTest {

    @Test
    public void testMergeDuplicateKeys() {
        final ResourceModel model = ResourceModel.builder()
            .tags(ImmutableSet.of(new Tag("sameKey", "value1"), new Tag("sameKey", "value2")))
            .build();

        final Configuration configuration = new Configuration();

        final Map<String, String> tags = configuration.resourceDefinedTags(model);

        assertEquals(ImmutableMap.of("sameKey", "value2"), tags);

    }

}
