import { requireScopes } from '@modelcontextprotocol/server';

type Scopes = readonly [string, ...string[]];

/**
 * Per-tool OAuth policy for ChatGPT and other MCP hosts.
 *
 * `scopeChallenge` makes the server answer an insufficient-scope call with a
 * proper RFC 6750 challenge instead of a generic tool error. ChatGPT also reads
 * the back-compat `_meta["securitySchemes"]` mirror on the tool descriptor;
 * the current MCP SDK strips unknown top-level fields, so the mirror is the
 * only place we can publish it.
 */
function oauthPolicy(scopes: Scopes) {
  return {
    scopeChallenge: requireScopes(...scopes),
    _meta: { securitySchemes: [{ type: 'oauth2', scopes: [...scopes] }] },
  };
}

/** Read-only tool: bounded to the authorized parent account, never mutates. */
export function readOnlyTool(title: string, scopes: Scopes) {
  return {
    title,
    annotations: {
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true,
      openWorldHint: false,
    },
    ...oauthPolicy(scopes),
  };
}

/**
 * Write tool. `destructive` is true when the action cannot be undone: a sent
 * message, a recorded quiz attempt, a permanent homework mark, a deletion.
 */
export function writeTool(title: string, scopes: Scopes, options: { destructive: boolean; idempotent?: boolean }) {
  return {
    title,
    annotations: {
      readOnlyHint: false,
      destructiveHint: options.destructive,
      idempotentHint: options.idempotent ?? false,
      openWorldHint: false,
    },
    ...oauthPolicy(scopes),
  };
}
