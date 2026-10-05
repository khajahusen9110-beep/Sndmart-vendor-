import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// Only this database may call this function: every pg_net caller sends x-push-secret
// (public.internal_push_headers), checked against Vault via verify_internal_push_secret.
let verifiedSecret: string | null = null;

async function isAuthorized(req: Request): Promise<boolean> {
  const secret = req.headers.get("x-push-secret") ?? "";
  if (secret === "") return false;
  if (verifiedSecret !== null && secret === verifiedSecret) return true;
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    console.error("send-fcm-vendor-native: SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY missing");
    return false;
  }
  const res = await fetch(`${supabaseUrl}/rest/v1/rpc/verify_internal_push_secret`, {
    method: "POST",
    headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ p_secret: secret }),
  });
  if (!res.ok) {
    console.error("send-fcm-vendor-native: secret check failed", res.status, await res.text());
    return false;
  }
  const ok = (await res.json()) === true;
  if (ok) verifiedSecret = secret;
  return ok;
}

function base64UrlEncode(obj: unknown): string {
  const json = JSON.stringify(obj);
  const b64 = btoa(json);
  return b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToBinaryDer(pem: string): ArrayBuffer {
  const cleaned = pem.replace(/\\n/g, "\n");
  const match = cleaned.match(/-----BEGIN PRIVATE KEY-----([\s\S]*?)-----END PRIVATE KEY-----/);
  const body = (match ? match[1] : cleaned).replace(/[\r\n\s]/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const derBuffer = pemToBinaryDer(pem);
  return crypto.subtle.importKey("pkcs8", derBuffer, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

async function getAccessToken(clientEmail: string, privateKeyPem: string): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claimSet = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };
  const signingInput = `${base64UrlEncode(header)}.${base64UrlEncode(claimSet)}`;
  const key = await importPrivateKey(privateKeyPem);
  const signatureBuffer = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(signingInput));
  const signature = btoa(String.fromCharCode(...new Uint8Array(signatureBuffer)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${signingInput}.${signature}` }),
  });
  const tokenData = await tokenRes.json();
  if (!tokenRes.ok) throw new Error("OAuth token error: " + JSON.stringify(tokenData));
  return tokenData.access_token;
}

Deno.serve(async (req) => {
  try {
    if (!(await isAuthorized(req))) {
      return new Response(JSON.stringify({ error: { http_code: 401, message: "Unauthorized" } }),
        { status: 401, headers: { "Content-Type": "application/json" } });
    }

    const body = await req.json();
    const token = body.token;
    if (!token) throw new Error("token is required");

    const projectId = Deno.env.get("FIREBASE_VENDOR_PROJECT_ID");
    const clientEmail = Deno.env.get("FIREBASE_VENDOR_CLIENT_EMAIL");
    const privateKey = Deno.env.get("FIREBASE_VENDOR_PRIVATE_KEY");
    if (!projectId || !clientEmail || !privateKey) {
      throw new Error("Firebase vendor project secrets are not configured");
    }

    const accessToken = await getAccessToken(clientEmail, privateKey);

    const orderId = String(body.orderId ?? body.order_id ?? "");
    // Only a real delivery assignment carries an assignment id. Never substitute the order id:
    // the partner app would treat it as an assignment and call accept/pickup with a wrong id.
    const rawAssignmentId = body.assignment_id ?? body.assignmentId;
    const assignmentId = rawAssignmentId === undefined || rawAssignmentId === null ? "" : String(rawAssignmentId);

    // DATA-ONLY message so the app's onMessageReceived() always runs (foreground, background, killed).
    const data: Record<string, string> = {
      orderId,
      order_id: orderId,
      title: String(body.title ?? "New Order"),
      message: String(body.message ?? "You have a new order"),
      full_screen: String(body.full_screen ?? "true"),
    };
    if (assignmentId !== "") {
      data.assignmentId = assignmentId;
      data.assignment_id = assignmentId;
    }
    const passthroughKeys = [
      "store_name", "store_address", "customer_address", "payment_mode",
      "total_amount", "delivery_fee", "pickup_distance", "drop_distance",
      "expires_at", "city_id", "status", "type",
    ];
    for (const key of passthroughKeys) {
      if (body[key] !== undefined && body[key] !== null && body[key] !== "") {
        data[key] = String(body[key]);
      }
    }

    const fcmRes = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data, android: { priority: "high" } } }),
    });

    const fcmBody = await fcmRes.text();
    if (!fcmRes.ok) {
      console.error("FCM send failed:", fcmRes.status, fcmBody);
      return new Response(JSON.stringify({ error: { http_code: 500, message: "FCM send failed: " + fcmBody } }),
        { status: 500, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({ success: true, fcm_status: fcmRes.status }),
      { status: 200, headers: { "Content-Type": "application/json" } });
  } catch (e) {
    console.error("send-fcm-vendor-native error:", e);
    return new Response(JSON.stringify({ error: { http_code: 500, message: e instanceof Error ? e.message : String(e) } }),
      { status: 500, headers: { "Content-Type": "application/json" } });
  }
});
