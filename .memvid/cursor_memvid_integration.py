#!/usr/bin/env python3
"""
Cursor-Memvid Integration
========================

This module provides integration between Cursor IDE and the Memvid knowledge base,
allowing Cursor to query project context, rules, and memory bank information
instead of making assumptions.
"""

import sys
import json
import logging
import requests
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import dataclass

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@dataclass
class MemvidResponse:
    """Represents a response from the Memvid knowledge base."""
    results: List[Dict[str, Any]]
    query: str
    knowledge_base: str
    total_results: int
    search_time: float = 0.0

class CursorMemvidClient:
    """
    HTTP client for accessing Memvid knowledge base from Cursor.
    """
    
    def __init__(self, base_url: str = "http://localhost:5001"):
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json",
            "User-Agent": "Cursor-Memvid-Integration/1.0"
        })
    
    def is_server_available(self) -> bool:
        """Check if the Memvid server is running."""
        try:
            response = self.session.get(f"{self.base_url}/api/memvid/health", timeout=2)
            return response.status_code == 200
        except Exception:
            return False
    
    def search_knowledge(self, query: str, knowledge_base: str = "complete", top_k: int = 5) -> MemvidResponse:
        """Search the Memvid knowledge base for relevant information."""
        try:
            payload = {
                "query": query,
                "knowledge_base": knowledge_base,
                "top_k": top_k
            }
            
            response = self.session.post(
                f"{self.base_url}/api/memvid/search",
                json=payload,
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                return MemvidResponse(
                    results=data.get("results", []),
                    query=query,
                    knowledge_base=knowledge_base,
                    total_results=len(data.get("results", [])),
                    search_time=data.get("search_time", 0.0)
                )
            else:
                logger.error(f"Search failed with status {response.status_code}: {response.text}")
                return MemvidResponse([], query, knowledge_base, 0)
                
        except Exception as e:
            logger.error(f"Error searching knowledge base: {e}")
            return MemvidResponse([], query, knowledge_base, 0)
    
    def ask_question(self, question: str, context_type: str = "general") -> str:
        """Ask a question and get a comprehensive answer based on project knowledge."""
        # First, search for relevant information
        search_response = self.search_knowledge(question, "complete", 5)
        
        if not search_response.results:
            return f"No relevant information found for: {question}"
        
        # Format the response
        answer_parts = [f"**Answer for: {question}**\n"]
        
        for i, result in enumerate(search_response.results, 1):
            content = result.get("content", "")
            source = result.get("source", "unknown")
            
            # Truncate content if too long
            if len(content) > 300:
                content = content[:300] + "..."
            
            answer_parts.append(f"{i}. **Source:** {source}")
            answer_parts.append(f"   **Content:** {content}\n")
        
        return "\n".join(answer_parts)

def create_cursor_rule():
    """Create a Cursor rule for Memvid integration."""
    rule_content = '''---
description: Memvid knowledge base integration for project context and clarity
globs: **/*
alwaysApply: true
---

# Memvid Knowledge Base Integration

## Purpose
This rule enables Cursor to access the project's Memvid knowledge base for context and clarity instead of making assumptions.

## When to Use Memvid

### Always Use Before:
- Making assumptions about project architecture
- Implementing new features without context
- Writing tests without understanding patterns
- Making technical decisions
- Suggesting code changes

### Search Queries Examples:
- "How does the shrinking zone work?"
- "What are the testing patterns used?"
- "What is the DynamoDB schema?"
- "How is authentication implemented?"
- "What are the subscription tiers?"

## Integration Commands

Use these Python commands to query the knowledge base:

```python
# Search for specific information
from .memvid.cursor_memvid_integration import CursorMemvidClient
client = CursorMemvidClient()

# Check if server is available
if client.is_server_available():
    # Search for information
    response = client.search_knowledge("your query here")
    
    # Ask comprehensive questions
    answer = client.ask_question("How should I implement feature X?")
    print(answer)
else:
    print("Start Memvid server: python3 .memvid/memvid_integration.py --start-server")
```

## Best Practices

- **Search First**: Always search before making assumptions
- **Be Specific**: Use specific queries for better results
- **Verify Information**: Cross-reference with multiple sources
- **Update Knowledge**: Suggest updates when information is outdated

Remember: **Always prefer Memvid knowledge over assumptions!**
'''
    
    cursor_rules_dir = Path.cwd() / ".cursor" / "rules"
    cursor_rules_dir.mkdir(parents=True, exist_ok=True)
    
    rule_file = cursor_rules_dir / "memvid_integration.mdc"
    
    with open(rule_file, 'w') as f:
        f.write(rule_content)
    
    print(f"✅ Created Cursor rule: {rule_file}")
    return rule_file

def main():
    """Main entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(description="Cursor-Memvid Integration")
    parser.add_argument("--setup-cursor", action="store_true", help="Setup Cursor rules")
    parser.add_argument("--test-client", action="store_true", help="Test the HTTP client")
    parser.add_argument("--query", help="Test query for the client")
    
    args = parser.parse_args()
    
    if args.setup_cursor:
        print("Setting up Cursor integration...")
        create_cursor_rule()
        print("\n✅ Cursor integration setup complete!")
        print("\nNext steps:")
        print("1. Start the Memvid server: python3 .memvid/memvid_integration.py --start-server")
        print("2. Use the client in Cursor for project context")
        return
    
    if args.test_client:
        print("Testing Memvid client...")
        client = CursorMemvidClient()
        
        if not client.is_server_available():
            print("❌ Memvid server is not available")
            print("Start it with: python3 .memvid/memvid_integration.py --start-server")
            return
        
        # Test search
        print("\n🔍 Testing search...")
        response = client.search_knowledge("shrinking zone", "complete", 3)
        print(f"Found {response.total_results} results")
        
        for i, result in enumerate(response.results, 1):
            print(f"{i}. {result.get('source', 'unknown')}: {result.get('content', '')[:100]}...")
        
        return
    
    if args.query:
        client = CursorMemvidClient()
        if not client.is_server_available():
            print("❌ Memvid server is not available")
            return
        
        answer = client.ask_question(args.query)
        print(answer)
        return
    
    parser.print_help()

if __name__ == "__main__":
    main()
