// deno-lint-ignore-file no-explicit-any
//
// send-push-notification — Edge Function
//
// Architecture : appelee uniquement par le cron pg_cron via pg_net,
// avec un JWT service_role en Authorization. Refuse tout autre caller.
//
// Input shape (JSON body) :
// {
//   "outbox_id": "<uuid>",
//   "user_ids":  ["<uuid>", ...],
//   "title":     string,
//   "body":      string,
//   "data":      object
// }
//
// Comportement :
//   1. Verifie role = 'service_role' dans le JWT.
//   2. Resout les push_token a partir de user_ids (filtre ExponentPushToken).
//   3. Envoie a l'API Expo Push.
//   4. Pour chaque ticket "DeviceNotRegistered" / "InvalidCredentials" :
//      DELETE le device correspondant pour cesser de pousser dans le vide.
//   5. Retourne { sent, errors, dead_tokens_purged }.
//
// Auth interne : pas de secret partage en plus du JWT service_role,
// car le JWT service_role est lui-meme un secret (stocke dans Vault).

// Declaration Deno pour l'IDE TypeScript (le runtime Deno expose
// ces globals en realite). Edit local pour eviter les diagnostics
// "Cannot find name 'Deno'" sans tsconfig dedie aux Edge Functions.
declare const Deno: {
  serve(handler: (req: Request) => Response | Promise<Response>): void;
  env: { get(key: string): string | undefined };
};

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface PushPayload {
  outbox_id?: string;
  user_ids: string[];
  title: string;
  body: string;
  data?: Record<string, unknown>;
}

interface ExpoMessage {
  to: string;
  title: string;
  body: string;
  data?: Record<string, unknown>;
  sound: "default";
}

interface ExpoTicket {
  status: "ok" | "error";
  message?: string;
  details?: { error?: string };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

/**
 * Decode tres permissif d'un JWT (pas de verif signature ici car
 * Supabase verifie deja le JWT en amont si verify_jwt = true). On lit
 * juste le claim `role` pour s'assurer que c'est bien service_role.
 */
function jwtRole(authHeader: string | null): string | null {
  if (!authHeader?.startsWith("Bearer ")) return null;
  const parts = authHeader.slice(7).split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.role === "string" ? payload.role : null;
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  // ─── Auth : exiger un JWT service_role ──────────────────────
  const role = jwtRole(req.headers.get("Authorization"));
  if (role !== "service_role") {
    console.warn(`[send-push-notification] Unauthorized caller (role=${role ?? "null"})`);
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  // ─── Parse payload ──────────────────────────────────────────
  let payload: PushPayload;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON" }, 400);
  }

  const { outbox_id, user_ids, title, body, data } = payload;
  if (!Array.isArray(user_ids) || user_ids.length === 0 || !title || !body) {
    return jsonResponse({ error: "user_ids (non-empty array), title, body sont requis" }, 400);
  }

  // ─── Client service_role pour bypass RLS ────────────────────
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SERVICE_ROLE_KEY") ?? "",
  );

  // ─── Resoudre les push_token ────────────────────────────────
  const { data: devices, error: devicesError } = await supabase
    .from("devices")
    .select("push_token")
    .in("user_id", user_ids);

  if (devicesError) {
    console.error(
      `[send-push-notification] Erreur lecture devices (outbox=${outbox_id}):`,
      devicesError.message,
    );
    return jsonResponse({ error: devicesError.message }, 500);
  }

  const tokens: string[] = (devices ?? [])
    .map((d: { push_token: string }) => d.push_token)
    .filter((t: string) => typeof t === "string" && t.startsWith("ExponentPushToken["));

  if (tokens.length === 0) {
    return jsonResponse({
      outbox_id,
      sent: 0,
      errors: [],
      dead_tokens_purged: 0,
    });
  }

  // ─── Envoi a l'API Expo Push ────────────────────────────────
  const messages: ExpoMessage[] = tokens.map((to) => ({
    to,
    title,
    body,
    sound: "default",
    ...(data ? { data } : {}),
  }));

  let tickets: ExpoTicket[] = [];
  try {
    const expoResponse = await fetch("https://exp.host/--/api/v2/push/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Accept-Encoding": "gzip, deflate",
      },
      body: JSON.stringify(messages),
    });

    const expoResult = (await expoResponse.json()) as { data?: ExpoTicket[] };
    tickets = Array.isArray(expoResult.data) ? expoResult.data : [];
  } catch (err) {
    console.error(`[send-push-notification] Exception fetch Expo (outbox=${outbox_id}):`, err);
    return jsonResponse({ error: "Expo Push API unreachable" }, 502);
  }

  // ─── Tri tickets : ok / errors / dead tokens ────────────────
  let sent = 0;
  const errors: Array<{ token: string; message: string }> = [];
  const deadTokens: string[] = [];

  tickets.forEach((ticket, idx) => {
    const token = tokens[idx] ?? "unknown";
    if (ticket.status === "ok") {
      sent++;
    } else {
      const errCode = ticket.details?.error ?? "";
      const message = ticket.message ?? "Erreur inconnue";
      errors.push({ token, message });
      // Codes Expo qui indiquent un device a purger
      if (errCode === "DeviceNotRegistered" || errCode === "InvalidCredentials") {
        deadTokens.push(token);
      }
    }
  });

  // ─── Purge des tokens morts ─────────────────────────────────
  let deadPurged = 0;
  if (deadTokens.length > 0) {
    const { error: purgeError, count } = await supabase
      .from("devices")
      .delete({ count: "exact" })
      .in("push_token", deadTokens);

    if (purgeError) {
      console.error(
        `[send-push-notification] Erreur purge devices (outbox=${outbox_id}):`,
        purgeError.message,
      );
    } else {
      deadPurged = count ?? 0;
    }
  }

  console.log(
    `[send-push-notification] outbox=${outbox_id} sent=${sent} errors=${errors.length} dead_purged=${deadPurged}`,
  );

  return jsonResponse({
    outbox_id,
    sent,
    errors,
    dead_tokens_purged: deadPurged,
  });
});
