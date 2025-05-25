const http = require("http");
const https = require("https");

const targetUrl = process.argv[2] || "http://localhost:7860"; // Get URL from args
const { hostname, pathname, protocol, port } = new URL(targetUrl);

const username = process.argv[3] || null;
const password = process.argv[4] || null;

let authHeader = null;

if (username && password) {
  const base64Auth = Buffer.from(`${username}:${password}`).toString("base64");
  authHeader = `Basic ${base64Auth}`;
  console.error("🔑 Authentication enabled");
}

process.stdin.setEncoding("utf8");

let buffer = "";

// Keep-alive ping
const keepAliveInterval = setInterval(() => {
  console.error("💓 Keep-alive ping...");
}, 10000);

// Graceful shutdown
process.on("SIGINT", () => {
  console.error("🛑 SIGINT received. Shutting down gracefully...");
  clearInterval(keepAliveInterval);
  process.exit(0);
});

process.on("uncaughtException", (err) => {
  console.error("❌ Uncaught exception:", err);
});

process.stdin.on("data", (chunk) => {
  buffer += chunk;
  let boundary;
  while ((boundary = buffer.indexOf("\n")) >= 0) {
    const line = buffer.slice(0, boundary);
    buffer = buffer.slice(boundary + 1);
    if (line.trim()) {
      traceAndForward(line);
    }
  }
});

function traceAndForward(line) {
  try {
    console.error("\n⬅️ Incoming from client:\n" + line);

    const json = JSON.parse(line);
    const postData = JSON.stringify(json);

    const headers = {
      "Content-Type": "application/json",
      "Content-Length": Buffer.byteLength(postData),
      "Connection": "keep-alive",
    };

    if (authHeader) {
      headers["Authorization"] = authHeader;
    }

    const options = {
      hostname,
      port: port || (protocol === "https:" ? 443 : 80),
      path: pathname || "/",
      method: "POST",
      headers,
    };

    const transport = protocol === "https:" ? https : http;

    const req = transport.request(options, (res) => {
      let responseBody = "";
      res.setEncoding("utf8");
      res.on("data", (chunk) => (responseBody += chunk));
      res.on("end", () => {
        if (responseBody.trim() === "") {
          console.warn("⚠️ Empty response body received (possibly a notification).");
          return;
        }
        try {
          console.error("➡️ Response from backend:\n" + responseBody);
          const response = JSON.parse(responseBody);
          process.stdout.write(JSON.stringify(response) + "\n");
        } catch (e) {
          console.error("❌ Failed to parse response:", e.message);
          const errorResponse = {
            jsonrpc: "2.0",
            id: json.id || null,
            error: {
              code: -32603,
              message: "Response parse error: " + e.message,
            },
          };
          process.stdout.write(JSON.stringify(errorResponse) + "\n");
        }
      });
    });

    req.on("error", (e) => {
      console.error("❌ Request error:", e.message);
      const errorResponse = {
        jsonrpc: "2.0",
        id: json.id || null,
        error: {
          code: -32603,
          message: "Request error: " + e.message,
        },
      };
      process.stdout.write(JSON.stringify(errorResponse) + "\n");
    });

    req.write(postData);
    req.end();
  } catch (e) {
    console.error("❌ Invalid JSON input:", e.message);
    const errorResponse = {
      jsonrpc: "2.0",
      id: null,
      error: {
        code: -32700,
        message: "Parse error: " + e.message,
      },
    };
    process.stdout.write(JSON.stringify(errorResponse) + "\n");
  }
}

console.error(`🚀 MCP passthrough server started, forwarding to ${targetUrl}`);
