package com.compendium.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Real collaborators (ArticleExtractor's jsoup fetch, InternalApiClient's
// HTTP POST, the cold-start SSM read) are mocked out here — this class
// covers only the batch-item-failure/retry-signal wiring in
// SqsConsumerHandler itself.
class SqsConsumerHandlerTest {

    @Mock
    private ArticleExtractor extractor;

    @Mock
    private InternalApiClient apiClient;

    @Mock
    private Context context;

    private SqsConsumerHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(org.mockito.Mockito.mock(LambdaLogger.class));
        handler = new SqsConsumerHandler(extractor, apiClient);
    }

    @Test
    void handleRequest_reportsSuccessAndHasNoBatchFailure() throws Exception {
        SQSEvent event = eventWithOneMessage("msg-1", "{\"articleId\":1,\"url\":\"https://example.com/a\"}");
        ExtractedArticle extracted = new ExtractedArticle("Title", "Excerpt", "Content");
        when(extractor.fetchAndExtract("https://example.com/a")).thenReturn(extracted);

        SQSBatchResponse response = handler.handleRequest(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        verify(apiClient).reportSuccess(1L, extracted);
    }

    @Test
    void handleRequest_reportsFailureAndHasNoBatchFailureForAPermanentError() throws Exception {
        SQSEvent event = eventWithOneMessage("msg-1", "{\"articleId\":1,\"url\":\"https://example.com/a\"}");
        when(extractor.fetchAndExtract("https://example.com/a"))
                .thenThrow(new PermanentFetchException("404"));

        SQSBatchResponse response = handler.handleRequest(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        verify(apiClient).reportFailure(1L, "404");
    }

    @Test
    void handleRequest_addsABatchFailureForARetryableError() throws Exception {
        SQSEvent event = eventWithOneMessage("msg-1", "{\"articleId\":1,\"url\":\"https://example.com/a\"}");
        when(extractor.fetchAndExtract("https://example.com/a"))
                .thenThrow(new RetryableFetchException("timeout"));

        SQSBatchResponse response = handler.handleRequest(event, context);

        assertThat(response.getBatchItemFailures())
                .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .containsExactly("msg-1");
        verifyNoInteractions(apiClient);
    }

    @Test
    void handleRequest_addsABatchFailureWhenTheSuccessCallbackItselfFails() throws Exception {
        SQSEvent event = eventWithOneMessage("msg-1", "{\"articleId\":1,\"url\":\"https://example.com/a\"}");
        ExtractedArticle extracted = new ExtractedArticle("Title", "Excerpt", "Content");
        when(extractor.fetchAndExtract("https://example.com/a")).thenReturn(extracted);
        doThrow(new java.io.IOException("API unreachable")).when(apiClient).reportSuccess(eq(1L), any());

        SQSBatchResponse response = handler.handleRequest(event, context);

        assertThat(response.getBatchItemFailures())
                .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .containsExactly("msg-1");
    }

    @Test
    void handleRequest_dropsAnUnparseableMessageWithoutRetryingOrCallingTheExtractor() {
        SQSEvent event = eventWithOneMessage("msg-1", "not valid json");

        SQSBatchResponse response = handler.handleRequest(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        verifyNoInteractions(extractor, apiClient);
    }

    private static SQSEvent eventWithOneMessage(String messageId, String body) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId(messageId);
        message.setBody(body);
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));
        return event;
    }
}
