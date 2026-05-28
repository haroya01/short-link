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
 * Forward the request to {@code origin}. Cloudflare's fetch() automatically sets the Host
 * header to match the target URL's hostname (app.kurl.me / origin.kurl.me) — we can't preserve
 * the original kurl.me host on the upstream connection, so instead we rewrite leaked hostnames
 * in the response headers below.
 *
 * <p>The HTML body itself is generated using {@code NEXT_PUBLIC_SITE_URL=https://kurl.me} on
 * Vercel, so canonical / og:url / alternate hreflang inside the HTML are all already kurl.me.
 * The only place upstream's own hostname leaks is the {@code Link} response header that
 * next-intl middleware generates from the request hostname — we rewrite that here.
 *
 * <p>{@code redirect: "manual"} keeps backend 302s (short-link redirects) flowing through
 * to the browser unchanged. Default {@code redirect: "follow"} would have the Worker chase
 * the redirect itself and return the destination's body to the client — wrong for short links.
 */
async function proxy(request: Request, origin: string): Promise<Response> {
  const url = new URL(request.url);
  const target = `${origin}${url.pathname}${url.search}`;
  const headers = new Headers(request.headers);
  headers.set("x-forwarded-host", url.host);
  headers.set("x-forwarded-proto", url.protocol.replace(":", ""));
  let response: Response;
  try {
    response = await fetch(target, {
      method: request.method,
      headers,
      body: request.body,
      redirect: "manual",
    });
  } catch (err) {
    // Upstream origin unreachable (DNS, TCP, TLS, connect timeout). Without this catch the Worker
    // bubbles its own 1101 / 1042 page — a Cloudflare-branded mystery surface that breaks both
    // short-link redirects (user clicked a link, gets opaque error) and frontend error boundaries
    // (no JSON to parse). Fail closed with a deterministic 503 so downstream UX is predictable.
    return upstreamUnavailableResponse(url);
  }
  return rewriteHostnameHeaders(response, new URL(origin).host, url.host);
}

function upstreamUnavailableResponse(url: URL): Response {
  const status = 503;
  const isApi = url.pathname.startsWith("/api/");
  const headers = new Headers({
    "cache-control": "no-store",
    "retry-after": "30",
  });
  if (isApi) {
    headers.set("content-type", "application/problem+json");
    const body = JSON.stringify({
      type: "about:blank",
      title: "Service Unavailable",
      status,
      detail: "upstream origin unreachable",
      instance: url.pathname,
    });
    return new Response(body, { status, headers });
  }
  headers.set("content-type", "text/html; charset=utf-8");
  const body =
    '<!doctype html><html lang="en"><head><meta charset="utf-8">' +
    "<title>503 — Service Unavailable</title></head><body>" +
    "<h1>503</h1><p>The service is temporarily unavailable. " +
    "Please retry in a few moments.</p></body></html>";
  return new Response(body, { status, headers });
}

/**
 * Rewrite the upstream's own hostname back to the visitor-facing one in response headers that
 * commonly carry absolute URLs (Link for hreflang alternates, Location for redirects). Google
 * uses the Link header for hreflang signals — leaking app.kurl.me there while the HTML's
 * rel="alternate" said kurl.me produced canonical confusion that blocked indexing.
 */
function rewriteHostnameHeaders(
  response: Response,
  upstreamHost: string,
  visitorHost: string,
): Response {
  if (upstreamHost === visitorHost) return response;
  const headers = new Headers(response.headers);
  let mutated = false;
  for (const name of ["link", "location"] as const) {
    const value = headers.get(name);
    if (value && value.includes(upstreamHost)) {
      headers.set(name, value.replaceAll(upstreamHost, visitorHost));
      mutated = true;
    }
  }
  if (!mutated) return response;
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
