# Cursor-Memvid Integration

This integration allows Cursor IDE to access the project's Memvid knowledge base for context and clarity instead of making assumptions. The system provides semantic search across project documentation, memory bank files, rules, and chat history.

## 🚀 Quick Start

### 1. Setup Integration

```bash
# Set up Cursor rules and configuration
python3 .memvid/cursor_memvid_integration.py --setup-cursor

# Start the Memvid server
python3 .memvid/memvid_integration.py --start-server --port 5001
```

### 2. Use in Cursor

```python
from .memvid.cursor_memvid_integration import CursorMemvidClient

# Initialize client
client = CursorMemvidClient()

# Check if server is available
if client.is_server_available():
    # Search for specific information
    response = client.search_knowledge("How does the shrinking zone work?")
    
    # Ask comprehensive questions
    answer = client.ask_question("How should I implement player location updates?")
    print(answer)
else:
    print("Start Memvid server: python3 .memvid/memvid_integration.py --start-server")
```

## 📋 Features

### Knowledge Base Access
- **Memory Bank**: Access to all `.memory/*.md` files with project context
- **Rules & Documentation**: Cursor rules, development standards, and docs
- **Chat History**: Previous conversation logs and decisions
- **Complete Knowledge**: Combined search across all sources

### Search Capabilities
- **Semantic Search**: Find relevant information using natural language queries
- **Source Attribution**: Each result includes source file information
- **Ranked Results**: Results ordered by relevance with similarity scores
- **Multiple Knowledge Bases**: Search specific areas or all combined

### Integration Methods
- **HTTP Client**: Direct API access for programmatic use
- **Cursor Rules**: Automatic guidance for when to use Memvid
- **Demo Scripts**: Examples and testing utilities

## 🔧 API Reference

### CursorMemvidClient

#### `search_knowledge(query, knowledge_base="complete", top_k=5)`
Search the knowledge base for relevant information.

**Parameters:**
- `query` (str): Search query in natural language
- `knowledge_base` (str): Which knowledge base to search
  - `"complete"`: All sources combined (default)
  - `"memory_bank"`: Only memory bank files
  - `"rules"`: Only rules and documentation
  - `"chat_history"`: Only conversation logs
- `top_k` (int): Number of results to return (default: 5)

**Returns:** `MemvidResponse` object with results

#### `ask_question(question, context_type="general")`
Ask a comprehensive question and get formatted answer with sources.

**Parameters:**
- `question` (str): The question to ask
- `context_type` (str): Context type for the question (default: "general")

**Returns:** Formatted string with answer and sources

#### `is_server_available()`
Check if the Memvid server is running and accessible.

**Returns:** Boolean indicating server availability

## 📖 Usage Examples

### Architecture Questions
```python
client = CursorMemvidClient()

# Get architecture information
response = client.search_knowledge("DynamoDB table structure")
for result in response.results:
    print(f"Source: {result['source']}")
    print(f"Content: {result['content'][:200]}...")
```

### Implementation Guidance
```python
# Ask for implementation guidance
answer = client.ask_question("How should I implement WebSocket connections?")
print(answer)
```

### Testing Patterns
```python
# Find testing patterns
response = client.search_knowledge("testing patterns JUnit Mockito")
```

### Domain Knowledge
```python
# Get domain-specific information
answer = client.ask_question("What are the game mechanics for elimination?")
```

## 🎯 When to Use Memvid

### Always Use Before:
- Making assumptions about project architecture
- Implementing new features without context
- Writing tests without understanding patterns
- Making technical decisions
- Suggesting code changes

### Example Queries:
- "How does the shrinking zone work?"
- "What are the testing patterns used?"
- "What is the DynamoDB schema?"
- "How is authentication implemented?"
- "What are the subscription tiers?"
- "What are the quality gates for task completion?"

## 🛠️ Setup Details

### Files Created
- `.memvid/cursor_memvid_integration.py`: Main integration client
- `.cursor/rules/memvid_integration.mdc`: Cursor rule for guidance
- `.memvid/cursor_memvid_demo.py`: Demonstration script

### Dependencies
- `requests`: HTTP client for API communication
- `pathlib`: File system operations
- `dataclasses`: Response data structures

### Server Requirements
- Memvid server running on `http://localhost:5001`
- Knowledge bases built and indexed
- API endpoints available:
  - `GET /api/memvid/health`
  - `POST /api/memvid/search`
  - `POST /api/memvid/context`

## 🔍 Testing

### Test Client Functionality
```bash
# Test the integration
python3 .memvid/cursor_memvid_integration.py --test-client

# Test with specific query
python3 .memvid/cursor_memvid_integration.py --query "How does authentication work?"

# Run full demonstration
python3 .memvid/cursor_memvid_demo.py
```

### Verify Server Health
```bash
curl -X GET http://localhost:5001/api/memvid/health
```

## 🚨 Troubleshooting

### Server Not Available
```
❌ Memvid server is not available
```
**Solution:** Start the server with:
```bash
python3 .memvid/memvid_integration.py --start-server --port 5001
```

### No Results Found
```