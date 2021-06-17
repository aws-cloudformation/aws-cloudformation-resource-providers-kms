package software.amazon.kms.common;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.InvalidArnException;
import software.amazon.cloudformation.LambdaWrapper;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

public class ClientBuilder {
    /**
     * Gets a KMS client.
     */
    public KmsClient getClient() {
        return KmsClient.builder()
            .httpClient(LambdaWrapper.HTTP_CLIENT)
            .build();
    }

    /**
     * Gets a KMS client for the region specified in the provided arn.
     *
     * @param arn to get the client's desired region from
     * @return a KmsClient supplier
     */
    public Supplier<KmsClient> getClientForArnRegion(final String arn) {
        try {
            final String regionStr = arn.split(":")[3];

            return getClientForRegion(regionStr);
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new CfnInvalidRequestException(InvalidArnException.create(
                "Invalid Arn Provided. Unable to determine the region of the supplied arn.", e));
        }
    }

    /**
     * Gets a KMS client for the region string specified.
     *
     * @param regionStr the region to get the client for
     * @return a KmsClient supplier
     */
    public Supplier<KmsClient> getClientForRegion(final String regionStr) {
        final Region region = KmsClient.serviceMetadata().regions().stream().filter(
            r -> r.id().equals(regionStr)).findFirst().orElseThrow(() ->
            new CfnInvalidRequestException(new IllegalArgumentException(String
                .format("'%s' is not a valid KMS Region.", regionStr))));

        return Suppliers.ofInstance(KmsClient.builder()
            .httpClient(LambdaWrapper.HTTP_CLIENT)
            .region(region)
            .build());
    }
}
