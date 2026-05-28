import { afterEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";

const env = {
  BACKEND_ORIGIN: "https://origin.kurl.me",
  FRONTEND_ORIGIN: "https://app.kurl.me",
};

describe("fail-closed when upstream unreachable", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("backend API path → 503 application/problem+json", async () => {
    vi.stubGlobal("fetch", () => Promise.reject(new TypeError("connect ECONNREFUSED")));
    const res = await worker.fetch(new Request("https://kurl.me/api/v1/links"), env);
    expect(res.status).toBe(503);
    expect(res.headers.get("content-type")).toBe("application/problem+json");
    expect(res.headers.get("retry-after")).toBe("30");
    expect(res.headers.get("cache-control")).toBe("no-store");
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.status).toBe(503);
    expect(body.detail).toBe("upstream origin unreachable");
    expect(body.instance).toBe("/api/v1/links");
  });

  it("short-code redirect path → 503 text/html", async () => {
    vi.stubGlobal("fetch", () => Promise.reject(new TypeError("dns failure")));
    const res = await worker.fetch(new Request("https://kurl.me/abc1234"), env);
    expect(res.status).toBe(503);
    expect(res.headers.get("content-type")).toContain("text/html");
    expect(res.headers.get("retry-after")).toBe("30");
    const body = await res.text();
    expect(body).toContain("503");
    expect(body).toContain("Service Unavailable");
  });

  it("frontend route (Vercel down) → 503 text/html", async () => {
    vi.stubGlobal("fetch", () => Promise.reject(new Error("upstream timeout")));
    const res = await worker.fetch(new Request("https://kurl.me/login"), env);
    expect(res.status).toBe(503);
    expect(res.headers.get("content-type")).toContain("text/html");
  });

  it("successful upstream response is passed through unchanged", async () => {
    vi.stubGlobal("fetch", () => Promise.resolve(new Response("ok", { status: 200 })));
    const res = await worker.fetch(new Request("https://kurl.me/api/v1/links"), env);
    expect(res.status).toBe(200);
    expect(await res.text()).toBe("ok");
  });
});
