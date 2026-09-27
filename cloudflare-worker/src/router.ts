// Reserved frontend paths win over the short-code regex so a custom code such as "login" or "u"
// cannot shadow an app route.

export type Target = "backend" | "frontend";

// Backend paths. /oauth2/* and /login/oauth2/* are Spring Security's OAuth2 client flow —
// /login/oauth2/code/{provider} is the redirect URI registered with Google/Kakao, so it MUST
// reach the backend or the login round-trip fails with a 404 on Vercel.
const BACKEND_PATH = /^\/(api|actuator|oauth2|login\/oauth2)(\/|$)/;

// Reserved frontend prefixes. Order doesn't matter — any match wins. Keep this list in sync
// with the Next.js app/[locale] routes so a new top-level page (e.g. /pricing) doesn't get
// mistaken for a short code when the slug happens to be 3-16 chars alnum.
const FRONTEND_PATHS: RegExp[] = [
  /^\/(en|ko|ja)(\/|$)/,
  /^\/u\//,
  /^\/_next\//,
  /^\/(login|signup|dashboard|stats|admin|settings|pricing|about|terms|privacy|auth|profile|demo|showcase|monitoring|learn|campaigns|qr-campaigns|visual-fixtures)(\/|$)/,
  /^\/(favicon|icon|apple-touch-icon|manifest\.json|opengraph-image)/,
  /^\/(sitemap\.xml|robots\.txt)$/,
  /^\/$/,
];

const SHORT_CODE = /^\/[0-9A-Za-z]{3,16}\/?$/;

// Unknown paths go to the frontend so the Next.js 404 page renders instead of a plaintext one.
export function routeFor(path: string): Target {
  if (BACKEND_PATH.test(path)) return "backend";
  if (FRONTEND_PATHS.some((re) => re.test(path))) return "frontend";
  if (SHORT_CODE.test(path)) return "backend";
  return "frontend";
}
