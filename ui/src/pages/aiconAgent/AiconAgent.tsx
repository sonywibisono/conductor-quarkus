import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  Box,
  TextField,
  IconButton,
  Typography,
  Paper,
  Container,
  CircularProgress,
  Chip,
  Tooltip,
  Button,
} from "@mui/material";
import SendIcon from "@mui/icons-material/Send";
import RefreshIcon from "@mui/icons-material/Refresh";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import { useNavigate } from "react-router";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";

const AICON_SERVER_URL =
  import.meta.env.VITE_AICON_SERVER || "http://localhost:3001";

type Message = { role: "user" | "agent" | "system"; text: string };

const MessageContent = ({ text, role }: { text: string; role: string }) => {
  const navigate = useNavigate();

  if (role === "system") {
    return (
      <Typography variant="body1" sx={{ whiteSpace: "pre-wrap", fontStyle: "italic", fontSize: "0.875rem" }}>
        {text}
      </Typography>
    );
  }

  let displayText = "";
  let workflows: any[] | null = null;

  try {
    const parsed = JSON.parse(text);
    
    // Check if the response is an array of function calls directly
    const functionCalls = Array.isArray(parsed) 
      ? parsed.filter(item => item && item.functionCall)
      : (parsed && parsed.functionCalls && Array.isArray(parsed.functionCalls) ? parsed.functionCalls : []);

    if (functionCalls.length > 0) {
      for (const call of functionCalls) {
        if (call.functionResult) {
          let resultParsed = null;
          
          if (typeof call.functionResult === "string") {
            try {
              resultParsed = JSON.parse(call.functionResult);
            } catch (e) {
              console.error("Standard parsing failed, attempting fallback:", e);
              // Fallback 1: Try appending missing bracket
              try {
                const trimmed = call.functionResult.trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("}")) {
                  resultParsed = JSON.parse(trimmed + "]");
                } else if (trimmed.startsWith("{") && !trimmed.endsWith("}")) {
                  resultParsed = [JSON.parse(trimmed + "}")];
                } else {
                  throw new Error("Needs regex fallback");
                }
              } catch (fallbackErr) {
                // Fallback 2: Regex extraction for truncated AI responses
                const nameMatches = [...call.functionResult.matchAll(/"name"\s*:\s*"([^"]+)"/g)];
                if (nameMatches.length > 0) {
                  resultParsed = nameMatches.map((m) => {
                    const block = call.functionResult.substring(Math.max(0, m.index - 50), m.index + 200);
                    const verMatch = block.match(/"version"\s*:\s*(\d+)/);
                    const descMatch = block.match(/"description"\s*:\s*"([^"]+)"/);
                    return {
                      name: m[1],
                      version: verMatch ? parseInt(verMatch[1], 10) : 1,
                      description: descMatch ? descMatch[1] : "",
                    };
                  });
                }
              }
            }
          } else {
            resultParsed = call.functionResult;
          }
          
          if (Array.isArray(resultParsed) && resultParsed.length > 0) {
            if (resultParsed[0].name || resultParsed[0].workflowName) {
              workflows = resultParsed;
              break;
            }
          } else if (resultParsed && (resultParsed.name || resultParsed.workflowName)) {
            // In case it parsed a single object instead of array
            workflows = [resultParsed];
            break;
          }
        }
      }
      
      // If we are in an object response, use the 'text' field if available
      if (!Array.isArray(parsed)) {
        displayText = parsed.text || parsed.response || parsed.message || "";
      }
    } else if (Array.isArray(parsed)) {
      // Direct array of workflows
      if (parsed.length > 0 && (parsed[0].name || parsed[0].workflowName)) {
        workflows = parsed;
      }
    } else if (parsed && typeof parsed === "object") {
      displayText = parsed.text || parsed.response || parsed.message || "";
    }
  } catch (e) {
    // Not JSON
  }

  if (workflows && workflows.length > 0) {
    return (
      <Box>
        {displayText && (
          <Typography variant="body1" sx={{ whiteSpace: "pre-wrap", mb: 2 }}>
            {displayText}
          </Typography>
        )}
        <Box display="flex" flexDirection="column" gap={1.5} sx={{ mt: 1 }}>
          {workflows.map((item: any, idx: number) => {
            const wfName = item.name || item.workflowName;
            const wfVersion = item.version || 1;
            return (
              <Paper 
                key={idx} 
                variant="outlined" 
                sx={{ 
                  p: 2, 
                  bgcolor: "background.paper",
                  borderRadius: 2,
                  borderLeft: 4,
                  borderLeftColor: "primary.main",
                  minWidth: "280px"
                }}
              >
                <Tooltip title="View Workflow Definition">
                  <Typography 
                    variant="subtitle1" 
                    sx={{ 
                      fontWeight: "bold", 
                      color: "primary.main",
                      cursor: "pointer",
                      "&:hover": { textDecoration: "underline" }
                    }}
                    onClick={() => navigate(`/workflowDef/${encodeURIComponent(wfName)}`)}
                  >
                    {wfName}
                  </Typography>
                </Tooltip>
                {(item.description || item.workflowDescription) && (
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    {item.description || item.workflowDescription}
                  </Typography>
                )}
                <Box display="flex" justifyContent="space-between" alignItems="center">
                  <Typography variant="caption" sx={{ bgcolor: "action.hover", px: 1, borderRadius: 1, fontWeight: "medium" }}>
                    Version: {wfVersion}
                  </Typography>
                  <Tooltip title="Run Workflow">
                    <IconButton 
                      size="small" 
                      color="primary" 
                      onClick={() => navigate("/runWorkflow", {
                        state: {
                          execution: {
                            workflowName: wfName,
                            workflowVersion: wfVersion,
                          },
                        },
                      })}
                      sx={{ bgcolor: "primary.light", color: "white", "&:hover": { bgcolor: "primary.main" } }}
                    >
                      <PlayArrowIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Paper>
            );
          })}
        </Box>
      </Box>
    );
  }

  // Fallback to text
  const finalOutput = displayText || text;
  return (
    <Typography variant="body1" sx={{ whiteSpace: "pre-wrap" }}>
      {typeof finalOutput === "string" ? finalOutput : JSON.stringify(finalOutput, null, 2)}
    </Typography>
  );
};

