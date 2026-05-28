import { describe, expect, it } from "vitest";
import { routeFor } from "../src/router";

describe("routeFor", () => {
  describe("backend paths", () => {
    it("REST API → backend", () => {
      expect(routeFor("/api/v1/links")).toBe("backend");
      expect(routeFor("/api/v1/users/me")).toBe("backend");
    });

    it("actuator → backend", () => {
      expect(routeFor("/actuator/health")).toBe("backend");
    });

    it("OAuth2 authorization endpoint → backend", () => {
      expect(routeFor("/oauth2/authorization/google")).toBe("backend");
    });

    it("OAuth2 callback → backend (registered redirect URI)", () => {
      expect(routeFor("/login/oauth2/code/google")).toBe("backend");
      expect(routeFor("/login/oauth2/code/kakao")).toBe("backend");
    });

    it("short code (7 chars) → backend", () => {
      expect(routeFor("/abc1234")).toBe("backend");
      expect(routeFor("/AbCd123")).toBe("backend");
    });

    it("short code at min length (3) → backend", () => {
      expect(routeFor("/abc")).toBe("backend");
    });

    it("short code at max length (16) → backend", () => {
      expect(routeFor("/" + "a".repeat(16))).toBe("backend");
    });

    it("short code with trailing slash → backend", () => {
      expect(routeFor("/abc1234/")).toBe("backend");
    });
  });

  describe("frontend paths", () => {
    it("root → frontend", () => {
      expect(routeFor("/")).toBe("frontend");
    });

    it("locale-prefixed pages → frontend", () => {
      expect(routeFor("/en")).toBe("frontend");
      expect(routeFor("/ko/login")).toBe("frontend");
      expect(routeFor("/ja/u/honggildong")).toBe("frontend");
    });

    it("profile page without locale → frontend", () => {
      expect(routeFor("/u/honggildong")).toBe("frontend");
    });

    it("Next.js static assets → frontend", () => {
      expect(routeFor("/_next/static/chunks/main.js")).toBe("frontend");
    });

    it("reserved app pages → frontend", () => {
      expect(routeFor("/login")).toBe("frontend");
      expect(routeFor("/dashboard")).toBe("frontend");
      expect(routeFor("/admin")).toBe("frontend");
      expect(routeFor("/settings")).toBe("frontend");
      expect(routeFor("/pricing")).toBe("frontend");
      expect(routeFor("/about")).toBe("frontend");
      expect(routeFor("/profile/leads")).toBe("frontend");
      expect(routeFor("/demo")).toBe("frontend");
      expect(routeFor("/showcase")).toBe("frontend");
      expect(routeFor("/campaigns")).toBe("frontend");
      expect(routeFor("/campaigns/new")).toBe("frontend");
      expect(routeFor("/qr-campaigns")).toBe("frontend");
      expect(routeFor("/visual-fixtures/mobile")).toBe("frontend");
    });

    it("/monitoring → frontend (Sentry tunnel route to bypass ad-blockers)", () => {
      expect(routeFor("/monitoring")).toBe("frontend");
      expect(routeFor("/monitoring/envelope")).toBe("frontend");
    });

    it("/learn → frontend (SEO FAQ page)", () => {
      expect(routeFor("/learn")).toBe("frontend");
      expect(routeFor("/ko/learn")).toBe("frontend");
    });

    it("public assets → frontend", () => {
      expect(routeFor("/favicon.ico")).toBe("frontend");
      expect(routeFor("/icon.svg")).toBe("frontend");
      expect(routeFor("/manifest.json")).toBe("frontend");
      expect(routeFor("/opengraph-image")).toBe("frontend");
      expect(routeFor("/sitemap.xml")).toBe("frontend");
      expect(routeFor("/robots.txt")).toBe("frontend");
    });
  });

  describe("precedence — reserved-word collision", () => {
    it("'login' would match short-code regex but goes to frontend", () => {
      // 5 chars alnum → matches /^[0-9A-Za-z]{3,16}$/ but reserved frontend path wins.
      expect(routeFor("/login")).toBe("frontend");
    });

    it("'/login/oauth2/code/...' goes to backend even though /login is a frontend prefix", () => {
      // OAuth callback MUST hit backend. Backend prefix /login/oauth2 is matched before the
      // generic frontend /login pattern.
      expect(routeFor("/login/oauth2/code/google")).toBe("backend");
    });

    it("'u' alone is too short for short code so falls to frontend default", () => {
      expect(routeFor("/u")).toBe("frontend");
    });

    it("'about' (5 alnum) — frontend takes priority", () => {
      expect(routeFor("/about")).toBe("frontend");
    });

    it("'campaigns' (9 alnum) — frontend takes priority", () => {
      expect(routeFor("/campaigns")).toBe("frontend");
    });
  });

  describe("edge cases", () => {
    it("path with dash is not a short code (regex excludes dashes) → frontend fallback", () => {
      expect(routeFor("/abc-123")).toBe("frontend");
    });

    it("path under 3 chars → frontend fallback", () => {
      expect(routeFor("/ab")).toBe("frontend");
    });

    it("path over 16 chars without locale → frontend fallback", () => {
      expect(routeFor("/" + "a".repeat(17))).toBe("frontend");
    });

    it("deeply nested unknown path → frontend (so Next.js 404 renders)", () => {
      expect(routeFor("/unknown/deep/path")).toBe("frontend");
    });
  });
});
