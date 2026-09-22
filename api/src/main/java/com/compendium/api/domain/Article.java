package com.compendium.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "article")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean deleted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false)
    private FetchStatus fetchStatus = FetchStatus.PENDING;

    @Column(length = 512)
    private String title;

    @Column(length = 1000)
    private String excerpt;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "last_enqueued_at")
    private Instant lastEnqueuedAt;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected Article() {}

    public Article(String url, User createdBy) {
        this.url = url;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public FetchStatus getFetchStatus() {
        return fetchStatus;
    }

    public String getTitle() {
        return title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getContent() {
        return content;
    }

    public Instant getLastEnqueuedAt() {
        return lastEnqueuedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getLastError() {
        return lastError;
    }

    // Called on initial save and again whenever the rescan job republishes a
    // stuck-PENDING article — bumps the staleness clock the rescan job's own
    // query keys off, so a message that was just re-sent isn't immediately
    // eligible to be treated as stuck again.
    public void markEnqueued() {
        this.lastEnqueuedAt = Instant.now();
    }
}
