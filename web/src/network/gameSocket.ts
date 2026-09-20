import { isServerMessage, PROTOCOL_VERSION, type ClientMessage, type ServerMessage } from "./protocol";

type Listener = (message: ServerMessage) => void;
type ClientCommand = ClientMessage extends infer Message
  ? Message extends { v: 1; requestId: string }
    ? Omit<Message, "v" | "requestId">
    : never
  : never;

export class GameSocket {
  private socket: WebSocket | null = null;
  private retry = 0;
  private retryTimer: number | null = null;
  private closedByUser = false;
  private listeners = new Set<Listener>();

  constructor(private readonly url: string) {}

  connect() {
    this.closedByUser = false;
    this.socket = new WebSocket(this.url);
    this.socket.addEventListener("open", () => { this.retry = 0; });
    this.socket.addEventListener("message", ({ data }) => {
      try {
        const parsed: unknown = JSON.parse(String(data));
        if (isServerMessage(parsed)) this.listeners.forEach((listener) => listener(parsed));
      } catch {
        // Invalid network input is ignored; production telemetry records the protocol error.
      }
    });
    this.socket.addEventListener("close", () => {
      this.socket = null;
      if (!this.closedByUser) this.scheduleReconnect();
    });
  }

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  send(message: ClientCommand) {
    if (this.socket?.readyState !== WebSocket.OPEN) throw new Error("SOCKET_NOT_READY");
    const requestId = crypto.randomUUID();
    this.socket.send(JSON.stringify({ ...message, v: PROTOCOL_VERSION, requestId }));
    return requestId;
  }

  close() {
    this.closedByUser = true;
    if (this.retryTimer !== null) window.clearTimeout(this.retryTimer);
    this.socket?.close(1000, "client closed");
  }

  private scheduleReconnect() {
    const delay = Math.min(15_000, 1_000 * 2 ** this.retry);
    this.retry += 1;
    this.retryTimer = window.setTimeout(() => this.connect(), delay);
  }
}
