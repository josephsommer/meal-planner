/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Origin of the deployed API, e.g. "http://compendium-api-env.us-east-1.elasticbeanstalk.com".
  // Unset in local dev, where the Vite dev server proxies /api to localhost:8080 instead
  // (see vite.config.ts), so requests stay relative and this is never read.
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
