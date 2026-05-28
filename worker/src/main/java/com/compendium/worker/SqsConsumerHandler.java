package com.compendium.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class SqsConsumerHandler implements RequestHandler<SQSEvent, Void> {

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        // TODO: parse recipe fetch requests from SQS messages and invoke scraper
        context.getLogger().log("SQS event received — handler not yet implemented. Records: "
                + event.getRecords().size());
        return null;
    }
}
