import { Plugin } from "vite";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";

export function vitePluginMcp(): Plugin {
  let sseServerTransport: SSEServerTransport | null = null;
  let sseClientTransport: SSEClientTransport | null = null;

  return {
    name: "vite-plugin-mcp",
    configureServer(server) {
      server.middlewares.use("/mcp/sse", async (req, res) => {
        console.log("[MCP] New SSE connection");
        
        // Setup SSE Server Transport
        sseServerTransport = new SSEServerTransport("/mcp/messages", res as any);
        await sseServerTransport.start();

        // Setup SSE Client Transport
        const aiconServer = process.env.VITE_AICON_SERVER || "http://localhost:3001";
        sseClientTransport = new SSEClientTransport(new URL(`${aiconServer}/sse`));
        
        await sseClientTransport.start();

        // Pipe messages
        sseServerTransport.onmessage = (msg) => {
          if (sseClientTransport) {
            sseClientTransport.send(msg);
          }
        };

        sseClientTransport.onmessage = (msg) => {
          if (sseServerTransport) {
            sseServerTransport.send(msg);
          }
        };

        sseServerTransport.onclose = () => {
          console.log("[MCP] Server SSE connection closed");
          sseClientTransport?.close();
          sseServerTransport = null;
          sseClientTransport = null;
        };

        sseClientTransport.onclose = () => {
          console.log("[MCP] Client SSE connection closed");
          sseServerTransport?.close();
          sseServerTransport = null;
          sseClientTransport = null;
        };
      });

      server.middlewares.use("/mcp/messages", async (req, res) => {
        if (!sseServerTransport) {
          res.statusCode = 500;
          res.end("SSE connection not established");
          return;
        }
        try {
          await sseServerTransport.handlePostMessage(req as any, res as any);
        } catch (error) {
          console.error("[MCP] Error handling message:", error);
          if (!res.writableEnded) {
             res.statusCode = 500;
             res.end(String(error));
          }
        }
      });
    },
  };
}