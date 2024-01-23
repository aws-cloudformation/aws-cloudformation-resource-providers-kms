package software.amazon.kms.common;

import software.amazon.cloudformation.proxy.ProgressEvent;

public class EventualConsistencyHandlerHelper <M, C extends EventualConsistencyCallbackContext> {
    // It may take up to 60 seconds for changes to propagate throughout the region for update
    // operation due to cache TTl set to 60 sec.
    public static final int EVENTUAL_CONSISTENCY_DELAY_SECONDS = 60;
    // Eventual consistency delay for create & delete operation to 15 secs
    public static final int CREATE_DELETE_EVENTUAL_CONSISTENCY_DELAY_SECONDS = 15;

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
        int delaySeconds = callbackContext.isUpdateRequest() ? EVENTUAL_CONSISTENCY_DELAY_SECONDS :
                CREATE_DELETE_EVENTUAL_CONSISTENCY_DELAY_SECONDS;
        return ProgressEvent.defaultInProgressHandler(callbackContext,
                delaySeconds,
            progressEvent.getResourceModel());
    }

    /**
     * Method for setting the request type, based on it the eventual consistency delay is determined.
     */
    public ProgressEvent<M, C> setRequestType(final ProgressEvent<M, C> progressEvent, boolean isUpdateRequest) {
        progressEvent.getCallbackContext().setUpdateRequest(isUpdateRequest);
        return progressEvent;

    }
}
