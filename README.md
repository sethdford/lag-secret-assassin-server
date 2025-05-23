# Assassin Game

A real-time location-based game platform built with AWS Serverless and Next.js.

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Java](https://img.shields.io/badge/java-17+-orange.svg)
![Node.js](https://img.shields.io/badge/node.js-18+-green.svg)

## Overview

The Assassin Game is a modern implementation of the classic campus game where players are assigned targets to "assassinate" (tag) while avoiding being tagged themselves. This digital version uses geolocation, real-time notifications, secure authentication, and advanced content moderation to create an engaging and fair gameplay experience.

## 🎯 Features

### Core Gameplay
* **User Authentication**: Secure login and registration system using Amazon Cognito
* **Real-time Location Tracking**: Report kills with geolocation verification
* **Target Assignment System**: Dynamic target assignment with safe zone protection
* **Kill Verification**: Photo-based proof system with AI content moderation

### Safety & Moderation
* **Content Moderation**: AWS Rekognition and Comprehend integration for automatic content filtering
* **Emergency Controls**: Game pause functionality for safety situations
* **Reporting System**: Comprehensive reporting tools for inappropriate behavior
* **Safe Zones**: Configurable areas where eliminations are not allowed

### Management & Analytics
* **Admin Dashboard**: Comprehensive game management tools for organizers
* **Player Statistics**: Detailed performance metrics and leaderboards
* **Real-time Updates**: Live kill feed and game state notifications
* **Payment Integration**: Stripe integration for entry fees and premium features

## 🏗️ Architecture

### Backend (AWS Serverless)

The backend is built using AWS Serverless architecture with Java 17:

* **API Gateway**: REST API endpoints for game operations
* **Lambda Functions**: Java-based serverless functions for business logic
* **DynamoDB**: NoSQL database for storing game state and player data
* **Cognito**: User authentication and authorization
* **Rekognition**: AI-powered image content moderation
* **Comprehend**: AI-powered text analysis and toxicity detection
* **CloudWatch**: Monitoring, logging, and alerting
* **S3**: Static file storage for user uploads

### Frontend

The frontend is built with modern web technologies:

* **Next.js**: React framework for the web application
* **TypeScript**: Type-safe code development
* **Tailwind CSS**: Utility-first CSS framework
* **SWR**: Data fetching and caching
* **Jest/React Testing Library**: Component testing
* **Cypress**: End-to-end testing

## 🚀 Getting Started

### Prerequisites

* Node.js (v18+)
* Java 17+
* Maven 3.8+
* AWS CLI configured with appropriate credentials
* AWS SAM CLI

### Backend Setup

1. **Navigate to the AWS SAM directory:**
   ```bash
   cd aws-sam-assassin
   ```

2. **Install dependencies and compile:**
   ```bash
   mvn clean compile
   ```

3. **Run tests:**
   ```bash
   mvn test
   ```

4. **Build the SAM application:**
   ```bash
   sam build
   ```

5. **Deploy to AWS:**
   ```bash
   sam deploy --guided
   ```

6. **Note the API endpoint URL** for frontend configuration.

### Frontend Setup

1. **Navigate to the web directory:**
   ```bash
   cd web
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Configure environment variables:**
   ```bash
   cp .env.example .env.local
   ```
   Update `.env.local` with your API endpoint and other configuration.

4. **Start the development server:**
   ```bash
   npm run dev
   ```

5. **Access the application** at `http://localhost:3000`

## 🧪 Testing

### Backend Testing

Our backend has comprehensive test coverage including unit tests, integration tests, and moderation system tests:

```bash
cd aws-sam-assassin

# Run all tests
mvn test

# Run specific test suites
mvn test -Dtest=ModerationConfigTest,ModerationCacheServiceTest
mvn test -Dtest="*Moderation*"

# Build without tests
mvn package -DskipTests
```

### Frontend Testing

```bash
cd web

# Run unit tests
npm test

# Run Cypress E2E tests
npm run cypress
```

## 📊 Current Development Status

- **✅ Core Game Mechanics**: User management, target assignment, kill reporting
- **✅ Authentication & Authorization**: Cognito integration with role-based access
- **✅ Content Moderation**: AI-powered image and text moderation using AWS services
- **✅ Payment Processing**: Stripe integration for entry fees
- **✅ Safe Zone Management**: Dynamic safe zone creation and protection
- **🔄 Emergency Systems**: Currently implementing game pause functionality
- **📋 Upcoming**: Social features, subscription tiers, analytics system

## 🔐 Security Features

* **AI Content Moderation**: Automatic filtering of inappropriate images and text
* **Location Verification**: Anti-cheat measures for location spoofing
* **Safe Zone Protection**: Configurable areas where eliminations are prohibited
* **Emergency Controls**: Game organizers can pause games immediately
* **Comprehensive Reporting**: Multi-level escalation for safety concerns

## 🛠️ Technology Stack

### Backend
- **Java 17** with Maven
- **AWS Lambda** (Serverless functions)
- **AWS DynamoDB** (Database)
- **AWS API Gateway** (REST API)
- **AWS Cognito** (Authentication)
- **AWS Rekognition** (Image moderation)
- **AWS Comprehend** (Text analysis)
- **Stripe** (Payment processing)

### Frontend
- **Next.js 13+** with TypeScript
- **Tailwind CSS** for styling
- **SWR** for data fetching
- **React Hook Form** for form management

### DevOps & Testing
- **AWS SAM** for infrastructure as code
- **JUnit 5** for unit testing
- **Mockito** for mocking
- **TestContainers** for integration testing
- **Jest** and **Cypress** for frontend testing

## 📈 Deployment

### Backend Deployment

```bash
cd aws-sam-assassin
sam deploy --stack-name assassin-game-prod --region us-east-1
```

### Frontend Deployment

```bash
cd web
npm run build
# Deploy to your hosting provider (Vercel, Netlify, etc.)
```

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

* Follow Java coding standards and use meaningful variable names
* Write comprehensive tests for new features
* Update documentation for any API changes
* Ensure all tests pass before submitting PRs

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

* The LAG team for the original concept
* AWS for providing the serverless infrastructure
* All contributors who have helped with the development
* The open-source community for the amazing tools and libraries

## 📞 Support

For questions, issues, or contributions, please:
- Open an issue on GitHub
- Check the documentation in the `/docs` folder
- Review the API specification at `/docs/openapi.yaml`

---

**Last Updated**: December 2024  
**Current Version**: Development Phase  
**Project Status**: Active Development 