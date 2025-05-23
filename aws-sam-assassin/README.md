## Deployment

### Quick Start

The easiest way to deploy is using our deployment script:

```bash
./scripts/deploy.sh
```

This script will:
- Check prerequisites
- Build the application
- Validate the template
- Deploy the stack (with API Gateway access logs disabled by default)

### Deployment with API Gateway Access Logs

To enable API Gateway access logs (recommended for production):

```bash
./scripts/deploy.sh --enable-logs
```

The script will automatically check if the required CloudWatch Logs role is configured and offer to set it up if needed.

### Manual Deployment

For more control over the deployment process:

```bash
# Build the application
mvn clean package

# Deploy with SAM
sam deploy --guided
```

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed deployment instructions and troubleshooting. 