package software.amazon.kms.common;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

public class CreatableKeyTranslatorTest {
    private static final Object MOCK_MODEL = new Object();

    private final CreatableKeyTranslator<Object> keyTranslator = new MockKeyTranslator();

    /**
     * The request translators are relatively trivial and the code to test them
     * would look a lot like their implementations. Instead of duplicating their code to test
     * them in depth, we will just call them and make sure they return a non null result and
     * do not cause any exceptions.
     */
    @Test
    public void testRequestTranslators() {
        assertThat(keyTranslator.createKeyRequest(MOCK_MODEL, TestConstants.TAGS)).isNotNull();
    }
}
