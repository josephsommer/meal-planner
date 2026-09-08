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

## 4. Redeploy api-stack with the real DBEndpoint

```
aws cloudformation deploy --profile compendium --template-file infra/api-stack.yml --stack-name compendium-api --capabilities CAPABILITY_NAMED_IAM --parameter-overrides ArtifactBucketName=compendium-eb-artifacts-<ACCOUNT_ID> CorsAllowedOrigin=<web-stack WebsiteURL> DBEndpoint=<db-stack DBEndpoint from step 3> DBUsername=<db-master-username>
```

After this, `.github/workflows/deploy.yml` needs a few GitHub Actions
secrets set from these stacks: `EB_ARTIFACT_BUCKET` from `api-stack`'s
`ArtifactBucketName` output, `EB_APPLICATION_NAME` as the literal
`compendium-api` (the `ApplicationName` hardcoded in `api-stack.yml`, not
an output), and `WEB_BUCKET` from `web-stack`'s `BucketName` output.