export default function AiconAgent() {
  const [messages, setMessages] = useState<Message[]>([
    { role: "agent", text: "Hello! I am AIcon Agent. Connecting to MCP Server..." }
  ]);
  const [input, setInput] = useState("");
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const clientRef = useRef<Client | null>(null);

  const connectMcp = useCallback(async () => {
    setIsConnecting(true);
    try {
      // Initialize the SSE Transport
      const transport = new SSEClientTransport(new URL(`${AICON_SERVER_URL}/sse`));
      
      // Initialize the MCP Client
      const client = new Client(
        { name: "conductor-ui-agent", version: "1.0.0" },
        { capabilities: {} }
      );
      
      await client.connect(transport);
      clientRef.current = client;
      setIsConnected(true);
      setIsConnecting(false);

      // Fetch available tools to verify connection
      const toolsResponse = await client.listTools();
      const toolNames = toolsResponse.tools.map((t) => t.name).join(", ");
      
      setMessages((prev) => [
        ...prev,
        {
          role: "system", 
          text: `Successfully connected to MCP Server (${AICON_SERVER_URL}).\nAvailable tools: ${toolNames || "None"}` 
        }
      ]);
    } catch (error) {
      console.error("Failed to connect to MCP:", error);
      setIsConnected(false);
      setIsConnecting(false);
      setMessages((prev) => [
        ...prev,
        { role: "system", text: `Connection error (${AICON_SERVER_URL}): ${String(error)}` }
      ]);
    }
  }, []);

  useEffect(() => {
    connectMcp();

    return () => {
      if (clientRef.current) {
        clientRef.current.close().catch(console.error);
      }
    };
  }, [connectMcp]);

  const handleSend = async () => {
    if (!input.trim()) return;

    // Add user message
    const userMessage = input;
    setMessages((prev) => [...prev, { role: "user", text: userMessage }]);
    setInput("");

    // Command: /call <tool_name> <json_args>
    if (userMessage.trim().toLowerCase().startsWith("/call")) {
      if (clientRef.current && isConnected) {
        try {
          const parts = userMessage.split(" ");
          const toolName = parts[1];
          const argsString = parts.slice(2).join(" ");
          const args = argsString ? JSON.parse(argsString) : {};

          setMessages((prev) => [...prev, { role: "system", text: `Calling tool "${toolName}"...` }]);

          const result = await clientRef.current.callTool({
            name: toolName,
            arguments: args,
          });

          setMessages((prev) => [
            ...prev,
            { role: "agent", text: `Tool Result:\n${JSON.stringify(result, null, 2)}` }
          ]);
        } catch (error) {
          setMessages((prev) => [
            ...prev,
            { role: "agent", text: `Error calling tool: ${String(error)}` }
          ]);
        }
      } else {
        setMessages((prev) => [...prev, { role: "agent", text: "MCP Client is not connected." }]);
      }
      return;
    }

    // Simple echo/command handler for the demo
    if (userMessage.trim().toLowerCase() === "/tools") {
      if (clientRef.current && isConnected) {
        try {
          const toolsResponse = await clientRef.current.listTools();
          const toolData = toolsResponse.tools.map(t => `- ${t.name}: ${t.description}`).join("\n");
          setMessages((prev) => [
            ...prev,
            { role: "agent", text: `Here are the available tools:\n${toolData}` }
          ]);
        } catch (error) {
           setMessages((prev) => [
            ...prev,
            { role: "agent", text: `Failed to fetch tools: ${String(error)}` }
          ]);
        }
      } else {
        setMessages((prev) => [
          ...prev,
          { role: "agent", text: "MCP Client is not connected yet." }
        ]);
      }
      return;
    }

    // Send normal messages to the AI API
    try {
      const response = await fetch(`${AICON_SERVER_URL}/api/generate`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ prompt: userMessage }),
      });

      if (!response.ok) {
        let errorText = `API error: ${response.statusText}`;
        try {
          const errorData = await response.json();
          if (errorData.error) {
            if (typeof errorData.error === "string") {
              try {
                const parsedError = JSON.parse(errorData.error);
                if (parsedError.error && parsedError.error.message) {
                  errorText = parsedError.error.message;
                } else {
                  errorText = errorData.error;
                }
              } catch (e) {
                errorText = errorData.error;
              }
            } else if (errorData.error.message) {
              errorText = errorData.error.message;
            } else {
              errorText = JSON.stringify(errorData.error);
            }
          } else if (errorData.message) {
            errorText = errorData.message;
          }
        } catch (e) {
          // Fallback to status text if response is not JSON
        }
        throw new Error(errorText);
      }

      const data = await response.json();
      // Pass the entire data object as a JSON string so MessageContent can parse functionCalls and text
      const aiText = typeof data === "object" ? JSON.stringify(data) : String(data);

      setMessages((prev) => [
        ...prev,
        { role: "agent", text: aiText }
      ]);
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        { role: "agent", text: `Error communicating with AI agent: ${String(error)}` }
      ]);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Container maxWidth="md" sx={{ height: "100%", display: "flex", flexDirection: "column", pt: 4, pb: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box display="flex" alignItems="center" gap={2}>
          <Typography variant="h4" component="h1" sx={{ fontWeight: "bold" }}>
            AIcon Agent
          </Typography>
          {isConnecting ? (
            <Chip
              icon={<CircularProgress size={14} color="inherit" />}
              label="Connecting..."
              color="warning"
              size="small"
              variant="outlined"
            />
          ) : isConnected ? (
            <Chip
              label="Connected (MCP)"
              color="success"
              size="small"
              variant="outlined"
            />
          ) : (
            <Chip
              label="Disconnected"
              color="error"
              size="small"
              variant="outlined"
            />
          )}
        </Box>
        {!isConnected && !isConnecting && (
          <Button
            size="small"
            variant="outlined"
            color="primary"
            startIcon={<RefreshIcon />}
            onClick={connectMcp}
          >
            Reconnect
          </Button>
        )}
      </Box>
      
      {/* Canvas / Message History */}
      <Paper 
        elevation={0}
        sx={{
          flex: 1, 
          overflowY: "auto", 
          mb: 2, 
          p: 2, 
          display: "flex", 
          flexDirection: "column", 
          gap: 2,
          bgcolor: "background.default",
          border: 1,
          borderColor: "divider",
          borderRadius: 2
        }}
      >
        {messages.map((msg, index) => (
          <Box
            key={index}
            sx={{
              alignSelf: msg.role === "user" ? "flex-end" : msg.role === "system" ? "center" : "flex-start",
              bgcolor: msg.role === "user" 
                ? "primary.main" 
                : msg.role === "system" 
                  ? "grey.200" 
                  : "background.paper",
              color: msg.role === "user" 
                ? "primary.contrastText" 
                : msg.role === "system" 
                  ? "text.secondary" 
                  : "text.primary",
              p: 2,
              borderRadius: 2,
              maxWidth: msg.role === "system" ? "95%" : "80%",
              boxShadow: msg.role === "system" ? 0 : 1,
              fontStyle: msg.role === "system" ? "italic" : "normal",
              fontSize: msg.role === "system" ? "0.875rem" : "1rem",
            }}
          >
            <MessageContent text={msg.text} role={msg.role} />
          </Box>
        ))}
      </Paper>

      {/* Input Area */}
      <Box sx={{ display: "flex", alignItems: "flex-end", gap: 1 }}>
        <TextField
          fullWidth
          multiline
          maxRows={4}
          variant="outlined"
          placeholder="Message AIcon Agent... (e.g. /tools or /call tool_name {args})"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          sx={{
            "& .MuiOutlinedInput-root": {
              borderRadius: 4,
            }
          }}
        />
        <IconButton 
          color="primary" 
          onClick={handleSend} 
          disabled={!input.trim()}
          sx={{ 
            bgcolor: "primary.main", 
            color: "primary.contrastText",
            "&:hover": {
              bgcolor: "primary.dark",
            },
            mb: 0.5
          }}
        >
          <SendIcon />
        </IconButton>
      </Box>
    </Container>
  );
}