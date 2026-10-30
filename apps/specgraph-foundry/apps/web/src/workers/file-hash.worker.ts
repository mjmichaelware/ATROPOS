self.onmessage = async (event: MessageEvent<File>) => {
  const digest = await crypto.subtle.digest("SHA-256", await event.data.arrayBuffer());
  const hex = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  self.postMessage(hex);
};
