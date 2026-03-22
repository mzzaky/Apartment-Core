/**
 * ApartmentCore Pro – License Validation API
 * ============================================
 * Cloudflare Worker that validates license keys against a Supabase database.
 *
 * Environment variables (set in Cloudflare dashboard → Worker → Settings → Variables):
 *   SUPABASE_URL        – e.g. https://xxxx.supabase.co
 *   SUPABASE_SERVICE_KEY – service_role key (NOT the anon key)
 *
 * Endpoints:
 *   POST /api/validate   – Validate a license key
 *   GET  /api/version    – Get latest plugin version metadata
 *   GET  /api/health     – Health check
 *
 * Free tier: 100,000 requests/day.
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS headers
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };

    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // ── Routes ──────────────────────────────────────────────────────
    if (url.pathname === "/api/health" && request.method === "GET") {
      return json({ status: "ok", service: "apartmentcore-license" }, 200, corsHeaders);
    }

    if (url.pathname === "/api/validate" && request.method === "POST") {
      return handleValidate(request, env, corsHeaders);
    }

    if (url.pathname === "/api/version" && request.method === "GET") {
      return handleVersion(url, env, corsHeaders);
    }

    return json({ error: "Not found" }, 404, corsHeaders);
  },
};

// ── Validate endpoint ─────────────────────────────────────────────────

async function handleValidate(request, env, corsHeaders) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ valid: false, error: "Invalid JSON body" }, 400, corsHeaders);
  }

  const { license_key, server_ip, server_port, server_id, plugin_version } = body;

  if (!license_key || typeof license_key !== "string") {
    return json({ valid: false, error: "Missing license_key" }, 400, corsHeaders);
  }

  const supabaseUrl = env.SUPABASE_URL;
  const supabaseKey = env.SUPABASE_SERVICE_KEY;

  if (!supabaseUrl || !supabaseKey) {
    return json({ valid: false, error: "Server misconfigured" }, 500, corsHeaders);
  }

  try {
    // 1. Look up the license
    const licenseRes = await supabaseGet(
      supabaseUrl,
      supabaseKey,
      `/rest/v1/licenses?license_key=eq.${encodeURIComponent(license_key)}&select=*`
    );

    if (!licenseRes.ok) {
      await logValidation(supabaseUrl, supabaseKey, null, license_key, server_id, server_ip, "error", "Database error");
      return json({ valid: false, error: "Database error" }, 500, corsHeaders);
    }

    const licenses = await licenseRes.json();

    if (licenses.length === 0) {
      await logValidation(supabaseUrl, supabaseKey, null, license_key, server_id, server_ip, "invalid", "Key not found");
      return json({ valid: false, status: "invalid", message: "License key not found" }, 200, corsHeaders);
    }

    const license = licenses[0];

    // 2. Check status
    if (license.status === "revoked" || license.status === "suspended") {
      await logValidation(supabaseUrl, supabaseKey, license.id, license_key, server_id, server_ip, "revoked", license.status);
      return json({ valid: false, status: license.status, message: `License is ${license.status}` }, 200, corsHeaders);
    }

    // 3. Check expiration
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      // Update status to expired
      await supabasePatch(supabaseUrl, supabaseKey,
        `/rest/v1/licenses?id=eq.${license.id}`,
        { status: "expired" }
      );
      await logValidation(supabaseUrl, supabaseKey, license.id, license_key, server_id, server_ip, "expired", "License expired");
      return json({ valid: false, expired: true, status: "expired", message: "License has expired" }, 200, corsHeaders);
    }

    // 4. Check server activation count
    const activationsRes = await supabaseGet(
      supabaseUrl,
      supabaseKey,
      `/rest/v1/license_activations?license_id=eq.${license.id}&is_active=eq.true&select=id,server_id`
    );
    const activations = activationsRes.ok ? await activationsRes.json() : [];
    const existingActivation = activations.find(a => a.server_id === server_id);

    if (!existingActivation && activations.length >= license.max_servers) {
      await logValidation(supabaseUrl, supabaseKey, license.id, license_key, server_id, server_ip, "over_limit",
        `${activations.length}/${license.max_servers} servers`);
      return json({
        valid: false,
        status: "over_limit",
        message: `Maximum server limit reached (${license.max_servers}). Deactivate another server first.`
      }, 200, corsHeaders);
    }

    // 5. Upsert activation
    await supabasePost(supabaseUrl, supabaseKey,
      `/rest/v1/license_activations`,
      {
        license_id: license.id,
        server_id: server_id || "unknown",
        server_ip: server_ip || "0.0.0.0",
        server_port: server_port || 25565,
        plugin_version: plugin_version || "unknown",
        last_seen_at: new Date().toISOString(),
        is_active: true,
      },
      true // upsert
    );

    // 6. Log success
    await logValidation(supabaseUrl, supabaseKey, license.id, license_key, server_id, server_ip, "valid", "OK");

    return json({
      valid: true,
      status: "active",
      message: "License validated successfully",
      expires_at: license.expires_at,
      max_servers: license.max_servers,
    }, 200, corsHeaders);

  } catch (err) {
    return json({ valid: false, error: "Internal error: " + err.message }, 500, corsHeaders);
  }
}

// ── Version endpoint ──────────────────────────────────────────────────

async function handleVersion(url, env, corsHeaders) {
  const pluginName = url.searchParams.get("plugin") || "ApartmentCore";
  const supabaseUrl = env.SUPABASE_URL;
  const supabaseKey = env.SUPABASE_SERVICE_KEY;

  if (!supabaseUrl || !supabaseKey) {
    return json({ error: "Server misconfigured" }, 500, corsHeaders);
  }

  try {
    const res = await supabaseGet(
      supabaseUrl,
      supabaseKey,
      `/rest/v1/plugin_metadata?plugin_name=eq.${encodeURIComponent(pluginName)}&select=latest_version,download_url,changelog_summary&limit=1`
    );

    if (!res.ok) {
      return json({ error: "Database error" }, 500, corsHeaders);
    }

    const rows = await res.json();
    if (rows.length === 0) {
      return json({ error: "Plugin not found" }, 404, corsHeaders);
    }

    const meta = rows[0];
    return json({
      latest_version: meta.latest_version,
      download_url: meta.download_url || null,
      changelog_summary: meta.changelog_summary || null,
    }, 200, corsHeaders);

  } catch (err) {
    return json({ error: "Internal error: " + err.message }, 500, corsHeaders);
  }
}

// ── Supabase helpers ──────────────────────────────────────────────────

async function supabaseGet(url, key, path) {
  return fetch(`${url}${path}`, {
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
    },
  });
}

async function supabasePatch(url, key, path, body) {
  return fetch(`${url}${path}`, {
    method: "PATCH",
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify(body),
  });
}

async function supabasePost(url, key, path, body, upsert = false) {
  const headers = {
    apikey: key,
    Authorization: `Bearer ${key}`,
    "Content-Type": "application/json",
    Prefer: upsert ? "resolution=merge-duplicates,return=minimal" : "return=minimal",
  };
  return fetch(`${url}${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

async function logValidation(url, key, licenseId, licenseKey, serverId, serverIp, result, message) {
  try {
    await supabasePost(url, key, "/rest/v1/validation_log", {
      license_id: licenseId,
      license_key: licenseKey,
      server_id: serverId || "unknown",
      server_ip: serverIp || "unknown",
      result,
      message,
    });
  } catch {
    // Don't fail validation if logging fails
  }
}

// ── Utility ───────────────────────────────────────────────────────────

function json(data, status, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}
