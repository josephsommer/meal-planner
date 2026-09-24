package com.compendium.worker;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

// Reads the worker-callback shared secret from SSM at cold start, via this
// Lambda's own narrowly-scoped ssm:GetParameter IAM grant (see
// infra/worker-stack.yml) — never baked into a plaintext env var at deploy
// time (decision 5 in the async-extraction spec).
public class SecretLoader {

    public static String loadWorkerSecret(SsmClient ssmClient, String parameterName) {
        return ssmClient.getParameter(GetParameterRequest.builder()
                        .name(parameterName)
                        .withDecryption(true)
                        .build())
                .parameter()
                .value();
    }
}
