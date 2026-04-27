// deno-lint-ignore-file no-explicit-any
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface PushPayload {
  userIds: string[];
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

Deno.serve(async (req: Request) => {
  // Preflight CORS
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { userIds, title, body, data }: PushPayload = await req.json();

    if (!Array.isArray(userIds) || userIds.length === 0) {
      return new Response(JSON.stringify({ error: "userIds doit être un tableau non vide." }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Client Supabase avec service_role pour bypasser RLS
    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SERVICE_ROLE_KEY") ?? "",
    );

    // Récupère les tokens des appareils des utilisateurs ciblés
    const { data: devices, error: devicesError } = await supabaseAdmin
      .from("devices")
      .select("fcm_token")
      .in("user_id", userIds);

    if (devicesError) {
      console.error("[send-push-notification] Erreur lecture devices:", devicesError.message);
      return new Response(JSON.stringify({ error: devicesError.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (!devices || devices.length === 0) {
      return new Response(JSON.stringify({ sent: 0, errors: [] }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Filtre les tokens Expo Push valides
    const tokens: string[] = devices
      .map((d: { fcm_token: string }) => d.fcm_token)
      .filter((t: string) => t.startsWith("ExponentPushToken["));

    if (tokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0, errors: [] }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Construction des messages Expo Push
    const messages: ExpoMessage[] = tokens.map((token) => ({
      to: token,
      title,
      body,
      sound: "default",
      ...(data ? { data } : {}),
    }));

    // Envoi à l'API Expo Push
    const expoResponse = await fetch("https://exp.host/--/api/v2/push/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        "Accept-Encoding": "gzip, deflate",
      },
      body: JSON.stringify(messages),
    });

    const expoResult: { data: Array<{ status: string; message?: string }> } =
      await expoResponse.json();

    const errors: Array<{ token: string; message: string }> = [];
    let sent = 0;

    if (Array.isArray(expoResult.data)) {
      expoResult.data.forEach((ticket, idx) => {
        if (ticket.status === "ok") {
          sent++;
        } else {
          errors.push({
            token: tokens[idx] ?? "unknown",
            message: ticket.message ?? "Erreur inconnue",
          });
        }
      });
    }

    console.log(`[send-push-notification] Envoyé: ${sent}, Erreurs: ${errors.length}`);

    return new Response(JSON.stringify({ sent, errors }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    console.error("[send-push-notification] Exception:", err);
    return new Response(JSON.stringify({ error: err?.message ?? "Erreur interne" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
