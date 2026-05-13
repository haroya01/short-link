import { routeFor } from "./router";

export interface Env {
  /** Backend origin (Spring Boot). Must be reachable from Workers — e.g. https://origin.kurl.me */
  BACKEND_ORIGIN: string;
  /** Frontend origin (Vercel Next.js). Typically https://app.kurl.me */
  FRONTEND_ORIGIN: string;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const origin = routeFor(url.pathname) === "backend" ? env.BACKEND_ORIGIN : env.FRONTEND_ORIGIN;
    return proxy(request, origin);
  },
};

/**
 * Forward the request to {@code origin}, rewriting the Host header so the target hostname
 * receives the request as if it were addressed there directly. {@code X-Forwarded-Host}
 * preserves the original kurl.me host so the backend / frontend can still build correct
 * absolute URLs (e.g. for redirects, og:url).
 *
 * <p>{@code redirect: "manual"} keeps backend 302s (short-link redirects) flowing through
 * to the browser unchanged. Default {@code redirect: "follow"} would have the Worker chase
 * the redirect itself and return the destination's body to the client — wrong for short links.
 */
async function proxy(request: Request, origin: string): Promise<Response> {
  const url = new URL(request.url);
  const target = `${origin}${url.pathname}${url.search}`;
  const headers = new Headers(request.headers);
  headers.set("host", new URL(origin).host);
  headers.set("x-forwarded-host", url.host);
  headers.set("x-forwarded-proto", url.protocol.replace(":", ""));
  return fetch(target, {
    method: request.method,
    headers,
    body: request.body,
    redirect: "manual",
  });
}
