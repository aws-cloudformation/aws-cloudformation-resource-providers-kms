package software.amazon.kms.common;

import org.mockito.creation.instance.Instantiator;
import org.mockito.internal.configuration.plugins.Plugins;

/**
 * Basic implementation of AbstractKmsApiHelper that allows us to test
 * the components implemented at the abstract class level.
 */
public class MockKmsApiHelper extends AbstractKmsApiHelper {
    private static final String OPERATION = "MockOperation";
    private static final Instantiator INSTANTIATOR =
        Plugins.getInstantiatorProvider().getInstantiator(null);

    public void testExceptionWrapping(Class<? extends RuntimeException> c) {
        testExceptionWrapping(INSTANTIATOR.newInstance(c));
    }

    public void testExceptionWrapping(final RuntimeException e) {
        wrapKmsExceptions(OPERATION, () -> {
            throw e;
        });
    }
}
