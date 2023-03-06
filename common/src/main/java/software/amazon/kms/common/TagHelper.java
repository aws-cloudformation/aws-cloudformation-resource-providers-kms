package software.amazon.kms.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class TagHelper<M, C extends KeyCallbackContext, T extends KeyTranslator<M>> {
    final T keyTranslator;
    final KeyApiHelper keyApiHelper;
    final KeyHandlerHelper<M, C, T> keyHandlerHelper;

    public TagHelper(final T keyTranslator, final KeyApiHelper keyApiHelper,
            final KeyHandlerHelper<M, C, T> keyHandlerHelper) {
        this.keyTranslator = keyTranslator;
        this.keyApiHelper = keyApiHelper;
        this.keyHandlerHelper = keyHandlerHelper;
    }

    /**
     * convertToSet
     *
     * Converts a tag map to a set of Tag objects.
     * Note: Like convertToMap, convertToSet filters out value-less tag entries.
     */
    public static Set<Tag> convertToSet(final Map<String, String> tagMap) {
        if (MapUtils.isEmpty(tagMap)) {
            return Collections.emptySet();
        }
        return tagMap.entrySet().stream()
                .filter(tag -> tag.getValue() != null)
                .map(tag -> Tag.builder()
                        .tagKey(tag.getKey())
                        .tagValue(tag.getValue())
                        .build())
                .collect(Collectors.toSet());
    }

    /**
     * generateTagsForCreate
     *
     * Generate tags to put into resource creation request.
     */
    public final Map<String, String>  generateTagsForCreate(final ResourceHandlerRequest<M> handlerRequest) {
        final Map<String, String> tagMap = new HashMap<>();

        // KMS does not support system tags currently, so we will not merge them in.
        if (handlerRequest.getDesiredResourceTags() != null) {
            tagMap.putAll(handlerRequest.getDesiredResourceTags());
        }

        return Collections.unmodifiableMap(tagMap);
    }

    /**
     * shouldUpdateTags
     *
     * Determines whether user defined tags have been changed during update.
     */
    public final boolean shouldUpdateTags(final ResourceHandlerRequest<M> handlerRequest) {
        final Map<String, String> previousTags = getPreviouslyAttachedTags(handlerRequest);
        final Map<String, String> desiredTags = getNewDesiredTags(handlerRequest);

        return ObjectUtils.notEqual(previousTags, desiredTags);
    }

    /**
     * getPreviouslyAttachedTags
     *
     * Get tags from the previous resource request.
     */
    public Map<String, String> getPreviouslyAttachedTags(final ResourceHandlerRequest<M> handlerRequest) {
        return handlerRequest.getPreviousResourceTags() != null ?
                handlerRequest.getPreviousResourceTags() : Collections.emptyMap();
    }

    /**
     * getNewDesiredTags
     *
     * Get the set of tags desired for this request.
     */
    public Map<String, String> getNewDesiredTags(final ResourceHandlerRequest<M> handlerRequest) {
        return handlerRequest.getDesiredResourceTags() != null ?
                handlerRequest.getDesiredResourceTags() : Collections.emptyMap();

    }

    /**
     * generateTagsToAdd
     *
     * Determines the tags the customer desired to add or update.
     */
    public Set<Tag> generateTagsToAdd(final Set<Tag> previousTags, final Set<Tag> desiredTags) {
        return Sets.difference(new HashSet<>(desiredTags), new HashSet<>(previousTags));
    }

    /**
     * getTagsToRemove
     *
     * Determines the tags the customer desired to remove.
     */
    public Set<Tag> generateTagsToRemove(final Set<Tag> previousTags, final Set<Tag> desiredTags) {
        return Sets.difference(new HashSet<>(previousTags), new HashSet<>(desiredTags));
    }

    /**
     * updateKeyTags
     *
     * Updates the user defined tags as the customer desired
     */
    public ProgressEvent<M, C>
    updateKeyTags(final AmazonWebServicesClientProxy proxy, final ProxyClient<KmsClient> proxyClient, final M model,
            final ResourceHandlerRequest<M> handlerRequest, final C callbackContext, final Map<String, String> desiredTags) {

        return ProgressEvent.progress(model, callbackContext)
                .then(progressEvent -> keyHandlerHelper.retrieveResourceTags(proxy, proxyClient, model,
                        callbackContext, false))
                .then(progressEvent -> {
                    final Set<Tag> existingTags = convertToSet(getPreviouslyAttachedTags(handlerRequest));
                    final Set<Tag> tagsToRemove = generateTagsToRemove(existingTags, convertToSet(desiredTags));
                    if (!tagsToRemove.isEmpty()) {
                        return proxy
                                .initiate("kms::untag-key", proxyClient, model, callbackContext)
                                .translateToServiceRequest(
                                        (m) -> keyTranslator.untagResourceRequest(m, tagsToRemove))
                                .makeServiceCall(keyApiHelper::untagResource)
                                .progress();
                    }
                    return progressEvent;
                })
                .then(progressEvent -> {
                    final Set<Tag> existingTags =
                            convertToSet(getPreviouslyAttachedTags(handlerRequest));
                    final Set<Tag> tagsToAdd = generateTagsToAdd(existingTags, convertToSet(desiredTags));
                    if (!tagsToAdd.isEmpty()) {
                        return proxy
                                .initiate("kms::tag-key", proxyClient, model, callbackContext)
                                .translateToServiceRequest(
                                        (m) -> keyTranslator.tagResourceRequest(m, tagsToAdd))
                                .makeServiceCall(keyApiHelper::tagResource)
                                .progress();
                    }
                    return progressEvent;
                });
    }
}
