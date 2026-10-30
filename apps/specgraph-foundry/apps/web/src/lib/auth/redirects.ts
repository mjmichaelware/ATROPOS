const CONTROL = /[\u0000-\u001f\u007f]/;

export function sanitizeReturnPath(value: string | null | undefined, fallback = "/projects") {
  if (!value || CONTROL.test(value) || value.includes("\\") || value.startsWith("//")) {
    return fallback;
  }
  try {
    const decoded = decodeURIComponent(value);
    if (decoded.startsWith("//") || decoded.slice(1).includes("//") || decoded.includes("\\") || /^[a-z][a-z0-9+.-]*:/i.test(decoded)) {
      return fallback;
    }
  } catch {
    return fallback;
  }
  if (!value.startsWith("/") || value.startsWith("/auth/callback")) {
    return fallback;
  }
  return value;
}

export function authErrorMessage(reason: string | null | undefined) {
  switch (reason) {
    case "callback":
      return "The authentication link could not be verified. Request a new link and try again.";
    case "expired":
      return "Your session expired. Sign in again to continue.";
    case "recovery":
      return "The recovery session is no longer valid. Request a new recovery email.";
    default:
      return "Authentication could not be completed safely.";
  }
}
