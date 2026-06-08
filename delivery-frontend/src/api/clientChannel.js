import { getClientChannel } from "../utils/clientPlatform";

export function getClientChannelHeaderValue() {
  const ch = getClientChannel();
  if (ch === "max" || ch === "telegram" || ch === "web") return ch;
  return "unknown";
}
