package com.compendium.worker;

// Thrown for a fetch failure worth another SQS delivery attempt (e.g. a
// connection timeout, or a 5xx from the target site). SqsConsumerHandler
// lets this propagate into a batch item failure so SQS redelivers the
// message, up to the queue's maxReceiveCount.
public class RetryableFetchException extends Exception {

    public RetryableFetchException(String message) {
        super(message);
    }

    public RetryableFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
