const listeners = new Set();
let messages = [];

export function readConsoleMessages() {
  return messages;
}

export function appendConsoleMessage(text, type = "info") {
  const entry = {
    id: messages.length + 1,
    text,
    type,
    timestamp: new Date().toISOString(),
  };
  messages = messages.concat(entry);
  listeners.forEach((listener) => listener(messages));
  return messages;
}

export function clearConsoleMessages() {
  messages = [];
  listeners.forEach((listener) => listener(messages));
}

export function subscribeConsole(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
