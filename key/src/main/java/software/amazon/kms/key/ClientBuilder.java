package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {
    private static class LazyHolder { static final KmsClient KMS_CLIENT = KmsClient.builder()
        .httpClient(LambdaWrapper.HTTP_CLIENT)
        .build();
    }
    public static KmsClient getClient() {
        return LazyHolder.KMS_CLIENT;
    }
}
