package com.compendium.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByCreatedBy_UsernameAndDeletedFalseOrderByCreatedAtDesc(String username);

    Optional<Article> findByIdAndCreatedBy_UsernameAndDeletedFalse(Long id, String username);

    List<Article> findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore(FetchStatus fetchStatus, Instant cutoff);

    // A conditional UPDATE rather than load-then-save: SQS delivers
    // at-least-once, so two deliveries of the same message can be in flight
    // concurrently. Without the "WHERE fetch_status = 'PENDING'" guard, a
    // stale duplicate reporting failure could race a first delivery that
    // already recorded success and clobber it. The WHERE clause makes the
    // transition atomic at the database level; a row count of 0 means the
    // article was already terminal, which is a correct no-op, not an error.
    // clearAutomatically: a JPQL bulk UPDATE writes straight to the DB and
    // bypasses the persistence context, so without this, an Article instance
    // already loaded earlier in the same transaction would keep showing its
    // stale pre-update field values if read again afterward.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Article a SET a.fetchStatus = com.compendium.api.domain.FetchStatus.SUCCEEDED,
                a.title = :title, a.excerpt = :excerpt, a.content = :content, a.fetchedAt = :now
            WHERE a.id = :id AND a.fetchStatus = com.compendium.api.domain.FetchStatus.PENDING
            """)
    int recordSuccessIfPending(@Param("id") Long id, @Param("title") String title,
            @Param("excerpt") String excerpt, @Param("content") String content, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Article a SET a.fetchStatus = com.compendium.api.domain.FetchStatus.FAILED,
                a.lastError = :errorMessage, a.fetchedAt = :now
            WHERE a.id = :id AND a.fetchStatus = com.compendium.api.domain.FetchStatus.PENDING
            """)
    int recordFailureIfPending(@Param("id") Long id, @Param("errorMessage") String errorMessage, @Param("now") Instant now);

    // Same conditional-UPDATE reasoning as above, for the rescan job's
    // republish path: a load-then-save of the whole entity would write back
    // every field of a (possibly by-then-stale) in-memory Article, silently
    // reverting a result the worker's conditional callback update may have
    // just recorded concurrently. Only the one column that actually needs
    // bumping is touched, and only while still PENDING.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Article a SET a.lastEnqueuedAt = :now
            WHERE a.id = :id AND a.fetchStatus = com.compendium.api.domain.FetchStatus.PENDING
            """)
    int markEnqueuedIfPending(@Param("id") Long id, @Param("now") Instant now);
}
