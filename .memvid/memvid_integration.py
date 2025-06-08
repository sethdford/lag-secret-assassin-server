#!/usr/bin/env python3
"""
Enhanced Memvid Integration for Assassin Game Project
====================================================

This script provides comprehensive Memvid integration for the Assassin Game project,
including memory bank integration, chat history processing, and intelligent documentation search.

Features:
- Build knowledge base from memory bank files (.memory/)
- Process chat history and conversation context
- Integrate with Cursor rules and project documentation
- Semantic search for game rules, architecture, and development patterns
- REST API server for Java backend integration
- Video-based memory storage for efficient retrieval
- Interactive chat interface for project assistance

Usage:
    python3 memvid_integration.py --build-memory-bank
    python3 memvid_integration.py --build-chat-history
    python3 memvid_integration.py --build-complete
    python3 memvid_integration.py --start-server
    python3 memvid_integration.py --interactive
    python3 memvid_integration.py --query "How does the shrinking zone work?"
"""

import os
import sys
import argparse
import json
import glob
from pathlib import Path
from typing import List, Dict, Any, Optional, Tuple
import logging
from datetime import datetime
import re

# Add the virtual environment to the path
sys.path.insert(0, str(Path(__file__).parent / "memvid-env" / "lib" / "python3.13" / "site-packages"))

try:
    from memvid import MemvidEncoder, MemvidRetriever, MemvidChat, quick_chat, chat_with_memory
    from flask import Flask, request, jsonify
    from flask_cors import CORS
