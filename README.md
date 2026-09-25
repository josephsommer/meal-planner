# Compendium Infra — Deploying the CloudFormation Stacks

## 0. One-time: SSM parameter for the DB master password

The Flyway connection reads the master-user password from SSM Parameter
Store at app startup (`application-aws.yml`), not from an EB environment
variable. Set it once, before `api-stack` is deployed (the EC2 role's
`ssm:GetParametersByPath` grant only covers this path):

```
aws ssm put-parameter --profile compendium --name /compendium/api/db-master-password --type SecureString --value "<master-password>"
```

Use the same password when passing `DBPassword` to `db-stack` in step 3.

## 0b. One-time: SSM parameter for the worker callback secret

The worker Lambda's callback to `/api/internal/articles/{id}/fetch-result`
is authenticated by a shared secret, read from SSM by both sides (the API
via the existing `aws-parameterstore` import, the Lambda via its own
`ssm:GetParameter` grant in `worker-stack.yml`) — never as a plaintext EB
env var. Same manual, out-of-CloudFormation convention as the DB master
password above:

```
aws ssm put-parameter --profile compendium --name /compendium/api/worker-shared-secret --type SecureString --value "$(openssl rand -base64 32)"
```

## 1. Deploy web-stack

```
aws cloudformation deploy --profile compendium --template-file infra/web-stack.yml --stack-name compendium-web --parameter-overrides BucketName=compendium-web-<ACCOUNT_ID>
```

Grab the website URL for the next step:

```
aws cloudformation describe-stacks --profile compendium --stack-name compendium-web --query "Stacks[0].Outputs[?OutputKey=='WebsiteURL'].OutputValue" --output text
```

## 2. Deploy api-stack (first pass)

```
aws cloudformation deploy --profile compendium --template-file infra/api-stack.yml --stack-name compendium-api --capabilities CAPABILITY_NAMED_IAM --parameter-overrides ArtifactBucketName=compendium-eb-artifacts-<ACCOUNT_ID> CorsAllowedOrigin=<web-stack WebsiteURL from step 1> DBEndpoint=placeholder DBUsername=<db-master-username>
```

`CAPABILITY_NAMED_IAM` is required because this template creates named IAM
resources (`compendium-api-ec2-role`, `compendium-api-ec2-profile`).

## 3. Deploy db-stack

```
aws cloudformation deploy --profile compendium --template-file infra/db-stack.yml --stack-name compendium-db --parameter-overrides DBUsername=<db-master-username> DBPassword=<master-password>
```

`DBUsername` must match what you passed to `api-stack`. `DBPassword` must
match the SSM parameter from step 0.

Grab the endpoint for the next step:

```
aws cloudformation describe-stacks --profile compendium --stack-name compendium-db --query "Stacks[0].Outputs[?OutputKey=='DBEndpoint'].OutputValue" --output text
```

## 3.5. Deploy worker-stack

CloudFormation validates the Lambda's `Code.S3Key` exists *at stack-create
time* — build and upload the jar before the first deploy, or it fails
immediately:

```
cd worker && ./mvnw --no-transfer-progress package -DskipTests
aws s3 cp target/compendium-worker-0.0.1-SNAPSHOT.jar s3://compendium-eb-artifacts-<ACCOUNT_ID>/worker/compendium-worker-0.0.1-SNAPSHOT.jar --profile compendium
```

Grab api-stack's environment URL for `InternalApiBaseUrl`:

```
aws cloudformation describe-stacks --profile compendium --stack-name compendium-api --query "Stacks[0].Outputs[?OutputKey=='EnvironmentURL'].OutputValue" --output text
```

```
aws cloudformation deploy --profile compendium --template-file infra/worker-stack.yml --stack-name compendium-worker --capabilities CAPABILITY_NAMED_IAM --parameter-overrides ArtifactObjectKey=worker/compendium-worker-0.0.1-SNAPSHOT.jar InternalApiBaseUrl=<api-stack EnvironmentURL from above>
```

`CAPABILITY_NAMED_IAM` is required for this template's named
`compendium-worker-lambda-role`. After the very first deploy, ongoing code
updates go through `.github/workflows/deploy.yml`'s `deploy-worker` job
(`aws lambda update-function-code`) — `cloudformation deploy` on this
template never redeploys new Lambda code on its own, since `Code` never
changes value once set.

Grab the queue/DLQ outputs for the next step:

```
aws cloudformation describe-stacks --profile compendium --stack-name compendium-worker --query "Stacks[0].Outputs"
```

## 4. Redeploy api-stack with the real DBEndpoint and worker-stack outputs

```
aws cloudformation deploy --profile compendium --template-file infra/api-stack.yml --stack-name compendium-api --capabilities CAPABILITY_NAMED_IAM --parameter-overrides ArtifactBucketName=compendium-eb-artifacts-<ACCOUNT_ID> CorsAllowedOrigin=<web-stack WebsiteURL> DBEndpoint=<db-stack DBEndpoint from step 3> DBUsername=<db-master-username> ArticleFetchQueueArn=<worker-stack ArticleFetchQueueArn> ArticleFetchQueueUrl=<worker-stack ArticleFetchQueueUrl> ArticleFetchDLQArn=<worker-stack ArticleFetchDLQArn> ArticleFetchDLQUrl=<worker-stack ArticleFetchDLQUrl>
```

After this, `.github/workflows/deploy.yml` needs a few GitHub Actions
secrets set from these stacks: `EB_ARTIFACT_BUCKET` from `api-stack`'s
`ArtifactBucketName` output, `EB_APPLICATION_NAME` as the literal
`compendium-api` (the `ApplicationName` hardcoded in `api-stack.yml`, not
an output), and `WEB_BUCKET` from `web-stack`'s `BucketName` output. No
additional secrets are needed for the `deploy-worker` job — it reuses
`EB_ARTIFACT_BUCKET`/`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_REGION`.
