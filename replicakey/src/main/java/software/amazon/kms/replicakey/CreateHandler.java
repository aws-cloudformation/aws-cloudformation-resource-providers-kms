package software.amazon.kms.replicakey;

import static software.amazon.kms.replicakey.ModelAdapter.setDefaults;
import static software.amazon.kms.replicakey.ModelAdapter.unsetWriteOnly;


import com.amazonaws.util.StringUtils;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.kms.common.ClientBuilder;
import software.amazon.kms.common.EventualConsistencyHandlerHelper;
import software.amazon.kms.common.KeyApiHelper;
import software.amazon.kms.common.KeyHandlerHelper;
import software.amazon.kms.common.KeyTranslator;
import software.amazon.kms.common.TagHelper;

public class CreateHandler extends BaseHandlerStd {
    private static final BiFunction<ResourceModel, ProxyClient<KmsClient>, ResourceModel>
        EMPTY_CALL = (model, proxyClient) -> model;

    public CreateHandler() {
        super();
    }

    public CreateHandler(final ClientBuilder clientBuilder,
                         final Translator translator,
                         final KeyApiHelper keyApiHelper,
                         final EventualConsistencyHandlerHelper<ResourceModel, CallbackContext>
                             eventualConsistencyHandlerHelper,
                         final KeyHandlerHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> keyHandlerHelper,
                         final TagHelper<ResourceModel, CallbackContext, KeyTranslator<ResourceModel>> tagHelper) {
        super(clientBuilder, translator, keyApiHelper, eventualConsistencyHandlerHelper,
            keyHandlerHelper, tagHelper);
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KmsClient> proxyClient,
        final Logger logger) {
        final ResourceModel model = setDefaults(request.getDesiredResourceState());

        return ProgressEvent.progress(model, callbackContext)
            .then(progress -> {
                    // We need to make the replicate request to the primary region
                    final ProxyClient<KmsClient> primaryRegionClient = proxy.newProxy(clientBuilder
                        .getClientForArnRegion(model.getPrimaryKeyArn()));

                    return proxy
                        .initiate("kms::replicate-key", primaryRegionClient, model, callbackContext)
                        .translateToServiceRequest(
                            m -> translator.replicateKeyRequest(m, request.getRegion(),
                                tagHelper.generateTagsForCreate(request)))
                        .makeServiceCall(keyApiHelper::replicateKey)
                        .done(replicateKeyResponse -> {
                            // Continue along if we have already saved the replica key Id
                            if (!StringUtils.isNullOrEmpty(model.getKeyId())) {
                                return ProgressEvent.progress(model, callbackContext);
                            }

                            // Update our resource model with the replica key's Id
                            model.setKeyId(replicateKeyResponse.replicaKeyMetadata().keyId());
                            model.setArn(replicateKeyResponse.replicaKeyMetadata().arn());

                            // Wait for key state to propagate to other hosts
                            return ProgressEvent.defaultInProgressHandler(callbackContext,
                                EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS,
                                model);
                        });
                }
            )
            /*
             * Wait until after kms::replicate-key to stabilize the replica.
             * This allows us to do the eventual consistency wait before stabilization,
             * which makes it more likely that the replica will be stabilized on the first
             * stabilization check.
             */
            .then(progress -> proxy
                .initiate("kms::replicate-key-is-done-creating", proxyClient, model,
                    callbackContext)
                .translateToServiceRequest(Function.identity())
                .makeServiceCall(EMPTY_CALL)
                .stabilize((replicateRequest, replicateResponse, client, m, ctx) ->
                    isDoneCreating(model, proxyClient))
                .progress())
            .then(progress -> keyHandlerHelper
                .disableKeyIfNecessary(proxy, proxyClient, null, model, callbackContext))
            // Final propagation to make sure all updates are reflected
            .then(eventualConsistencyHandlerHelper::waitForChangesToPropagate)
            .then(progress -> ProgressEvent.defaultSuccessHandler(unsetWriteOnly(model)));
    }

    private boolean isDoneCreating(final ResourceModel model,
                                   final ProxyClient<KmsClient> proxyClient) {
        final KeyState keyState;
        try {
            keyState = keyApiHelper.describeKey(
                translator.describeKeyRequest(model.getArn()), proxyClient).keyMetadata()
                .keyState();
        } catch (final CfnNotFoundException e) {
            return false;
        }

        return !keyState.equals(KeyState.CREATING);
    }
}
