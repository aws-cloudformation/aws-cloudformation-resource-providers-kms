package software.amazon.kms.common;

import software.amazon.cloudformation.proxy.ProgressEvent;

public class EventualConsistencyHandlerHelper <M, C extends EventualConsistencyCallbackContext> {
    // It may take up to 60 seconds for changes to propagate throughout the region
    public static final int EVENTUAL_CONSISTENCY_DELAY_SECONDS = 60;

    /**
     * Perform the final propagation delay to make sure the latest
     * version of the resource is available throughout the region.
     */
    public ProgressEvent<M, C> waitForChangesToPropagate(final ProgressEvent<M, C> progressEvent) {
        final C callbackContext = progressEvent.getCallbackContext();
        if (callbackContext.isPropagationComplete()) {
            return progressEvent;
        }

        callbackContext.setPropagationComplete(true);
        return ProgressEvent.defaultInProgressHandler(callbackContext,
            EVENTUAL_CONSISTENCY_DELAY_SECONDS,
            progressEvent.getResourceModel());
    }
}
