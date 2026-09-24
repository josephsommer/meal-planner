package com.compendium.worker;

// Thrown for a fetch/extraction failure that won't succeed no matter how
// many times SQS redelivers it (e.g. a 404, or a page Readability can't
// extract anything meaningful from). Caught by SqsConsumerHandler and
// reported to the internal callback as a failure, not left for SQS's
// maxReceiveCount to burn through pointlessly.
public class PermanentFetchException extends Exception {

    public PermanentFetchException(String message) {
        super(message);
    }
}
