package software.amazon.kms.key;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.collect.Sets;
import org.assertj.core.util.Maps;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final org.slf4j.Logger delegate;
  protected static final LoggerProxy logger;
  protected static final Map<String, String> MODEL_TAGS;
  protected static final Set<software.amazon.awssdk.services.kms.model.Tag> SDK_TAGS;

  static {
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss:SSS Z");
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    MODEL_TAGS = Maps.newHashMap("Key2", "Value2");
    SDK_TAGS = Sets.newHashSet(software.amazon.awssdk.services.kms.model.Tag.builder().tagKey("Key1").tagValue("Value1").build());

    delegate = LoggerFactory.getLogger("testing");
    logger = new LoggerProxy();
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
      injectCredentialsAndInvokeV2Async(RequestT request,
          Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
      IterableT
      injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
        return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
      injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
      injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public KmsClient client() {
        return kmsClient;
      }
    };
  }
}
