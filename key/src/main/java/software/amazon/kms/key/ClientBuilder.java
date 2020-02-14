package software.amazon.kms.key;

import software.amazon.cloudformation.LambdaWrapper;
import software.amazon.awssdk.services.kms.KmsClient;

public class ClientBuilder {
    public static KmsClient getClient() {
        return KmsClient.builder().httpClient(LambdaWrapper.HTTP_CLIENT).build();
    }
}
