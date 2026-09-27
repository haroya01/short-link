import { routeFor } from "./router";

export interface Env {
  // Must be reachable from Workers, e.g. https://origin.kurl.me
  BACKEND_ORIGIN: string;
  FRONTEND_ORIGIN: string;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const origin = routeFor(url.pathname) === "backend" ? env.BACKEND_ORIGIN : env.FRONTEND_ORIGIN;
    return proxy(request, origin);
  },
};

// fetch() sets Host to the upstream hostname, so the upstream host can leak into the Link header
// that next-intl builds; it is rewritten below. redirect: "manual" passes short-link 302s through
// instead of the Worker following them and returning the destination body.
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
    // Without this the Worker returns Cloudflare's own 1101/1042 page, which neither redirects nor
    // gives the frontend JSON to handle. A plain 503 keeps failures predictable.
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

// Google reads hreflang from the Link header; app.kurl.me there conflicts with the HTML's kurl.me
// alternates and blocks indexing.
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
