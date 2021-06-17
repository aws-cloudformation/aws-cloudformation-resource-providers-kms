package software.amazon.kms.common;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.cloudformation.proxy.ProgressEvent;

public class EventualConsistencyHandlerHelperTest {
    private static final Object MOCK_MODEL = new Object();

    private EventualConsistencyHandlerHelper<Object, EventualConsistencyCallbackContext>
        eventualConsistencyHandlerHelper;
    private EventualConsistencyCallbackContext eventualConsistencyCallbackContext;

    @BeforeEach
    public void setup() {
        eventualConsistencyHandlerHelper = new EventualConsistencyHandlerHelper<>();
        eventualConsistencyCallbackContext = new EventualConsistencyCallbackContext();
    }

    @Test
    public void testWaitForChangesToPropagate() {
        assertThat(eventualConsistencyHandlerHelper.waitForChangesToPropagate(
            ProgressEvent.progress(MOCK_MODEL, eventualConsistencyCallbackContext))).isEqualTo(
            ProgressEvent.defaultInProgressHandler(eventualConsistencyCallbackContext,
                EventualConsistencyHandlerHelper.EVENTUAL_CONSISTENCY_DELAY_SECONDS, MOCK_MODEL));
        assertThat(eventualConsistencyCallbackContext.isPropagationComplete()).isTrue();
    }

    @Test
    public void testWaitForChangesToPropagateAlreadyPropagated() {
        eventualConsistencyCallbackContext.setPropagationComplete(true);

        assertThat(eventualConsistencyHandlerHelper.waitForChangesToPropagate(
            ProgressEvent.progress(MOCK_MODEL, eventualConsistencyCallbackContext)))
            .isEqualTo(ProgressEvent.progress(MOCK_MODEL, eventualConsistencyCallbackContext));
        assertThat(eventualConsistencyCallbackContext.isPropagationComplete()).isTrue();
    }
}
