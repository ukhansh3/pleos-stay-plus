const safeSegment = /^[A-Za-z0-9_-]+$/;

export function pickupProxyPath(path: string[]): string | null {
  return path.length > 1 && path[0] === "pickup" && path.every(part => safeSegment.test(part))
    ? path.join("/")
    : null;
}
