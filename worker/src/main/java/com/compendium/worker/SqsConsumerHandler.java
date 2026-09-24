package com.compendium.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SqsConsumerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArticleExtractor extractor;
    private final InternalApiClient apiClient;

    // Lambda itself only ever calls this one (via reflection, by class
    // name) — real collaborators, including the cold-start SSM read.
    public SqsConsumerHandler() {
        this(new ArticleExtractor(), buildApiClient());
    }

    // Substitutable collaborators for tests, so a unit test doesn't have to
    // spin up real jsoup connections or a real SSM client just to exercise
    // the batch-failure/retry-signal wiring below.
    SqsConsumerHandler(ArticleExtractor extractor, InternalApiClient apiClient) {
        this.extractor = extractor;
        this.apiClient = apiClient;
    }

    private static InternalApiClient buildApiClient() {
        String baseUrl = System.getenv("INTERNAL_API_BASE_URL");
        String secretParameterName = System.getenv("INTERNAL_SECRET_PARAMETER_NAME");
        String secret = SecretLoader.loadWorkerSecret(SsmClient.create(), secretParameterName);
        return new InternalApiClient(baseUrl, secret);
    }

    // Processes each record independently and reports partial batch
    // failures (rather than letting one bad message fail the whole batch)
    // — requires FunctionResponseTypes: [ReportBatchItemFailures] on the
    // event source mapping (see infra/worker-stack.yml) or SQS ignores this
    // and redelivers the entire batch regardless.
    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processMessage(message, context);
            } catch (RetryableFailureSignal e) {
                context.getLogger().log("Retryable failure for message " + message.getMessageId() + ": " + e.getMessage());
                failures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }

        return new SQSBatchResponse(failures);
    }

    private void processMessage(SQSEvent.SQSMessage message, Context context) throws RetryableFailureSignal {
        ArticleFetchMessage payload;
        try {
            payload = objectMapper.readValue(message.getBody(), ArticleFetchMessage.class);
        } catch (JsonProcessingException e) {
            // A malformed body will never parse no matter how many times SQS
            // redelivers it, and there's no articleId to report a failure
            // against either — logged and dropped rather than retried forever.
            context.getLogger().log("Unparseable message " + message.getMessageId() + ": " + e.getMessage(), LogLevel.ERROR);
            return;
        }

        ExtractedArticle article;
        try {
            article = extractor.fetchAndExtract(payload.url());
        } catch (PermanentFetchException e) {
            reportFailureOrRetry(payload.articleId(), e.getMessage());
            return;
        } catch (RetryableFetchException e) {
            throw new RetryableFailureSignal(e.getMessage(), e);
        } catch (RuntimeException e) {
            // Something unclassified (e.g. an internal Readability4J error,
            // or Jsoup itself rejecting a malformed URL) rather than one of
            // ArticleExtractor's own two classified outcomes above. Treated
            // as retryable rather than silently losing the message, but
            // logged distinctly below from an actual callback failure —
            // this exception never reached apiClient at all.
            throw new RetryableFailureSignal("Unexpected extraction failure for article " + payload.articleId(), e);
        }

        reportSuccessOrRetry(payload.articleId(), article);
    }

    private void reportSuccessOrRetry(Long articleId, ExtractedArticle article) throws RetryableFailureSignal {
        try {
            apiClient.reportSuccess(articleId, article);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableFailureSignal("Callback interrupted for article " + articleId, e);
        } catch (IOException e) {
            // Nothing was persisted despite a successful extraction — worth
            // redelivering rather than silently discarding a good result.
            throw new RetryableFailureSignal("Callback failed for article " + articleId, e);
        }
    }

    private void reportFailureOrRetry(Long articleId, String errorMessage) throws RetryableFailureSignal {
        try {
            apiClient.reportFailure(articleId, errorMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableFailureSignal("Callback interrupted for article " + articleId, e);
        } catch (IOException e) {
            throw new RetryableFailureSignal("Callback failed for article " + articleId, e);
        }
    }

    // Internal-only signal for "this message needs SQS redelivery" — never
    // crosses a method boundary outside this class.
    private static class RetryableFailureSignal extends Exception {
        RetryableFailureSignal(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
