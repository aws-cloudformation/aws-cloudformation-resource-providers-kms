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
     *
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

        // merge system tags with desired resource tags if your service supports CloudFormation system tags
        // KMS does not support system tags currently.
        // tagMap.putAll(handlerRequest.getSystemTags());
        if (handlerRequest.getDesiredResourceTags() != null) {
            tagMap.putAll(handlerRequest.getDesiredResourceTags());
        }
        // TODO: get tags from resource model based on your tag property name.
        // TODO: tagMap.putAll(convertToMap(resourceModel.getTags()));
        // getDesiredResourceTags() gets both resource and stack level tags. getTags() gets resource level tags only.
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
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get previous attached user defined tags from both handlerRequest.getPreviousResourceTags (stack tags)
     * and handlerRequest.getPreviousResourceState (resource tags).
     */
    public Map<String, String> getPreviouslyAttachedTags(final ResourceHandlerRequest<M> handlerRequest) {
        // get previous stack level tags from handlerRequest
        final Map<String, String> previousTags = handlerRequest.getPreviousResourceTags() != null ?
                handlerRequest.getPreviousResourceTags() : Collections.emptyMap();
        // TODO: get resource level tags from previous resource state based on your tag property name
        // TODO: previousTags.putAll(handlerRequest.getPreviousResourceState().getTags());
        // getDesiredResourceTags() gets both resource and stack level tags. getTags() gets resource level tags only.
        return previousTags;
    }

    /**
     * getNewDesiredTags
     *
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get new user defined tags from both resource model and previous stack tags.
     */
    public Map<String, String> getNewDesiredTags(final ResourceHandlerRequest<M> handlerRequest) {
        // get new stack level tags from handlerRequest
        final Map<String, String> desiredTags = handlerRequest.getDesiredResourceTags() != null ?
                handlerRequest.getDesiredResourceTags() : Collections.emptyMap();

        // TODO: get resource level tags from resource model based on your tag property name
        // TODO: desiredTags.putAll(convertToMap(resourceModel.getTags()));
        // getDesiredResourceTags() gets both resource and stack level tags. getTags() gets resource level tags only.
        return desiredTags;

    }

    /**
     * generateTagsToAdd
     *
     * Determines the tags the customer desired to define or redefine.
     */
    public Set<Tag> generateTagsToAdd(final Set<Tag> previousTags, final Set<Tag> desiredTags) {
        return Sets.difference(new HashSet<>(desiredTags), new HashSet<>(previousTags));
    }

    /**
     * getTagsToRemove
     *
     * Determines the tags the customer desired to remove from the function.
     */
    public Set<Tag> generateTagsToRemove(final Set<Tag> previousTags, final Set<Tag> desiredTags) {
        return Sets.difference(new HashSet<>(previousTags), new HashSet<>(desiredTags));
    }

    /**
     * updateKeyTags
     *
     * Updates the user defined tags as the customer desired to define or redefine
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
