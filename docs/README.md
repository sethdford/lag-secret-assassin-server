# LAG Secret Assassin Documentation

## Overview
Complete documentation for the LAG Secret Assassin location-based gaming platform.

## Core Documentation

### Architecture & Design
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Comprehensive system architecture using AWS serverless
- **[MULTI_MODE_ARCHITECTURE.md](MULTI_MODE_ARCHITECTURE.md)** - Multi-game mode platform design
- **[GAME_MECHANICS.md](GAME_MECHANICS.md)** - Core game mechanics and rules
- **[WEBSOCKET_API.md](WEBSOCKET_API.md)** - Real-time communication architecture

### API Documentation
- **[openapi.yaml](openapi.yaml)** - Complete OpenAPI 3.0 specification for all endpoints

### Deployment & Operations
- **[AWS_DEPLOYMENT_GUIDE.md](AWS_DEPLOYMENT_GUIDE.md)** - Step-by-step AWS deployment instructions
- **[SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md)** - Comprehensive security audit results

### Development & Planning
- **[GAME_MODES_EXPANSION_PLAN.md](GAME_MODES_EXPANSION_PLAN.md)** - Multi-game mode implementation plan
- **[REFACTORING_PLAN.md](REFACTORING_PLAN.md)** - Code refactoring and optimization plan
- **[OPTIMIZATION_SUMMARY.md](OPTIMIZATION_SUMMARY.md)** - Performance optimization recommendations

### Testing & Simulation
- **[SIMULATION_GUIDE.md](SIMULATION_GUIDE.md)** - Game simulation and testing guide
- **[SIMULATION_RESULTS.md](SIMULATION_RESULTS.md)** - Test simulation results and analysis

## Quick Start
1. Review [ARCHITECTURE.md](ARCHITECTURE.md) for system overview
2. Follow [AWS_DEPLOYMENT_GUIDE.md](AWS_DEPLOYMENT_GUIDE.md) for deployment
3. Check [SECURITY_AUDIT_REPORT.md](SECURITY_AUDIT_REPORT.md) for security status

## Development Standards
- All code follows Java 17 conventions
- AWS SAM template for infrastructure as code
- DynamoDB for data persistence
- JWT authentication via AWS Cognito
- Comprehensive test coverage (646 tests)

## Security Score: 🟢 9/10
Platform demonstrates excellent security practices with proper secrets management, authentication, and data protection.