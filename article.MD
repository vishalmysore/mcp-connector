# MCP Connector Guide

## Understanding MCP Architecture

The Model Context Protocol (MCP) is designed to enable AI agents to interact with external tools and services in a standardized way. An MCP server typically:

1. **Exposes Tools**: Provides a set of functions that AI agents can invoke
2. **Handles JSON-RPC**: Communicates using JSON-RPC 2.0 protocol
3. **Manages State**: Maintains context across multiple interactions

### Why Connectors?

MCP connectors serve as bridges between AI agents (like Claude Desktop or VS Code extensions) and your MCP server implementation. They are necessary because:

- **Protocol Translation**: Convert JSON-RPC messages to HTTP requests
- **Stream Management**: Handle stdin/stdout communication with AI agents
- **Error Handling**: Provide robust error management and timeouts
- **Cross-Language Support**: Allow servers written in any language to communicate with MCP clients

# MCP Connector Guide

If you have created an MCP Server in Java and are looking for ways to connect it to Claude Desktop or Visual Studio Code agents, the MCP Connector provides multiple ways to establish these connections.

## 1. Pure Java Connector

The Java connector is a lightweight, efficient bridge that requires only Java runtime:

```json
{
  "routeplanningagent": {
    "command": "java",
    "args": [
      "-jar",
      "/work/a2a-mcp-bridge/target/mcp-connector-full.jar",
      "http://localhost:8080"
    ],
    "timeout": 30000
  }
}
```

## 2. Node.js Connectors

### Basic Node.js Connector
Suitable for simple MCP server connections:

```json
{
  "customerserviceagent": {
    "command": "node",
    "args": [
      "/work/springactions/src/main/resources/mcpserver.js"
    ]
  }
}
```

### Dynamic Node.js Connector
Supports dynamic port configuration and multiple agent instances:

```json
{
  "dynamiccustomerserviceagent": {
    "command": "node",
    "args": ["/work/springactions/src/main/resources/a2ajava.cjs", "http://localhost:7860"],
    "timeout": 30000
  },
  "routeplanningagent": {
    "command": "node",
    "args": ["/work/springactions/src/main/resources/a2ajava.cjs", "http://localhost:8080"],
    "timeout": 30000
  }
}
```

## Coming Soon

- **Python Connector**: Native Python implementation for MCP protocol
- **Shell Script Connector**: Lightweight shell-based implementation

## Features

- **Language Independence**: Choose the connector that best fits your stack
- **Flexible Configuration**: Support for different ports and endpoints
- **Robust Error Handling**: Built-in timeout and error management
- **Easy Integration**: Simple configuration for Claude Desktop and VS Code agents

## Reference Implementations

### Python Connector
Here's a minimal Python implementation:

```python
import sys
import json
import requests
from typing import Dict, Any

class MCPConnector:
    def __init__(self, target_url: str):
        self.target_url = target_url

    def forward_request(self, data: Dict[Any, Any]) -> None:
        try:
            response = requests.post(self.target_url, json=data)
            response.raise_for_status()
            print(json.dumps(response.json()))
            sys.stdout.flush()
        except Exception as e:
            error = {
                "jsonrpc": "2.0",
                "id": data.get("id"),
                "error": {"code": -32603, "message": str(e)}
            }
            print(json.dumps(error))
            sys.stdout.flush()

def main():
    connector = MCPConnector("http://localhost:8080")
    for line in sys.stdin:
        if line.strip():
            try:
                data = json.loads(line)
                connector.forward_request(data)
            except json.JSONDecodeError as e:
                error = {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32700, "message": f"Parse error: {str(e)}"}
                }
                print(json.dumps(error))
                sys.stdout.flush()

if __name__ == "__main__":
    main()
```

### Shell Script Connector
A basic bash implementation:

```bash
#!/bin/bash

TARGET_URL=${1:-"http://localhost:8080"}

while IFS= read -r line; do
    if [ ! -z "$line" ]; then
        # Log incoming request
        echo "⬅️ Incoming: $line" >&2
        
        # Forward request to server
        response=$(curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "$line" \
            "$TARGET_URL")
        
        # Handle empty response
        if [ -z "$response" ]; then
            echo "⚠️ Empty response from server" >&2
            continue
        fi
        
        # Log and output response
        echo "➡️ Response: $response" >&2
        echo "$response"
    fi
done

```

### Configuration Examples:

For Python connector:
```json
{
  "pythonagent": {
    "command": "python",
    "args": ["mcp_connector.py", "http://localhost:8080"],
    "timeout": 30000
  }
}
```

For Shell script connector:
```json
{
  "shellagent": {
    "command": "bash",
    "args": ["mcp_connector.sh", "http://localhost:8080"],
    "timeout": 30000
  }
}
```