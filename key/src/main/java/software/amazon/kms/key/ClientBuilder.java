package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.KmsClient;

public class ClientBuilder {
    private static class LazyHolder { static final KmsClient KMS_CLIENT = KmsClient.create();}
    public static KmsClient getClient() {
        return LazyHolder.KMS_CLIENT;
    }
}
