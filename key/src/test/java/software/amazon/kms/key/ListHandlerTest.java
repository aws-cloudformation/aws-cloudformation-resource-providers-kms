package software.amazon.kms.key;

import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.GetKeyRotationStatusResponse;
import software.amazon.awssdk.services.kms.model.GetKeyPolicyResponse;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.ListKeysResponse;
import software.amazon.awssdk.services.kms.model.ListResourceTagsResponse;
import software.amazon.awssdk.services.kms.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    private ListHandler handler;
    private static final String DESCRIPTION = "DESCRIPTION";
    private static final Boolean ENABLED = true;
    private static final Boolean ENABLE_KEY_ROTATION = true;
    private static final String KEY_ID = "samplearn";
    private static final Boolean KEY_ROTATION = true;
    private static final String KEY_USAGE = KeyUsageType.ENCRYPT_DECRYPT.toString();
    private static final KeyMetadata KEY_METADATA = KeyMetadata.builder()
            .keyId(KEY_ID)
            .description(DESCRIPTION)
            .enabled(ENABLED)
            .keyUsage(KEY_USAGE).build();
    private static final String NEXT_TOKEN = "4b90a7e4-b790-456b";
    private static final String POLICY = "{\"foo\": \"bar\"}";
    private static final String TAG_KEY = "key";
    private static final String TAG_VALUE = "value";

    @BeforeEach
    public void setup() {
        handler = new ListHandler();
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_SimpleSuccess() {

        final ListKeysResponse listKeysResponse = ListKeysResponse.builder()
                .keys(Collections.singletonList(KeyListEntry.builder().keyId(KEY_ID).build()))
                .nextMarker(NEXT_TOKEN).build();


        doReturn(listKeysResponse)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());
        final ResourceModel model = ResourceModel.builder().build();

        final ResourceModel expectedModel = ResourceModel.builder().keyId(KEY_ID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, request, null, logger);

        verify(proxy).injectCredentialsAndInvokeV2(eq(Translator.listKeysRequest(null)), any());

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getResourceModels()).containsExactly(expectedModel);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getNextToken()).isEqualTo(NEXT_TOKEN);


    }
}
