# Assassin Game - Backend API

[![AWS SAM](https://img.shields.io/badge/AWS-SAM-FF9900?logo=amazon-aws&logoColor=white)](https://aws.amazon.com/serverless/sam/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![DynamoDB](https://img.shields.io/badge/Amazon-DynamoDB-4053D6?logo=amazon-dynamodb&logoColor=white)](https://aws.amazon.com/dynamodb/)
[![API Gateway](https://img.shields.io/badge/Amazon-API%20Gateway-FF4B4B?logo=amazon-api-gateway&logoColor=white)](https://aws.amazon.com/api-gateway/)

> A location-based elimination game backend that combines the thrill of Assassin's Creed with the real-world engagement of Pokémon Go. Built for college campuses and gaming communities.

## 🎯 Project Overview

The Assassin Game Backend API powers a mobile elimination game where players hunt targets in real-world locations using advanced geolocation, safe zones, and social gaming mechanics. This repository contains the serverless backend infrastructure built with AWS SAM.

### Related Repositories
- **📱 Mobile App**: [lag-secret-assassin-ios](https://github.com/sethdford/lag-secret-assassin-ios) - Native iOS app built with SwiftUI
- **🎨 Design System**: [lag-secret-assassin-design](https://github.com/sethdford/lag-secret-assassin-design) - Comprehensive design tokens and components

## 📖 Product Requirements & User Stories

See our comprehensive [User Stories Documentation](.memory/12-user-stories.md) which includes:

### **21 Complete User Journey Epics**
1. **Pre-Download Experience** - Creating viral awareness and FOMO
2. **App Discovery & Download** - Irresistible first impression
3. **First Launch & Onboarding** - Mind-blowing hook experience
4. **Account Creation & Profile Setup** - Assassin identity creation
5. **Game Discovery & Joining** - Finding the perfect thrill level
6. **Pre-Game Lobby & Preparation** - Strategic preparation and anticipation
7. **Game Start & Target Assignment** - Pure adrenaline moment
8. **Active Gameplay - The Hunt** - Core hunting/hunted experience
9. **Elimination Mechanics - The Kill** - Multiple thrilling elimination methods
10. **Being Eliminated - Graceful Death** - Epic rather than disappointing elimination
11. **Safe Zones - Strategic Sanctuaries** - Dynamic safe zone gameplay
12. **Social Features - Building Community** - Viral social experiences
13. **Achievements & Progression** - Addictive progression system
14. **Notifications - Staying Connected** - Perfectly timed engagement
15. **Payment & Monetization** - Fair and exciting monetization
16. **Emergency & Safety Features** - Comprehensive safety system
17. **Admin & Game Management** - Powerful game creation tools
18. **Analytics & Insights** - Deep performance insights
19. **Seasonal Events & Special Modes** - Limited-time experiences
20. **Long-term Engagement** - Features for years of play
21. **App Deletion Prevention** - Making deletion unthinkable

**Total**: **300+ detailed user stories** covering every aspect of the player experience from discovery to long-term engagement.

## 🏗️ Architecture Overview

### **Core Systems**
- **🎮 Game Management**: Complete game lifecycle with real-time state management
- **📍 Geolocation Services**: Advanced location tracking with boundary validation
- **🛡️ Safe Zone System**: Dynamic safe zones with multiple types and mechanics
- **🎯 Target Assignment**: Circular elimination chains with fair randomization
- **💰 Payment Integration**: Stripe-powered entry fees and transactions
- **🔐 Authentication**: JWT-based security with AWS Cognito integration
- **📊 Real-time Updates**: WebSocket support for live game events

### **Advanced Features**
- **🔄 Shrinking Zone System**: Battle royale-style zone mechanics with time-based progression
- **⚡ Elimination Verification**: Multiple methods (GPS, QR codes, photos, NFC)
- **📈 Analytics & Insights**: Comprehensive player and game analytics
- **🚨 Safety & Moderation**: Emergency features and behavior monitoring
- **🏆 Achievements & Leaderboards**: Progression system with campus rankings

## 🚀 Quick Start

### Prerequisites
- **Java 17+**
- **AWS CLI** configured with appropriate permissions
- **AWS SAM CLI** installed
- **Maven** for dependency management

### Setup & Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/lag-secret-assassin.git
   cd lag-secret-assassin
   ```

2. **Install dependencies**
   ```bash
   cd aws-sam-assassin
   mvn clean install
   ```

3. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

4. **Start local development**
   ```bash
   sam local start-api --port 3000
   ```

5. **Run tests**
   ```bash
   mvn test
   ```

### Project Structure

```
aws-sam-assassin/
├── src/main/java/com/assassin/
│   ├── handlers/          # Lambda function handlers
│   ├── service/           # Business logic services
│   ├── dao/               # Data access objects
│   ├── model/             # Domain models
│   ├── util/              # Utility classes
│   └── config/            # Configuration classes
├── src/test/              # Unit and integration tests
├── docs/                  # API documentation
├── template.yaml          # SAM template
└── pom.xml               # Maven configuration
```

## 📡 API Documentation

### Core Endpoints

#### Game Management
- `POST /games` - Create a new game
- `GET /games/{gameId}` - Get game details
- `POST /games/{gameId}/start` - Start game and assign targets
- `PUT /games/{gameId}/end` - End game (admin only)
- `POST /games/{gameId}/join` - Join a game
- `PUT /games/{gameId}/boundary` - Update game boundaries

#### Player & Location
- `GET /players/{playerId}` - Get player profile
- `PUT /players/{playerId}/location` - Update player location
- `GET /players/{playerId}/games` - Get player's games

#### Elimination System
- `POST /eliminations` - Report an elimination
- `PUT /eliminations/{eliminationId}/verify` - Verify elimination
- `GET /games/{gameId}/eliminations` - Get game eliminations

#### Safe Zones
- `GET /games/{gameId}/safe-zones` - Get safe zones for game
- `POST /safe-zones` - Create a safe zone
- `PUT /safe-zones/{zoneId}` - Update safe zone

#### Shrinking Zone System
- `GET /games/{gameId}/zone-state` - Get current zone state
- `GET /games/{gameId}/zone-stages` - Get zone configuration
- `GET /games/{gameId}/zone-damage` - Get player damage status

#### Payments
- `POST /games/{gameId}/pay-entry-fee` - Process entry fee payment

**Full API Documentation**: See [OpenAPI Specification](docs/openapi.yaml)

## 🛠️ Development Workflow

This project uses **TaskMaster** for development planning and execution:

### Getting Started with Tasks
```bash
# Get next priority task
task-master next

# View all tasks and progress
task-master list

# Get detailed task information
task-master show <task-id>

# Update task status
task-master set-status --id=<task-id> --status=done
```

### Current Development Focus
- **🔄 Shrinking Zone System**: Implementing time-based zone progression and damage mechanics
- **📱 Mobile Integration**: API endpoints optimized for iOS app consumption
- **⚡ Real-time Features**: WebSocket implementation for live game updates
- **🔐 Security Hardening**: Advanced anti-cheat and location verification

**Progress**: 19/58 tasks completed (33% done) with 62/123 subtasks completed (50% done)

## 🧪 Testing

### Test Categories
- **Unit Tests**: Service and utility classes
- **Integration Tests**: End-to-end API testing with test containers
- **Performance Tests**: Load testing for concurrent games
- **Security Tests**: Authentication and authorization validation

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test category
mvn test -Dtest="*Integration*"

# Run with coverage
mvn test jacoco:report
```

## 🚀 Deployment

### AWS Infrastructure
The application deploys to AWS using the following services:
- **AWS Lambda**: Serverless compute for API handlers
- **Amazon DynamoDB**: NoSQL database for game state and player data
- **Amazon API Gateway**: RESTful API management and routing
- **AWS Cognito**: User authentication and authorization
- **Amazon S3**: Static asset storage
- **AWS CloudWatch**: Monitoring and logging

### Deployment Commands
```bash
# Build and deploy to staging
sam build && sam deploy --config-env staging

# Deploy to production
sam deploy --config-env production

# Deploy with parameter overrides
sam deploy --parameter-overrides Environment=prod DatabaseTablePrefix=prod-
```

## 🔐 Security & Privacy

### Data Protection
- **End-to-end encryption** for all location data
- **PCI DSS Level 1 compliance** for payment processing
- **GDPR/CCPA compliant** with comprehensive user data controls
- **Advanced anti-cheat** systems with location spoofing detection

### Safety Features
- **Emergency protocols** with one-tap emergency contacts
- **Automatic safety check-ins** for player wellbeing
- **Incident reporting** system for inappropriate behavior
- **Admin oversight** with instant game pause capabilities

## 📊 Monitoring & Analytics

### Performance Metrics
- **Real-time location updates** with sub-3 second latency
- **Battery optimization** for 8+ hours of active gameplay
- **Scalability** supporting 1000+ concurrent players per game

### Business Metrics
- **Viral Growth**: K-factor > 1.5 target
- **Engagement**: 70%+ daily active users
- **Retention**: 90% 7-day, 70% 30-day retention targets

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Follow our [development standards](.cursor/rules/java_standards.mdc)
4. Run tests and ensure they pass
5. Commit changes (`git commit -m 'Add amazing feature'`)
6. Push to branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Development Standards
- **Java 17+** with modern language features
- **Hexagonal architecture** with clear separation of concerns
- **Comprehensive testing** with unit and integration coverage
- **Code quality** enforced with Checkstyle, SpotBugs, and PMD
- **Security first** approach with input validation and sanitization

## 📞 Support & Community

- **Issues**: [GitHub Issues](https://github.com/your-username/lag-secret-assassin/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-username/lag-secret-assassin/discussions)
- **Email**: support@assassingame.com

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Built with ❤️ for the ultimate campus gaming experience** 