#!/bin/bash

# Deployment script for AWS SAM Assassin Game
# This script handles common deployment scenarios and provides helpful guidance

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "🎯 AWS SAM Assassin Game Deployment Script"
echo "=========================================="

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

if ! command -v aws &> /dev/null; then
    echo -e "${RED}❌ AWS CLI not found. Please install it first.${NC}"
    exit 1
fi

if ! command -v sam &> /dev/null; then
    echo -e "${RED}❌ SAM CLI not found. Please install it first.${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found. Please install it first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All prerequisites found${NC}"

# Build the application
echo -e "\n${YELLOW}Building the application...${NC}"
mvn clean package -DskipTests
echo -e "${GREEN}✅ Build complete${NC}"

# Validate the template
echo -e "\n${YELLOW}Validating SAM template...${NC}"
sam validate
echo -e "${GREEN}✅ Template is valid${NC}"

# Check if this is the first deployment
if [ ! -f "samconfig.toml" ]; then
    echo -e "\n${YELLOW}This appears to be your first deployment.${NC}"
    echo "We'll deploy without API Gateway access logs to avoid setup requirements."
    echo -e "\n${GREEN}Starting guided deployment...${NC}"
    sam deploy --guided --parameter-overrides EnableApiGatewayAccessLogs=false
else
    # Parse command line arguments
    ENABLE_LOGS=false
    EXTRA_PARAMS=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --enable-logs)
                ENABLE_LOGS=true
                shift
                ;;
            --parameter-overrides)
                EXTRA_PARAMS="$2"
                shift 2
                ;;
            *)
                shift
                ;;
        esac
    done
    
    if [ "$ENABLE_LOGS" = true ]; then
        echo -e "\n${YELLOW}API Gateway access logs requested.${NC}"
        echo "Checking if CloudWatch Logs role is configured..."
        
        # Check if the role is configured
        if ! aws apigateway get-account --query 'cloudwatchRoleArn' --output text | grep -q "arn:aws:iam"; then
            echo -e "${RED}❌ CloudWatch Logs role not configured.${NC}"
            echo -e "\nWould you like to set it up now? (y/n)"
            read -r response
            
            if [[ "$response" == "y" || "$response" == "Y" ]]; then
                echo -e "\n${YELLOW}Setting up CloudWatch Logs role...${NC}"
                ./scripts/setup-api-gateway-logging.sh
                echo -e "${GREEN}✅ Role configured${NC}"
            else
                echo -e "${YELLOW}Deploying without access logs...${NC}"
                ENABLE_LOGS=false
            fi
        else
            echo -e "${GREEN}✅ CloudWatch Logs role already configured${NC}"
        fi
    fi
    
    # Build the deployment command
    DEPLOY_CMD="sam deploy"
    
    if [ "$ENABLE_LOGS" = true ]; then
        DEPLOY_CMD="$DEPLOY_CMD --parameter-overrides EnableApiGatewayAccessLogs=true"
    else
        DEPLOY_CMD="$DEPLOY_CMD --parameter-overrides EnableApiGatewayAccessLogs=false"
    fi
    
    if [ ! -z "$EXTRA_PARAMS" ]; then
        DEPLOY_CMD="$DEPLOY_CMD $EXTRA_PARAMS"
    fi
    
    echo -e "\n${GREEN}Deploying stack...${NC}"
    echo "Command: $DEPLOY_CMD"
    eval $DEPLOY_CMD
fi

echo -e "\n${GREEN}🎉 Deployment complete!${NC}"
echo -e "\nNext steps:"
echo "1. Check the stack outputs for your API endpoint URL"
echo "2. Update your frontend with the new API endpoint"
echo "3. Configure Stripe webhooks if using payment features"
echo -e "\nTo enable API Gateway access logs in the future, run:"
echo "  ./scripts/deploy.sh --enable-logs" 