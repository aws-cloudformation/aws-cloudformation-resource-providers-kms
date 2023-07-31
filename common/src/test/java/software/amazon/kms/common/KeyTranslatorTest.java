package software.amazon.kms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.TerminalException;

public class KeyTranslatorTest {
    private static final Object MOCK_MODEL = new Object();

    private final KeyTranslator<Object> keyTranslator = new MockKeyTranslator();

    @Test
    public void testTranslateTagsToSdkNull() {
        assertThat(keyTranslator.translateTagsToSdk(null)).isEmpty();
    }

    @Test
    public void testTranslateTagsToSdk() {
        assertThat(keyTranslator.translateTagsToSdk(TestConstants.TAGS))
            .isEqualTo(TestConstants.SDK_TAGS);
    }

    @Test
    public void testTranslatePolicyInputString() {
        assertThat(keyTranslator.translatePolicyInput(TestConstants.KEY_POLICY))
            .isEqualTo(TestConstants.KEY_POLICY);
    }

    @Test
    public void testTranslatePolicyInputObject() {
        assertThat(keyTranslator
            .translatePolicyInput(TestConstants.DESERIALIZED_KEY_POLICY))
            .isEqualTo(TestConstants.KEY_POLICY);
    }

    @Test
    public void testTranslatePolicyInputJsonProcessingException() throws JsonProcessingException {
        final ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.writeValueAsString(eq(TestConstants.DESERIALIZED_KEY_POLICY)))
            .thenThrow(JsonProcessingException.class);
        final MockKeyTranslator mockKeyTranslator = new MockKeyTranslator(mockMapper);

        assertThatExceptionOfType(TerminalException.class).isThrownBy(
            () -> mockKeyTranslator.translatePolicyInput(TestConstants.DESERIALIZED_KEY_POLICY));
    }

    @Test
    public void testDeserializeKeyPolicyNull() {
        assertThat(keyTranslator.deserializeKeyPolicy(null)).isNull();
    }

    @Test
    public void testDeserializeKeyPolicy() {
        assertThat(keyTranslator.deserializeKeyPolicy(TestConstants.KEY_POLICY))
            .isEqualTo(TestConstants.DESERIALIZED_KEY_POLICY);
    }

    @Test
    public void testDeserializeInvalidKeyPolicy() {
        assertThatExceptionOfType(CfnInternalFailureException.class).isThrownBy(
            () -> keyTranslator.deserializeKeyPolicy("\"InvalidPolicy"));
    }

    /**
     * The request translators are relatively trivial and the code to test them
     * would look a lot like their implementations. Instead of duplicating their code to test
     * them in depth, we will just call them and make sure they return a non null result and
     * do not cause any exceptions.
     */
    @Test
    public void testRequestTranslators() {
        assertThat(keyTranslator.describeKeyRequest("mock-key-id")).isNotNull();
        assertThat(keyTranslator.listKeysRequest(TestConstants.NEXT_MARKER)).isNotNull();
        assertThat(keyTranslator.describeKeyRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.listResourceTagsRequest(MOCK_MODEL, TestConstants.NEXT_MARKER))
            .isNotNull();
        assertThat(keyTranslator.updateKeyDescriptionRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.getKeyPolicyRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.putKeyPolicyRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.scheduleKeyDeletionRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.enableKeyRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.disableKeyRequest(MOCK_MODEL)).isNotNull();
        assertThat(keyTranslator.tagResourceRequest(MOCK_MODEL, TestConstants.SDK_TAGS))
            .isNotNull();
        assertThat(keyTranslator.untagResourceRequest(MOCK_MODEL, TestConstants.SDK_TAGS))
            .isNotNull();
    }
}
