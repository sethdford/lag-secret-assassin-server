# AWS SAM Assassin Game Deployment Guide

## Prerequisites

1. AWS CLI configured with appropriate credentials
2. SAM CLI installed
3. Java 17+ installed
4. Maven installed
5. Docker (for local testing with SAM)

## Building the Application

```bash
# Build the Java application
mvn clean package

# Validate the SAM template
sam validate
```

## Deployment Options

### Basic Deployment (Without API Gateway Access Logs)

This is the simplest deployment option that doesn't require any additional AWS account setup:

```bash
sam deploy --guided
```

Or if you've already run guided deployment before:

```bash
sam deploy
```

### Deployment with API Gateway Access Logs

API Gateway access logging requires a one-time account setup to create a CloudWatch Logs role.

#### Step 1: Set up API Gateway CloudWatch Logs Role (One-time per AWS Account)

```bash
./scripts/setup-api-gateway-logging.sh
```

This script will:
- Create an IAM role for API Gateway to write to CloudWatch Logs
- Configure your AWS account to use this role for API Gateway logging

#### Step 2: Deploy with Access Logs Enabled

```bash
sam deploy --parameter-overrides EnableApiGatewayAccessLogs=true
```

## Common Deployment Issues

### CloudWatch Logs Role Error

If you see this error:
```
CloudWatch Logs role ARN must be set in account settings to enable logging
```

This means you need to run the setup script first:
```bash
./scripts/setup-api-gateway-logging.sh
```

### Linter Warnings in template.yaml

The YAML linter may show warnings about CloudFormation intrinsic functions (!Ref, !Sub, etc.). These are false positives and can be ignored. We've included a `.yamllint` configuration to minimize these warnings.

For VS Code users, install the recommended extensions:
```bash
code --install-extension aws-scripting-guy.cform
code --install-extension kddejong.vscode-cfn-lint
```

## Environment-Specific Deployments

### Development
```bash
sam deploy --parameter-overrides Environment=dev
```

### Testing
```bash
sam deploy --parameter-overrides Environment=test
```

### Production
```bash
sam deploy --parameter-overrides Environment=prod DeployInVPC=true PrivateSubnet1=<subnet-id> PrivateSubnet2=<subnet-id> LambdaSecurityGroup=<sg-id>
```

## Post-Deployment Steps

1. Note the API Gateway endpoint URL from the stack outputs
2. Update your client application with the API endpoint
3. Create necessary Stripe webhook configurations if using payment features
4. Configure any monitoring/alerting as needed

## Cleanup

To delete the stack and all resources:
```bash
sam delete
```

**Warning**: This will delete all data in DynamoDB tables. Make sure to backup any important data first. 