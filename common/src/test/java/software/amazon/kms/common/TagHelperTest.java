package software.amazon.kms.common;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.awssdk.services.kms.model.TagResourceRequest;
import software.amazon.awssdk.services.kms.model.TagResourceResponse;
import software.amazon.awssdk.services.kms.model.UntagResourceRequest;
import software.amazon.awssdk.services.kms.model.UntagResourceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TagHelperTest {

    @Mock
    private KmsClient kms;

    @Spy
    private MockKeyTranslator keyTranslator;

    @Mock
    private KeyApiHelper keyApiHelper;

    @Mock
    private KeyHandlerHelper<Object, KeyCallbackContext, KeyTranslator<Object>>
        keyHandlerHelper;

    private TagHelper<Object, KeyCallbackContext, KeyTranslator<Object>> tagHelper;
    private Object MOCK_MODEL;
    private KeyCallbackContext callbackContext;
    private Set<Tag> previousTags;
    private Set<Tag> desiredTags;
    private ProxyClient<KmsClient> proxyKmsClient;
    private ProgressEvent<Object, KeyCallbackContext> inProgressEvent;
    private TagResourceResponse tagResourceResponse;
    private UntagResourceResponse untagResourceResponse;
    private AmazonWebServicesClientProxy proxy;

    @BeforeEach
    public void setup() {
        tagHelper = new TagHelper<>(keyTranslator, keyApiHelper, keyHandlerHelper);
        callbackContext = new KeyCallbackContext();
        proxy = new AmazonWebServicesClientProxy(TestConstants.LOGGER,
            TestConstants.MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyKmsClient = TestUtils.buildMockProxy(proxy, kms);
        MOCK_MODEL = new Object();
        tagResourceResponse = TagResourceResponse.builder().build();
        untagResourceResponse = UntagResourceResponse.builder().build();
        previousTags = TestConstants.SDK_TAGS;
        desiredTags = TestConstants.SDK_TAGS_NEW;
        inProgressEvent = ProgressEvent.progress(MOCK_MODEL, callbackContext);
    }

    @Test
    public  void testConvertToSet() {
        final Map<String, String> tagMap = TestConstants.TAGS;
        assertThat(tagHelper.convertToSet(tagMap)).
            isEqualTo(TestConstants.SDK_TAGS);
    }

    @Test
    public void testConvertToSetEmpty() {
    final Map<String, String> tagMap = new HashMap<>();
    assertThat(tagHelper.convertToSet(tagMap))
            .isEqualTo(Collections.emptySet());
    }

    @Test
    public void testGenerateTagsForCreate() {
        final ResourceHandlerRequest<Object> request =
            ResourceHandlerRequest.<Object>builder()
                .desiredResourceTags(TestConstants.TAGS)
                .previousResourceTags(TestConstants.TAGS)
                .build();
        assertThat(tagHelper.generateTagsForCreate(request))
            .isEqualTo(TestConstants.TAGS);
    }

    @Test
    public void testGenerateTagsForCreateNull() {
        final ResourceHandlerRequest<Object> request =
            ResourceHandlerRequest.<Object>builder()
                .desiredResourceTags(null)
                .build();
        assertThat(tagHelper.generateTagsForCreate(request))
            .isEqualTo(new HashMap<>());
    }

    @Test
    public void testShouldUpdateTagsReturnsTrue() {
        final ResourceHandlerRequest<Object> request = ResourceHandlerRequest.<Object>builder()
            .desiredResourceTags(TestConstants.TAGS)
            .previousResourceTags(TestConstants.PREVIOUS_TAGS)
            .build();
        assertThat(tagHelper.shouldUpdateTags(request)).
            isEqualTo(true);
    }

    @Test
    public void testShouldUpdateTagsReturnsFalse() {
        final ResourceHandlerRequest<Object> request = ResourceHandlerRequest.<Object>builder()
            .desiredResourceTags(TestConstants.TAGS)
            .previousResourceTags(TestConstants.TAGS)
            .build();
        assertThat(tagHelper.shouldUpdateTags(request)).
            isEqualTo(false);
    }

    @Test
    public void testGetPreviouslyAttachedTagsTest() {
        final ResourceHandlerRequest<Object> request =
            ResourceHandlerRequest.<Object>builder()
                .previousResourceTags(TestConstants.PREVIOUS_TAGS)
                .build();
        assertThat(tagHelper.getPreviouslyAttachedTags(request))
            .isEqualTo(TestConstants.PREVIOUS_TAGS);
    }

    @Test
    public void testGenerateTagsToAdd() {
        assertThat(tagHelper.generateTagsToAdd(previousTags, desiredTags))
            .isEqualTo(desiredTags);
    }

    @Test
    public void testGenerateTagsToRemove() {
        assertThat(tagHelper.generateTagsToRemove(previousTags, desiredTags)).
            isEqualTo(previousTags);
        }

    @Test
    public void testUpdateKeyTags() {
        ResourceHandlerRequest<Object> request = ResourceHandlerRequest.<Object>builder()
            .desiredResourceTags(TestConstants.TAGS)
            .previousResourceTags(TestConstants.PREVIOUS_TAGS)
            .build();

        when(keyApiHelper.tagResource(any(TagResourceRequest.class), eq(proxyKmsClient)))
            .thenReturn(tagResourceResponse);
        when(keyApiHelper.untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient)))
                .thenReturn(untagResourceResponse);
        when(keyHandlerHelper.retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(MOCK_MODEL), eq(callbackContext),
                eq(false))).thenReturn(inProgressEvent);

        assertThat(tagHelper.updateKeyTags(proxy,proxyKmsClient,MOCK_MODEL,request,callbackContext,TestConstants.TAGS))
                .isEqualTo(inProgressEvent);
        verify(keyHandlerHelper)
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(MOCK_MODEL), eq(callbackContext), eq(false));
        verify(keyApiHelper).tagResource(any(TagResourceRequest.class), eq(proxyKmsClient));
        verify(keyApiHelper).untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient));
    }

    @Test
    public void testNoUpdateKeyTags() {
        ResourceHandlerRequest<Object> request = ResourceHandlerRequest.<Object>builder()
            .previousResourceTags(TestConstants.PREVIOUS_TAGS)
            .build();

        when(keyHandlerHelper.retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(MOCK_MODEL), eq(callbackContext),
                eq(false))).thenReturn(inProgressEvent);

        assertThat(tagHelper.updateKeyTags(proxy,proxyKmsClient,MOCK_MODEL,request,callbackContext,
                TestConstants.PREVIOUS_TAGS)).isEqualTo(inProgressEvent);
        verify(keyHandlerHelper)
            .retrieveResourceTags(eq(proxy), eq(proxyKmsClient), eq(MOCK_MODEL), eq(callbackContext), eq(false));
        verify(keyApiHelper, never()).tagResource(any(TagResourceRequest.class), eq(proxyKmsClient));
        verify(keyApiHelper, never()).untagResource(any(UntagResourceRequest.class), eq(proxyKmsClient));
    }
}
