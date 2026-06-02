package com.compendium.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class SqsConsumerHandler implements RequestHandler<SQSEvent, Void> {

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        int recordCount = event.getRecords() != null ? event.getRecords().size() : 0;
        context.getLogger().log("SQS event received — handler not yet implemented. Records: " + recordCount);
        throw new UnsupportedOperationException("SQS handler not yet implemented");
    }
}
