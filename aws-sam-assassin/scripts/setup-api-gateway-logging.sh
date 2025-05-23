#!/bin/bash

# Script to set up API Gateway CloudWatch Logs role
# This needs to be run once per AWS account before enabling API Gateway access logging

set -e

echo "Setting up API Gateway CloudWatch Logs role..."

# Create the IAM role for API Gateway to write to CloudWatch Logs
ROLE_NAME="apigateway-cloudwatch-logs"
TRUST_POLICY='{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "apigateway.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}'

# Check if role already exists
if aws iam get-role --role-name $ROLE_NAME >/dev/null 2>&1; then
    echo "Role $ROLE_NAME already exists"
    ROLE_ARN=$(aws iam get-role --role-name $ROLE_NAME --query 'Role.Arn' --output text)
else
    echo "Creating IAM role $ROLE_NAME..."
    ROLE_ARN=$(aws iam create-role \
        --role-name $ROLE_NAME \
        --assume-role-policy-document "$TRUST_POLICY" \
        --query 'Role.Arn' \
        --output text)
    
    echo "Attaching CloudWatch Logs policy..."
    aws iam attach-role-policy \
        --role-name $ROLE_NAME \
        --policy-arn "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
    
    # Wait for role to propagate
    echo "Waiting for IAM role to propagate..."
    sleep 10
fi

echo "Role ARN: $ROLE_ARN"

# Check if the role is already configured
CURRENT_ROLE=$(aws apigateway get-account --query 'cloudwatchRoleArn' --output text 2>/dev/null || echo "")

if [ "$CURRENT_ROLE" == "$ROLE_ARN" ]; then
    echo "API Gateway is already configured with the correct CloudWatch Logs role"
else
    # Configure API Gateway to use the role
    echo "Configuring API Gateway account to use CloudWatch Logs role..."
    
    # Retry logic for role configuration
    MAX_RETRIES=3
    RETRY_COUNT=0
    
    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
        if aws apigateway update-account \
            --patch-operations op='replace',path='/cloudwatchRoleArn',value="$ROLE_ARN" 2>/dev/null; then
            echo "Successfully configured API Gateway CloudWatch Logs role!"
            break
        else
            RETRY_COUNT=$((RETRY_COUNT + 1))
            if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
                echo "Failed to configure role. Waiting 5 seconds before retry $RETRY_COUNT/$MAX_RETRIES..."
                sleep 5
            else
                echo "Failed to configure API Gateway CloudWatch Logs role after $MAX_RETRIES attempts."
                echo ""
                echo "This might be due to IAM propagation delays. Please try running this script again in a minute."
                echo "If the problem persists, you can manually configure it in the AWS Console:"
                echo "1. Go to API Gateway service"
                echo "2. Click on 'Settings' in the left navigation"
                echo "3. Set the CloudWatch log role ARN to: $ROLE_ARN"
                exit 1
            fi
        fi
    done
fi

echo ""
echo "API Gateway CloudWatch Logs role setup complete!"
echo ""
echo "You can now deploy the SAM template with API Gateway access logging enabled:"
echo "  sam deploy --parameter-overrides EnableApiGatewayAccessLogs=true" 