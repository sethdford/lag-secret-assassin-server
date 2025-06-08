#!/usr/bin/env python3
"""
Test script for Memvid Integration
==================================

This script tests the enhanced Memvid integration with the Assassin Game project.
"""

import sys
from pathlib import Path

# Add the virtual environment to the path
sys.path.insert(0, str(Path(__file__).parent / "memvid-env" / "lib" / "python3.13" / "site-packages"))

from .memvid.memvid_integration import AssassinGameMemvidIntegration

def test_memory_bank_integration():
    """Test building knowledge base from memory bank files."""
    print("Testing Memory Bank Integration...")
    
    integration = AssassinGameMemvidIntegration()
    
    # Test building memory bank knowledge
    print("1. Building Memory Bank knowledge base...")
    success = integration.build_memory_bank_knowledge()
    print(f"   Result: {'SUCCESS' if success else 'FAILED'}")
    
    if success:
        # Test searching the memory bank
        print("2. Testing search functionality...")
        results = integration.search("shrinking zone mechanics", knowledge_base="memory_bank")
        print(f"   Found {len(results)} results for 'shrinking zone mechanics'")
        
        if results:
            print(f"   Top result: {results[0]['content'][:100]}...")
        
        # Test context retrieval
        print("3. Testing context retrieval...")
        context = integration.get_context("definition of done", knowledge_base="memory_bank")
        print(f"   Context length: {len(context)} characters")
        
        if context:
            print(f"   Context preview: {context[:100]}...")
    
    return success

def test_complete_integration():
    """Test building complete knowledge base from all sources."""
    print("\nTesting Complete Integration...")
    
    integration = AssassinGameMemvidIntegration()
    
    # Test building complete knowledge base
    print("1. Building Complete knowledge base...")
    success = integration.build_complete_knowledge_base()
    print(f"   Result: {'SUCCESS' if success else 'FAILED'}")
    
    if success:
        # Test various queries
        test_queries = [
            "How does the shrinking zone work?",
            "What are the subscription tiers?",
            "Java testing patterns",
            "AWS architecture",
            "Definition of Done"
        ]
        
        print("2. Testing various queries...")
        for query in test_queries:
            results = integration.search(query, top_k=3, knowledge_base="complete")
            print(f"   '{query}': {len(results)} results")
    
    return success

def test_chat_functionality():
    """Test chat functionality with the knowledge base."""
    print("\nTesting Chat Functionality...")
    
    integration = AssassinGameMemvidIntegration()
    
    # Initialize with complete knowledge base
    if integration.initialize_retriever("complete"):
        print("1. Knowledge base initialized successfully")
        
        # Test chat
        test_messages = [
            "What is the Assassin Game about?",
            "How do eliminations work?",
            "What testing frameworks are used?"
        ]
        
        for message in test_messages:
            try:
                response = integration.chat_with_knowledge(message, "complete")
                print(f"   Q: {message}")
                print(f"   A: {response[:100]}...")
                print()
            except Exception as e:
                print(f"   Chat failed for '{message}': {e}")
    else:
        print("1. Failed to initialize knowledge base")

def main():
    """Run all tests."""
    print("Memvid Integration Test Suite")
    print("=" * 40)
    
    # Check if memory bank exists
    memory_dir = Path(".memory")
    if not memory_dir.exists():
        print("ERROR: .memory directory not found!")
        print("Please ensure you're running this from the project root.")
        return
    
    print(f"Found {len(list(memory_dir.glob('*.md')))} memory bank files")
    
    # Run tests
    try:
        # Test 1: Memory Bank Integration
        memory_success = test_memory_bank_integration()
        
        # Test 2: Complete Integration
        complete_success = test_complete_integration()
        
        # Test 3: Chat Functionality (only if complete integration worked)
        if complete_success:
            test_chat_functionality()
        
        # Summary
        print("\nTest Summary:")
        print(f"Memory Bank Integration: {'PASS' if memory_success else 'FAIL'}")
        print(f"Complete Integration: {'PASS' if complete_success else 'FAIL'}")
        
        if memory_success or complete_success:
            print("\n✅ Memvid integration is working!")
            print("\nNext steps:")
            print("1. Run: python3 .memvid/memvid_integration.py --build-complete")
            print("2. Run: python3 .memvid/memvid_integration.py --start-server")
            print("3. Test API endpoints or use --interactive mode")
        else:
            print("\n❌ Integration tests failed")
            
    except Exception as e:
        print(f"\nTest suite failed with error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main() 