except ImportError as e:
    print(f"Error importing required libraries: {e}")
    print("Please ensure you have activated the memvid-env virtual environment:")
    print("source memvid-env/bin/activate")
    print("And installed required packages:")
    print("pip install memvid PyPDF2 flask flask-cors")
    sys.exit(1)

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class AssassinGameMemvidIntegration:
    """
    Enhanced Memvid integration for the Assassin Game project.
    Provides intelligent search across memory bank, chat history, rules, and documentation.
    """
    
    def __init__(self, base_dir: str = ".memvid"):
        self.base_dir = Path(base_dir)
        self.base_dir.mkdir(exist_ok=True)
        
        # Memory bank integration
        self.memory_bank_video = self.base_dir / "memory_bank.mp4"
        self.memory_bank_index = self.base_dir / "memory_bank_index.json"
        
        # Chat history integration
        self.chat_history_video = self.base_dir / "chat_history.mp4"
        self.chat_history_index = self.base_dir / "chat_history_index.json"
        
        # Complete knowledge base (combined)
        self.complete_video = self.base_dir / "complete_knowledge.mp4"
        self.complete_index = self.base_dir / "complete_knowledge_index.json"
        
        # Rules and documentation
        self.rules_video = self.base_dir / "rules_docs.mp4"
        self.rules_index = self.base_dir / "rules_docs_index.json"
        
        self.encoder = None
        self.retriever = None
        self.chat = None
        
    def build_memory_bank_knowledge(self) -> bool:
        """
        Build knowledge base from .memory/ directory files.
        """
        logger.info("Building Memory Bank knowledge base...")
        
        self.encoder = MemvidEncoder()
        
        memory_dir = Path(".memory")
        if not memory_dir.exists():
            logger.warning("No .memory directory found")
            return False
        
        memory_files_processed = 0
        
        # Process all memory bank files
        for memory_file in sorted(memory_dir.glob("*.md")):
            try:
                with open(memory_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    
                # Add content with source information
                formatted_content = f"MEMORY BANK FILE: {memory_file.name}\nCategory: {self._categorize_memory_file(memory_file.name)}\nSource: {memory_file}\n\n{content}"
                
                self.encoder.add_text(
                    formatted_content,
                    chunk_size=512,
                    overlap=50
                )
                
                memory_files_processed += 1
                logger.info(f"Processed memory file: {memory_file.name}")
                
            except Exception as e:
                logger.warning(f"Could not process {memory_file}: {e}")
        
        if memory_files_processed == 0:
            logger.warning("No memory bank files were processed")
            return False
        
        # Build the video knowledge base
        try:
            self.encoder.build_video(
                str(self.memory_bank_video),
                str(self.memory_bank_index),
                codec='mp4v',
                show_progress=True
            )
            logger.info(f"Memory Bank knowledge base built: {memory_files_processed} files processed")
            return True
        except Exception as e:
            logger.error(f"Failed to build Memory Bank knowledge base: {e}")
            return False
    
    def build_chat_history_knowledge(self) -> bool:
        """
        Build knowledge base from chat history and conversation logs.
        """
        logger.info("Building Chat History knowledge base...")
        
        self.encoder = MemvidEncoder()
        
        # Look for various chat history sources
        chat_sources = [
            ".cursor/chat_history.json",
            ".cursor/conversations/*.json",
            "chat_logs/*.txt",
            "conversation_history.md",
            ".specstory"  # If this contains conversation history
        ]
        
        chat_content_processed = 0
        
        # Process chat history files
        for pattern in chat_sources:
            for chat_file in glob.glob(pattern, recursive=True):
                try:
                    chat_path = Path(chat_file)
                    
                    if chat_path.suffix == '.json':
                        # Process JSON chat history
                        with open(chat_path, 'r', encoding='utf-8') as f:
                            chat_data = json.load(f)
                            content = self._process_json_chat_history(chat_data)
                    else:
                        # Process text-based chat history
                        with open(chat_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                    
                    if content.strip():
                        formatted_content = f"CHAT HISTORY: {chat_path.name}\nSource: {chat_path}\nType: chat_history\n\n{content}"
                        
                        self.encoder.add_text(
                            formatted_content,
                            chunk_size=512,
                            overlap=50
                        )
                        
                        chat_content_processed += 1
                        logger.info(f"Processed chat history: {chat_path.name}")
                        
                except Exception as e:
                    logger.warning(f"Could not process chat file {chat_file}: {e}")
        
        # Add simulated conversation context based on the current project state
        project_context = self._generate_project_context()
        if project_context:
            formatted_content = f"PROJECT CONTEXT AND CONVERSATION SUMMARY\nSource: generated_context\nType: project_context\n\n{project_context}"
            
            self.encoder.add_text(
                formatted_content,
                chunk_size=512,
                overlap=50
            )
            chat_content_processed += 1
        
        if chat_content_processed == 0:
            logger.warning("No chat history content was processed")
            return False
        
        # Build the video knowledge base
        try:
            self.encoder.build_video(
                str(self.chat_history_video),
                str(self.chat_history_index),
                codec='mp4v',
                show_progress=True
            )
            logger.info(f"Chat History knowledge base built: {chat_content_processed} sources processed")
            return True
        except Exception as e:
            logger.error(f"Failed to build Chat History knowledge base: {e}")
            return False
    
    def build_rules_and_docs_knowledge(self) -> bool:
        """
        Build knowledge base from Cursor rules and project documentation.
        """
        logger.info("Building Rules and Documentation knowledge base...")
        
        self.encoder = MemvidEncoder()
        
        # Process Cursor rules
        rules_dir = Path(".cursor/rules")
        docs_processed = 0
        
        if rules_dir.exists():
            for rule_file in rules_dir.glob("**/*.mdc"):
                try:
                    with open(rule_file, 'r', encoding='utf-8') as f:
                        content = f.read()
                        
                    formatted_content = f"CURSOR RULE: {rule_file.name}\nSource: {rule_file}\nType: cursor_rule\nCategory: development_standards\n\n{content}"
                    
                    self.encoder.add_text(
                        formatted_content,
                        chunk_size=512,
                        overlap=50
                    )
                    
                    docs_processed += 1
                    logger.info(f"Processed rule file: {rule_file.name}")
                    
                except Exception as e:
                    logger.warning(f"Could not process rule file {rule_file}: {e}")
        
        # Process project documentation
        doc_patterns = [
            "README.md",
            "docs/**/*.md",
            "*.md",
            "pom.xml",
            "template.yaml",
            ".cursorrules"
        ]
        
        for pattern in doc_patterns:
            for doc_file in glob.glob(pattern, recursive=True):
                try:
                    doc_path = Path(doc_file)
                    
                    # Skip memory bank files (processed separately)
                    if ".memory" in str(doc_path):
                        continue
                    
                    with open(doc_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    formatted_content = f"DOCUMENTATION: {doc_path.name}\nSource: {doc_path}\nType: documentation\nCategory: {self._categorize_doc_file(doc_path.name)}\n\n{content}"
                    
                    self.encoder.add_text(
                        formatted_content,
                        chunk_size=512,
                        overlap=50
                    )
                    
                    docs_processed += 1
                    logger.info(f"Processed documentation: {doc_path.name}")
                    
                except Exception as e:
                    logger.warning(f"Could not process documentation {doc_file}: {e}")
        
        if docs_processed == 0:
            logger.warning("No rules or documentation files were processed")
            return False
        
        # Build the video knowledge base
        try:
            self.encoder.build_video(
                str(self.rules_video),
                str(self.rules_index),
                codec='mp4v',
                show_progress=True
            )
            logger.info(f"Rules and Documentation knowledge base built: {docs_processed} files processed")
            return True
        except Exception as e:
            logger.error(f"Failed to build Rules and Documentation knowledge base: {e}")
            return False
    
    def build_complete_knowledge_base(self) -> bool:
        """
        Build a complete knowledge base combining all sources.
        """
        logger.info("Building Complete knowledge base...")
        
        self.encoder = MemvidEncoder()
        
        total_processed = 0
        
        # Add memory bank content
        memory_dir = Path(".memory")
        if memory_dir.exists():
            for memory_file in sorted(memory_dir.glob("*.md")):
                try:
                    with open(memory_file, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    priority = "high" if memory_file.name in ["40-active.md", "54-definition-of-done.md", "52-patterns.md"] else "medium"
                    formatted_content = f"MEMORY BANK - {memory_file.name.upper()}\nSource: {memory_file}\nType: memory_bank\nCategory: {self._categorize_memory_file(memory_file.name)}\nPriority: {priority}\n\n{content}"
                    
                    self.encoder.add_text(
                        formatted_content,
                        chunk_size=512,
                        overlap=50
                    )
                    total_processed += 1
                    
                except Exception as e:
                    logger.warning(f"Could not process memory file {memory_file}: {e}")
        
        # Add rules and documentation
        rules_dir = Path(".cursor/rules")
        if rules_dir.exists():
            for rule_file in rules_dir.glob("**/*.mdc"):
                try:
                    with open(rule_file, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    formatted_content = f"CURSOR RULE - {rule_file.name.upper()}\nSource: {rule_file}\nType: cursor_rule\nCategory: development_standards\nPriority: high\n\n{content}"
                    
                    self.encoder.add_text(
                        formatted_content,
                        chunk_size=512,
                        overlap=50
                    )
                    total_processed += 1
                    
                except Exception as e:
                    logger.warning(f"Could not process rule file {rule_file}: {e}")
        
        # Add project documentation
        doc_files = ["README.md", "pom.xml", "template.yaml"]
        for doc_file in doc_files:
            if os.path.exists(doc_file):
                try:
                    with open(doc_file, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    formatted_content = f"PROJECT DOCUMENTATION - {doc_file.upper()}\nSource: {doc_file}\nType: project_documentation\nCategory: {self._categorize_doc_file(doc_file)}\nPriority: medium\n\n{content}"
                    
                    self.encoder.add_text(
                        formatted_content,
                        chunk_size=512,
                        overlap=50
                    )
                    total_processed += 1
                    
                except Exception as e:
                    logger.warning(f"Could not process documentation {doc_file}: {e}")
        
        # Add comprehensive project context
        project_context = self._generate_comprehensive_project_context()
        if project_context:
            formatted_content = f"COMPREHENSIVE PROJECT CONTEXT\nSource: generated_comprehensive_context\nType: project_overview\nCategory: system_knowledge\nPriority: high\n\n{project_context}"
            
            self.encoder.add_text(
                formatted_content,
                chunk_size=512,
                overlap=50
            )
            total_processed += 1
        
        if total_processed == 0:
            logger.warning("No content was processed for complete knowledge base")
            return False
        
        # Build the video knowledge base
        try:
            self.encoder.build_video(
                str(self.complete_video),
                str(self.complete_index),
                codec='mp4v',
                show_progress=True
            )
            logger.info(f"Complete knowledge base built: {total_processed} sources processed")
            return True
        except Exception as e:
            logger.error(f"Failed to build complete knowledge base: {e}")
            return False
    
    def _categorize_memory_file(self, filename: str) -> str:
        """Categorize memory bank files."""
        if filename.startswith("01-") or filename.startswith("02-"):
            return "project_foundation"
        elif filename.startswith("1"):
            return "requirements_domain"
        elif filename.startswith("2"):
            return "architecture"
        elif filename.startswith("3"):
            return "implementation"
        elif filename.startswith("4"):
            return "active_development"
        elif filename.startswith("5"):
            return "standards_progress"
        elif filename.startswith("6"):
            return "knowledge_repository"
        elif filename.startswith("9"):
            return "documentation"
        else:
            return "general"
    
    def _categorize_doc_file(self, filename: str) -> str:
        """Categorize documentation files."""
        if filename.lower() == "readme.md":
            return "project_overview"
        elif filename.lower() == "pom.xml":
            return "build_configuration"
        elif filename.lower() == "template.yaml":
            return "infrastructure"
        elif filename.endswith(".md"):
            return "documentation"
        elif filename.endswith(".mdc"):
            return "cursor_rules"
        else:
            return "configuration"
    
    def _process_json_chat_history(self, chat_data: Dict) -> str:
        """Process JSON chat history data."""
        if isinstance(chat_data, list):
            # Array of messages
            messages = []
            for msg in chat_data:
                if isinstance(msg, dict):
                    role = msg.get("role", "unknown")
                    content = msg.get("content", "")
                    messages.append(f"{role.upper()}: {content}")
            return "\n\n".join(messages)
        elif isinstance(chat_data, dict):
            # Single conversation object
            if "messages" in chat_data:
                return self._process_json_chat_history(chat_data["messages"])
            else:
                # Extract text content from the object
                content_parts = []
                for key, value in chat_data.items():
                    if isinstance(value, str) and len(value) > 10:
                        content_parts.append(f"{key}: {value}")
                return "\n\n".join(content_parts)
        else:
            return str(chat_data)
    
    def _generate_project_context(self) -> str:
        """Generate project context based on current state."""
        return """
        ASSASSIN GAME PROJECT CONTEXT
        
        This is a location-based real-time elimination game built on AWS serverless architecture.
        The project combines elements of Assassin and Pokémon Go for an engaging multiplayer experience.
        
        KEY FEATURES:
        - Real-time GPS-based player tracking and elimination
        - Shrinking zone mechanics to intensify gameplay
        - Photo verification for eliminations
        - Multiple subscription tiers (Basic, Hunter, Assassin, Elite)
        - Safe zones for player protection
        - WebSocket real-time updates
        
        TECHNICAL STACK:
        - Backend: Java 17 with Spring Boot
        - Database: AWS DynamoDB
        - Infrastructure: AWS Lambda, API Gateway, CloudFormation
        - Real-time: WebSocket connections
        - Testing: JUnit 5, Mockito, Testcontainers
        
        CURRENT STATUS:
        - Core architecture implemented and tested
        - 338+ tests passing with comprehensive coverage
        - Memory Bank system fully rationalized
        - Quality foundation established
        - Ready for continued development
        
        DEVELOPMENT APPROACH:
        - Task Master workflow for project management
        - Memory Bank for context preservation
        - Definition of Done for quality assurance
        - Comprehensive testing strategy
        """
    
    def _generate_comprehensive_project_context(self) -> str:
        """Generate comprehensive project context."""
        return """
        ASSASSIN GAME - COMPREHENSIVE PROJECT OVERVIEW
        
        VISION:
        A location-based real-time elimination game that combines the strategic elements of Assassin 
        with the location-based mechanics of Pokémon Go, creating an engaging multiplayer experience 
        for thousands of concurrent users.
        
        CORE GAME MECHANICS:
        - GPS-based player tracking and target assignment
        - Photo verification system for eliminations
        - Shrinking zone mechanics creating dynamic pressure
        - Safe zones for strategic gameplay
        - Real-time updates via WebSocket connections
        - Multiple game modes (Classic, Team, Survival, Tournament)
        
        TECHNICAL ARCHITECTURE:
        - Serverless AWS infrastructure for scalability
        - Java 17 backend with modern features
        - DynamoDB for real-time data storage
        - Lambda functions for event processing
        - API Gateway for REST endpoints
        - CloudFormation for infrastructure as code
        
        QUALITY STANDARDS:
        - Comprehensive testing with 338+ passing tests
        - Definition of Done enforcement
        - Memory Bank for context preservation
        - Task Master workflow management
        - Continuous integration and deployment
        
        SUBSCRIPTION MODEL:
        - Basic: Standard game access
        - Hunter: Enhanced tracking capabilities
        - Assassin: Advanced tools and features
        - Elite: Exclusive features and tournaments
        
        DEVELOPMENT PATTERNS:
        - Domain-driven design principles
        - Event-driven architecture
        - Microservices with Lambda functions
        - Test-driven development approach
        - Infrastructure as code practices
        
        FUTURE ROADMAP:
        - Multi-game platform expansion
        - Plugin architecture for game types
        - Enhanced social features
        - Tournament and league systems
        - Mobile app development
        """
    
    def _extract_source_from_chunk(self, chunk: str) -> str:
        """
        Extract source information from a chunk.
        """
        lines = chunk.split('\n')
        for line in lines[:5]:  # Check first few lines
            if line.startswith('Source:'):
                return line.replace('Source:', '').strip()
            elif 'MEMORY BANK FILE:' in line:
                return line.replace('MEMORY BANK FILE:', '').strip()
            elif 'CURSOR RULE:' in line:
                return line.replace('CURSOR RULE:', '').strip()
            elif 'DOCUMENTATION:' in line:
                return line.replace('DOCUMENTATION:', '').strip()
        return "unknown"
    
    def initialize_retriever(self, knowledge_base: str = "complete") -> bool:
        """Initialize the retriever for querying the knowledge base."""
        video_file = None
        index_file = None
        
        if knowledge_base == "memory_bank":
            video_file = self.memory_bank_video
            index_file = self.memory_bank_index
        elif knowledge_base == "chat_history":
            video_file = self.chat_history_video
            index_file = self.chat_history_index
        elif knowledge_base == "rules_docs":
            video_file = self.rules_video
            index_file = self.rules_index
        else:  # complete
            video_file = self.complete_video
            index_file = self.complete_index
        
        if not video_file.exists() or not index_file.exists():
            logger.error(f"Knowledge base '{knowledge_base}' not found. Please build it first.")
            return False
        
        try:
            self.retriever = MemvidRetriever(str(video_file), str(index_file))
            self.chat = MemvidChat(str(video_file), str(index_file))
            logger.info(f"Retriever initialized for '{knowledge_base}' knowledge base")
            return True
        except Exception as e:
            logger.error(f"Failed to initialize retriever: {e}")
            return False
    
    def search(self, query: str, top_k: int = 5, knowledge_base: str = "complete") -> List[Dict[str, Any]]:
        """
        Search the knowledge base for relevant information.
        """
        if not self.initialize_retriever(knowledge_base):
            return []
        
        try:
            results = self.retriever.search(query, top_k=top_k)
            
            formatted_results = []
            for i, chunk in enumerate(results):
                formatted_results.append({
                    "rank": i + 1,
                    "content": chunk,
                    "knowledge_base": knowledge_base,
                    "query": query,
                    "source": self._extract_source_from_chunk(chunk)
                })
            
            logger.info(f"Found {len(formatted_results)} results for query: {query}")
            return formatted_results
            
        except Exception as e:
            logger.error(f"Search failed: {e}")
            return []
    
    def get_context(self, query: str, max_tokens: int = 2000, knowledge_base: str = "complete") -> str:
        """
        Get contextual information for a query.
        """
        if not self.initialize_retriever(knowledge_base):
            return ""
        
        try:
            context = self.retriever.get_context(query, max_tokens=max_tokens)
            logger.info(f"Retrieved context for query: {query}")
            return context
        except Exception as e:
            logger.error(f"Context retrieval failed: {e}")
            return ""
    
    def chat_with_knowledge(self, message: str, knowledge_base: str = "complete") -> str:
        """
        Chat with the knowledge base.
        """
        if not self.initialize_retriever(knowledge_base):
            return "Sorry, I couldn't access the knowledge base."
        
        try:
            response = self.chat.chat(message)
            logger.info(f"Chat response generated for: {message[:50]}...")
            return response
        except Exception as e:
            logger.error(f"Chat failed: {e}")
            return f"Sorry, I encountered an error: {e}"
    
    def start_interactive_chat(self, knowledge_base: str = "complete"):
        """
        Start an interactive chat interface using quick_chat.
        """
        if not self.initialize_retriever(knowledge_base):
            print("Failed to initialize knowledge base.")
            return
        
        try:
            video_file = (self.complete_video if knowledge_base == "complete" else 
                         self.memory_bank_video if knowledge_base == "memory_bank" else
                         self.chat_history_video if knowledge_base == "chat_history" else
                         self.rules_video)
            
            index_file = (self.complete_index if knowledge_base == "complete" else 
                         self.memory_bank_index if knowledge_base == "memory_bank" else
                         self.chat_history_index if knowledge_base == "chat_history" else
                         self.rules_index)
            
            print(f"Starting interactive chat with '{knowledge_base}' knowledge base...")
            print("Type 'quit' to exit the chat.")
            print("-" * 50)
            
            while True:
                user_input = input("\nYou: ").strip()
                if user_input.lower() in ['quit', 'exit', 'q']:
                    print("Goodbye!")
                    break
                
                if user_input:
                    try:
                        response = quick_chat(user_input, str(video_file), str(index_file))
                        print(f"Assistant: {response}")
                    except Exception as e:
                        print(f"Error: {e}")
            
        except KeyboardInterrupt:
            print("\nGoodbye!")
        except Exception as e:
            logger.error(f"Interactive chat failed: {e}")

# Flask API Server
app = Flask(__name__)
CORS(app)

# Global instance
memvid_integration = AssassinGameMemvidIntegration()

@app.route('/api/memvid/search', methods=['POST'])
def api_search():
    """
    API endpoint for searching the knowledge base.
    
    Expected JSON payload:
    {
        "query": "search query",
        "top_k": 5,
        "knowledge_base": "complete"  // optional: complete, memory_bank, chat_history, rules_docs
    }
    """
    try:
        data = request.get_json()
        query = data.get('query', '')
        top_k = data.get('top_k', 5)
        knowledge_base = data.get('knowledge_base', 'complete')
        
        if not query:
            return jsonify({"error": "Query is required"}), 400
        
        results = memvid_integration.search(query, top_k, knowledge_base)
        
        return jsonify({
            "query": query,
            "knowledge_base": knowledge_base,
            "results": results,
            "count": len(results)
        })
        
    except Exception as e:
        logger.error(f"API search error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/memvid/context', methods=['POST'])
def api_context():
    """
    API endpoint for getting contextual information.
    
    Expected JSON payload:
    {
        "query": "context query",
        "max_tokens": 2000,
        "knowledge_base": "complete"
    }
    """
    try:
        data = request.get_json()
        query = data.get('query', '')
        max_tokens = data.get('max_tokens', 2000)
        knowledge_base = data.get('knowledge_base', 'complete')
        
        if not query:
            return jsonify({"error": "Query is required"}), 400
        
        context = memvid_integration.get_context(query, max_tokens, knowledge_base)
        
        return jsonify({
            "query": query,
            "knowledge_base": knowledge_base,
            "context": context,
            "token_count": len(context.split())
        })
        
    except Exception as e:
        logger.error(f"API context error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/memvid/chat', methods=['POST'])
def api_chat():
    """
    API endpoint for chatting with the knowledge base.
    
    Expected JSON payload:
    {
        "message": "chat message",
        "knowledge_base": "complete"
    }
    """
    try:
        data = request.get_json()
        message = data.get('message', '')
        knowledge_base = data.get('knowledge_base', 'complete')
        
        if not message:
            return jsonify({"error": "Message is required"}), 400
        
        response = memvid_integration.chat_with_knowledge(message, knowledge_base)
        
        return jsonify({
            "message": message,
            "knowledge_base": knowledge_base,
            "response": response
        })
        
    except Exception as e:
        logger.error(f"API chat error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/memvid/health', methods=['GET'])
def api_health():
    """Health check endpoint."""
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "available_knowledge_bases": {
            "memory_bank": memvid_integration.memory_bank_video.exists(),
            "chat_history": memvid_integration.chat_history_video.exists(),
            "rules_docs": memvid_integration.rules_video.exists(),
            "complete": memvid_integration.complete_video.exists()
        }
    })

def main():
    """Main function to handle command line arguments."""
    parser = argparse.ArgumentParser(description="Memvid Integration for Assassin Game Project")
    
    parser.add_argument('--build-memory-bank', action='store_true',
                       help='Build knowledge base from .memory/ directory')
    parser.add_argument('--build-chat-history', action='store_true',
                       help='Build knowledge base from chat history')
    parser.add_argument('--build-rules-docs', action='store_true',
                       help='Build knowledge base from rules and documentation')
    parser.add_argument('--build-complete', action='store_true',
                       help='Build complete knowledge base from all sources')
    parser.add_argument('--start-server', action='store_true',
                       help='Start the Flask API server')
    parser.add_argument('--interactive', action='store_true',
                       help='Start interactive chat interface')
    parser.add_argument('--query', type=str,
                       help='Query the knowledge base')
    parser.add_argument('--knowledge-base', type=str, default='complete',
                       choices=['complete', 'memory_bank', 'chat_history', 'rules_docs'],
                       help='Which knowledge base to use')
    parser.add_argument('--port', type=int, default=5000,
                       help='Port for the Flask server')
    
    args = parser.parse_args()
    
    integration = AssassinGameMemvidIntegration()
    
    if args.build_memory_bank:
        success = integration.build_memory_bank_knowledge()
        print(f"Memory Bank knowledge base build: {'SUCCESS' if success else 'FAILED'}")
    
    elif args.build_chat_history:
        success = integration.build_chat_history_knowledge()
        print(f"Chat History knowledge base build: {'SUCCESS' if success else 'FAILED'}")
    
    elif args.build_rules_docs:
        success = integration.build_rules_and_docs_knowledge()
        print(f"Rules and Documentation knowledge base build: {'SUCCESS' if success else 'FAILED'}")
    
    elif args.build_complete:
        success = integration.build_complete_knowledge_base()
        print(f"Complete knowledge base build: {'SUCCESS' if success else 'FAILED'}")
    
    elif args.start_server:
        print(f"Starting Memvid API server on port {args.port}...")
        print("Available endpoints:")
        print(f"  POST http://localhost:{args.port}/api/memvid/search")
        print(f"  POST http://localhost:{args.port}/api/memvid/context")
        print(f"  POST http://localhost:{args.port}/api/memvid/chat")
        print(f"  GET  http://localhost:{args.port}/api/memvid/health")
        app.run(host='0.0.0.0', port=args.port, debug=True)
    
    elif args.interactive:
        integration.start_interactive_chat(args.knowledge_base)
    
    elif args.query:
        results = integration.search(args.query, knowledge_base=args.knowledge_base)
        print(f"\nQuery: {args.query}")
        print(f"Knowledge Base: {args.knowledge_base}")
        print(f"Results ({len(results)}):")
        print("-" * 50)
        
        for i, result in enumerate(results, 1):
            print(f"{i}. Source: {result['source']}")
            print(f"   Content: {result['content'][:200]}...")
            print()
    
    else:
        parser.print_help()

if __name__ == "__main__":
    main() 