# Memory Bank - LAG Secret Assassin

## Project Overview
Location-based gaming platform with multi-mode support, built on AWS serverless architecture.

## Current Status
- **Core Platform**: Stable with 646 passing tests
- **Security**: 9/10 audit score - production ready
- **Architecture**: Multi-mode framework designed and documented
- **Documentation**: Consolidated in docs/ directory

## Key Technical Decisions
1. **AWS Serverless**: Lambda + DynamoDB for scalability
2. **Java 17**: Runtime for all Lambda functions
3. **Abstract GameMode**: Interface for unlimited game types
4. **Real-time**: WebSocket for Uber-like tracking experience

## Next Steps
1. Implement GameMode framework (Task #68)
2. Deploy Capture The Flag and Hide & Seek modes
3. Add Netflix-like game discovery service
4. Configure AWS deployment pipeline

## Memory Files
- `.memory/`: Project knowledge base
- `.taskmaster/`: Task management system
- `memory/`: Runtime memory and sessions
- `coordination/`: Multi-agent coordination