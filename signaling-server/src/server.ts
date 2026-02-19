import { WebSocketServer, WebSocket, RawData } from "ws";

type MessageType = "offer" | "answer" | "ice" | "hello";

interface SignalingMessage {
  type: MessageType;
  payload: unknown;
}

const PORT = 3001;

const rooms = new Map<string, Set<WebSocket>>();
const socketToRoom = new Map<WebSocket, string>();

const wss = new WebSocketServer({ port: PORT });

const getRoomIdFromUrl = (url?: string): string => {
  if (!url) {
    return "default";
  }

  try {
    const parsed = new URL(url, `ws://localhost:${PORT}`);
    return parsed.searchParams.get("room") || "default";
  } catch (error) {
    console.error("Failed to parse connection URL:", { url, error });
    return "default";
  }
};

const getRoomSize = (roomId: string): number => rooms.get(roomId)?.size ?? 0;

const isValidSignalingMessage = (value: unknown): value is SignalingMessage => {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  const validTypes: MessageType[] = ["offer", "answer", "ice", "hello"];

  return validTypes.includes(candidate.type as MessageType) && "payload" in candidate;
};

wss.on("connection", (socket, request) => {
  const roomId = getRoomIdFromUrl(request.url);

  if (!rooms.has(roomId)) {
    rooms.set(roomId, new Set<WebSocket>());
  }

  rooms.get(roomId)?.add(socket);
  socketToRoom.set(socket, roomId);

  console.log(`Client connected. room=${roomId}, clients=${getRoomSize(roomId)}`);

  socket.on("message", (data: RawData) => {
    try {
      const text = typeof data === "string" ? data : data.toString("utf-8");
      const parsed: unknown = JSON.parse(text);

      if (!isValidSignalingMessage(parsed)) {
        console.error("Invalid signaling message format:", parsed);
        return;
      }

      const peers = rooms.get(roomId);
      if (!peers) {
        return;
      }

      const message = JSON.stringify(parsed);
      for (const peer of peers) {
        if (peer !== socket && peer.readyState === WebSocket.OPEN) {
          peer.send(message);
        }
      }
    } catch (error) {
      console.error("Failed to process incoming message:", {
        roomId,
        error,
        rawData: data.toString()
      });
    }
  });

  socket.on("close", () => {
    const targetRoom = socketToRoom.get(socket);
    if (!targetRoom) {
      return;
    }

    const peers = rooms.get(targetRoom);
    peers?.delete(socket);
    socketToRoom.delete(socket);

    if (peers && peers.size === 0) {
      rooms.delete(targetRoom);
    }

    console.log(`Client disconnected. room=${targetRoom}, clients=${getRoomSize(targetRoom)}`);
  });

  socket.on("error", (error) => {
    console.error("Socket error:", { roomId, error });
  });
});

wss.on("listening", () => {
  console.log(`Signaling server started on ws://localhost:${PORT}`);
});

wss.on("error", (error) => {
  console.error("WebSocket server error:", error);
});
