#!/usr/bin/env python3
"""
Cursor-Memvid Integration Demo
=============================

This script demonstrates how to use the Memvid knowledge base integration
within Cursor for getting project context and clarity.
"""

from cursor_memvid_integration import CursorMemvidClient

def demo_memvid_integration():
    """Demonstrate the Memvid integration capabilities."""
    
    print("🚀 Cursor-Memvid Integration Demo")
    print("=" * 50)
    
    # Initialize the client
    client = CursorMemvidClient()
    
    # Check server availability
    print("\n1. Checking Memvid server availability...")
    if not client.is_server_available():
        print("❌ Memvid server is not available!")
        print("Please start it with: python3 .memvid/memvid_integration.py --start-server")
        return
    
    print("✅ Memvid server is running!")
    
    # Demo queries that Cursor might use
    demo_queries = [
        "How does the shrinking zone work?",
        "What are the testing patterns used in this project?",
        "What is the DynamoDB schema structure?",
        "How is authentication implemented?",
        "What are the subscription tiers?"
    ]
    
    print("\n2. Demonstrating knowledge base searches...")
    print("-" * 50)
    
    for i, query in enumerate(demo_queries, 1):
        print(f"\n🔍 Query {i}: {query}")
        print("-" * 30)
        
        # Search for information
        response = client.search_knowledge(query, "complete", 3)
        
        if response.total_results > 0:
            print(f"Found {response.total_results} results:")
            for j, result in enumerate(response.results, 1):
                content = result.get("content", "")
                source = result.get("source", "unknown")
                
                # Show first 150 characters
                preview = content[:150] + "..." if len(content) > 150 else content
                print(f"  {j}. Source: {source}")
                print(f"     Preview: {preview}")
        else:
            print("No results found.")
        
        print()
    
    print("\n3. Demonstrating comprehensive question answering...")
    print("-" * 50)
    
    question = "How should I implement player location updates?"
    print(f"\n❓ Question: {question}")
    print("-" * 30)
    
    answer = client.ask_question(question)
    print(answer)
    
    print("\n✅ Demo completed!")
    print("\nHow to use in Cursor:")
    print("1. Import the client: from .memvid.cursor_memvid_integration import CursorMemvidClient")
    print("2. Create client: client = CursorMemvidClient()")
    print("3. Search: response = client.search_knowledge('your query')")
    print("4. Ask questions: answer = client.ask_question('your question')")

if __name__ == "__main__":
    demo_memvid_integration() 