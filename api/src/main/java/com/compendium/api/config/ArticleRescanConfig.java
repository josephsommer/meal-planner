package com.compendium.api.config;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.FetchStatus;
import com.compendium.api.service.ArticleService;
import com.compendium.api.service.ArticleService.ArticleFetchMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Two safety nets in one scheduled pass, both reusing infra that already
// exists for another reason rather than adding a CloudWatch alarm or a
// second Lambda:
//  1. Republish articles stuck PENDING too long (a lost/never-sent SQS
//     message) — see ArticleService.saveArticle's own publish-failure comment.
//  2. Drain the DLQ, flipping any still-PENDING article whose message
//     exhausted all of SQS's redrive attempts to FAILED, so it doesn't sit
//     invisibly PENDING forever with nothing else able to notice.
// Unconditional bean (not @Profile("aws")-scoped like RdsIamDataSourceConfig's
// token refresh) — in local/test profiles with no real queue configured this
// just finds nothing and no-ops, which is cheap enough not to bother gating.
@Component
public class ArticleRescanConfig {

    private static final Logger log = LoggerFactory.getLogger(ArticleRescanConfig.class);
    private static final Duration STUCK_PENDING_THRESHOLD = Duration.ofMinutes(15);

    private final ArticleRepository articleRepository;
    private final ArticleService articleService;
    private final SqsTemplate sqsTemplate;
    private final String fetchQueueUrl;
    private final String dlqUrl;

    public ArticleRescanConfig(ArticleRepository articleRepository, ArticleService articleService,
            SqsTemplate sqsTemplate,
            @Value("${app.article-fetch-queue-url}") String fetchQueueUrl,
            @Value("${app.article-fetch-dlq-url}") String dlqUrl) {
        this.articleRepository = articleRepository;
        this.articleService = articleService;
        this.sqsTemplate = sqsTemplate;
        this.fetchQueueUrl = fetchQueueUrl;
        this.dlqUrl = dlqUrl;
    }

    // Tighter than RdsIamDataSourceConfig's 10-minute token refresh — this is
    // a correctness safety net for a user-visible feature, not just beating a
    // credential's expiry with margin. Both jobs share Spring's default
    // single-threaded scheduler; neither is long-running enough for that to
    // matter at this scale.
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void rescan() {
        republishStuckPending();
        drainDeadLetterQueue();
    }

    private void republishStuckPending() {
        Instant cutoff = Instant.now().minus(STUCK_PENDING_THRESHOLD);
        List<Article> stuck = articleRepository
                .findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore(FetchStatus.PENDING, cutoff);

        for (Article article : stuck) {
            log.info("Republishing stuck-PENDING article {} to fetch queue", article.getId());
            sqsTemplate.send(fetchQueueUrl, new ArticleFetchMessage(article.getId(), article.getUrl()));
            // Bumped so this same article isn't picked up again next run
            // before the new message gets a fair chance to be processed.
            article.markEnqueued();
            articleRepository.save(article);
        }
    }

    // The SqsTemplate bean's default acknowledgement mode deletes each
    // message as part of receiveMany() itself, before this loop's DB update
    // runs. Accepted simplification: on the rare chance the update below
    // fails right after a message is received, that DLQ entry is gone and
    // the article stays PENDING rather than FAILED. Guarding against that
    // would mean standing up a second, manually-acknowledged SqsTemplate
    // bean just for this path — not worth the complexity for a failure mode
    // this unlikely in a portfolio project.
    private void drainDeadLetterQueue() {
        // Blank in local/test profiles where no real queue is configured —
        // receiveMany() against a blank queue name/URL throws, which would
        // otherwise show up as a scheduled-task error on every context
        // startup for an entirely expected "nothing to drain here" state.
        if (dlqUrl.isBlank()) {
            return;
        }

        Collection<Message<ArticleFetchMessage>> messages = sqsTemplate.receiveMany(dlqUrl, ArticleFetchMessage.class);

        for (Message<ArticleFetchMessage> message : messages) {
            ArticleFetchMessage payload = message.getPayload();
            log.info("Draining DLQ message for article {}, marking FAILED if still PENDING", payload.articleId());
            articleService.recordFetchFailure(payload.articleId(), "Exhausted SQS delivery attempts");
        }
    }
}
