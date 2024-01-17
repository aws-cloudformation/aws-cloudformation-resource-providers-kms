package software.amazon.kms.common;

import com.amazonaws.util.StringUtils;
import java.util.Map;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;

/**
 * A KeyHandlerHelper with key creation logic.
 *
 * @param <M> The CFN resource type's resource model type
 * @param <C> The CFN resource type's callback context type
 * @param <T> The CreatableKeyTranslator<M> used to translate the resource model
 */
public class CreatableKeyHandlerHelper<M, C extends KeyCallbackContext, T extends CreatableKeyTranslator<M>>
    extends KeyHandlerHelper<M, C, T> {

    public CreatableKeyHandlerHelper(final String typeName,
                                     final KeyApiHelper keyApiHelper,
                                     final EventualConsistencyHandlerHelper<M, C> eventualConsistencyHandlerHelper,
                                     final T keyTranslator) {
        super(typeName, keyApiHelper, eventualConsistencyHandlerHelper, keyTranslator);
    }

    /**
     * Creates a KMS key, updates the resource model,
     * and then waits for it to propagate throughout the region.
     */
    public ProgressEvent<M, C> createKey(
        final AmazonWebServicesClientProxy proxy,
        final ProxyClient<KmsClient> proxyClient,
        final M model,
        final C callbackContext,
        final Map<String, String> tags
    ) {
        return proxy.initiate("kms::create-key", proxyClient, model, callbackContext)
            .translateToServiceRequest(m -> keyTranslator.createKeyRequest(model, tags))
            .makeServiceCall(keyApiHelper::createKey)
            .done(createKeyResponse -> {
                if (!StringUtils.isNullOrEmpty(keyTranslator.getKeyId(model))) {
                    return ProgressEvent.progress(model, callbackContext);
                }

                // We only want to update the metadata that we have set as read only,
                // since updating writable metadata at this point would only
                // overwrite the properties supplied in the CFN template.
                keyTranslator.setReadOnlyKeyMetadata(model, createKeyResponse.keyMetadata());
                return ProgressEvent.progress(model, callbackContext);
            });
    }
}
