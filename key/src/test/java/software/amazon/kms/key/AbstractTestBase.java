package software.amazon.kms.key;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.mockito.internal.util.collections.Sets;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final org.slf4j.Logger delegate;
  protected static final LoggerProxy logger;


  protected static final String DESCRIPTION;
  protected static final String FAMILY;
  protected static final Set<Tag> TAG_SET;

  static {
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss:SSS Z");
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");

    delegate = LoggerFactory.getLogger("testing");
    logger = new LoggerProxy();

    DESCRIPTION = "sample description";
    FAMILY = "default.aurora.5";
    TAG_SET = Sets.newSet(Tag.builder().key("key").value("value").build());
  }

  static ProxyClient<KmsClient> MOCK_PROXY(
      final AmazonWebServicesClientProxy proxy,
      final KmsClient kmsClient
  ) {
    return new ProxyClient<KmsClient>() {
      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
      ResponseT
      injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
        return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
      CompletableFuture<ResponseT>
      injectCredentialsAndInvokeV2Aync(RequestT request,
          Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
        throw new UnsupportedOperationException();
      }

      @Override
      public KmsClient client() {
        return kmsClient;
      }
    };
  }
}
