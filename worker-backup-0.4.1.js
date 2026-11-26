var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// worker.js
var VERSION = "0.4.1";
var SESSION_DAYS = 7;
var PBKDF2_ITERATIONS = 1e5;
var RESEND_URL = "https://api.resend.com/emails";
var EMAIL_FROM = "NEXORA VPN <noreply@parsaprogrammer.ir>";
var EMAIL_TO = "parsaprogrammer6@gmail.com";
var SERVERS = [
  {
    id: "de-01",
    country: "Germany",
    name: "Germany 01",
    flag: "\u{1F1E9}\u{1F1EA}",
    status: "coming_soon"
  },
  {
    id: "nl-01",
    country: "Netherlands",
    name: "Netherlands 01",
    flag: "\u{1F1F3}\u{1F1F1}",
    status: "coming_soon"
  },
  {
    id: "fi-01",
    country: "Finland",
    name: "Finland 01",
    flag: "\u{1F1EB}\u{1F1EE}",
    status: "coming_soon"
  }
];
var MODES = [
  { id: "normal", name: "\u062D\u0627\u0644\u062A \u0639\u0627\u062F\u06CC" },
  { id: "game", name: "\u062D\u0627\u0644\u062A \u0628\u0627\u0632\u06CC" },
  { id: "social", name: "\u062D\u0627\u0644\u062A \u0627\u062C\u062A\u0645\u0627\u0639\u06CC" }
];
var encoder = new TextEncoder();
function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Cache-Control": "no-store"
    }
  });
}
__name(json, "json");
function safeErrorInfo(error, secrets = []) {
  let message = String(error?.message ?? error ?? "");
  for (const secret of secrets) {
    if (secret) {
      message = message.split(String(secret)).join("[redacted]");
    }
  }
  return {
    name: String(error?.name ?? "Error"),
    message: message.slice(0, 300)
  };
}
__name(safeErrorInfo, "safeErrorInfo");
function normalizeDigits(value) {
  return String(value ?? "").replace(/[۰-۹]/g, (d) => "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9".indexOf(d)).replace(/[٠-٩]/g, (d) => "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669".indexOf(d));
}
__name(normalizeDigits, "normalizeDigits");
function normalizePhone(value) {
  return normalizeDigits(value).trim().replace(/[\s()-]/g, "");
}
__name(normalizePhone, "normalizePhone");
function randomBytes(length) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}
__name(randomBytes, "randomBytes");
function toBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
__name(toBase64Url, "toBase64Url");
function fromBase64Url(value) {
  const base64 = String(value).replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - base64.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}
__name(fromBase64Url, "fromBase64Url");
async function sha256Base64Url(value) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    encoder.encode(value)
  );
  return toBase64Url(new Uint8Array(digest));
}
__name(sha256Base64Url, "sha256Base64Url");
async function makePasswordHash(password) {
  const salt = randomBytes(16);
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    "PBKDF2",
    false,
    ["deriveBits"]
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations: PBKDF2_ITERATIONS,
      hash: "SHA-256"
    },
    key,
    256
  );
  const hash = new Uint8Array(bits);
  return `pbkdf2$${PBKDF2_ITERATIONS}$${toBase64Url(salt)}$${toBase64Url(hash)}`;
}
__name(makePasswordHash, "makePasswordHash");
async function verifyPassword(password, storedHash) {
  try {
    const parts = String(storedHash).split("$");
    if (parts.length !== 4 || parts[0] !== "pbkdf2") {
      return false;
    }
    const iterations = Number(parts[1]);
    if (!Number.isInteger(iterations) || iterations < 1 || iterations > 1e6) {
      return false;
    }
    const salt = fromBase64Url(parts[2]);
    const expectedHash = fromBase64Url(parts[3]);
    const key = await crypto.subtle.importKey(
      "raw",
      encoder.encode(password),
      "PBKDF2",
      false,
      ["deriveBits"]
    );
    const bits = await crypto.subtle.deriveBits(
      {
        name: "PBKDF2",
        salt,
        iterations,
        hash: "SHA-256"
      },
      key,
      expectedHash.length * 8
    );
    const actualHash = new Uint8Array(bits);
    if (actualHash.length !== expectedHash.length) {
      return false;
    }
    let difference = 0;
    for (let i = 0; i < actualHash.length; i++) {
      difference |= actualHash[i] ^ expectedHash[i];
    }
    return difference === 0;
  } catch {
    return false;
  }
}
__name(verifyPassword, "verifyPassword");
async function createSession(env, userId) {
  const token = toBase64Url(randomBytes(32));
  const tokenHash = await sha256Base64Url(token);
  const expiresAt = new Date(
    Date.now() + SESSION_DAYS * 24 * 60 * 60 * 1e3
  ).toISOString();
  await env.DB.prepare(`
    INSERT INTO sessions (user_id, token_hash, expires_at)
    VALUES (?, ?, ?)
  `).bind(userId, tokenHash, expiresAt).run();
  return { token, expires_at: expiresAt };
}
__name(createSession, "createSession");
async function authenticatedUser(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return null;
  }
  const token = match[1].trim();
  if (!token || token.length > 500) {
    return null;
  }
  const tokenHash = await sha256Base64Url(token);
  const user = await env.DB.prepare(`
    SELECT
      users.id,
      users.phone,
      users.first_name,
      users.last_name,
      users.created_at
    FROM sessions
    INNER JOIN users ON users.id = sessions.user_id
    WHERE sessions.token_hash = ?
      AND sessions.expires_at > ?
    LIMIT 1
  `).bind(tokenHash, (/* @__PURE__ */ new Date()).toISOString()).first();
  return user || null;
}
__name(authenticatedUser, "authenticatedUser");
function publicUser(user) {
  return {
    id: user.id,
    phone: user.phone,
    first_name: user.first_name,
    last_name: user.last_name,
    created_at: user.created_at
  };
}
__name(publicUser, "publicUser");
async function handleRegister(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({
      success: false,
      message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0646\u0627\u0645\u0639\u062A\u0628\u0631 \u0627\u0633\u062A."
    }, 400);
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return json({
      success: false,
      message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0646\u0627\u0645\u0639\u062A\u0628\u0631 \u0627\u0633\u062A."
    }, 400);
  }
  const phone = normalizePhone(body.phone);
  const firstName = String(body.first_name ?? "").trim();
  const lastName = String(body.last_name ?? "").trim();
  const password = String(body.password ?? "");
  if (!/^\+?[0-9]{7,15}$/.test(phone)) {
    return json({
      success: false,
      message: "\u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A."
    }, 400);
  }
  if (firstName.length < 1 || firstName.length > 80 || lastName.length < 1 || lastName.length > 80) {
    return json({
      success: false,
      message: "\u0646\u0627\u0645 \u06CC\u0627 \u0646\u0627\u0645 \u062E\u0627\u0646\u0648\u0627\u062F\u06AF\u06CC \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A."
    }, 400);
  }
  if (password.length < 8 || password.length > 128) {
    return json({
      success: false,
      message: "\u0631\u0645\u0632 \u0639\u0628\u0648\u0631 \u0628\u0627\u06CC\u062F \u062D\u062F\u0627\u0642\u0644 \u06F8 \u0648 \u062D\u062F\u0627\u06A9\u062B\u0631 \u06F1\u06F2\u06F8 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631 \u0628\u0627\u0634\u062F."
    }, 400);
  }
  try {
    const existing = await env.DB.prepare(`
      SELECT id FROM users WHERE phone = ? LIMIT 1
    `).bind(phone).first();
    if (existing) {
      return json({
        success: false,
        message: "\u0627\u06CC\u0646 \u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646 \u0642\u0628\u0644\u0627\u064B \u062B\u0628\u062A \u0634\u062F\u0647 \u0627\u0633\u062A."
      }, 409);
    }
  } catch (error) {
    console.error("register: duplicate check failed", safeErrorInfo(error, [phone, password]));
    return json({
      success: false,
      message: "\u062B\u0628\u062A\u200C\u0646\u0627\u0645 \u0627\u0646\u062C\u0627\u0645 \u0646\u0634\u062F. \u0644\u0637\u0641\u0627\u064B \u06A9\u0645\u06CC \u0628\u0639\u062F \u062F\u0648\u0628\u0627\u0631\u0647 \u062A\u0644\u0627\u0634 \u06A9\u0646\u06CC\u062F."
    }, 500);
  }
  let passwordHash;
  try {
    passwordHash = await makePasswordHash(password);
  } catch (error) {
    console.error("register: password hashing failed", safeErrorInfo(error, [phone, password]));
    return json({
      success: false,
      message: "\u062B\u0628\u062A\u200C\u0646\u0627\u0645 \u0627\u0646\u062C\u0627\u0645 \u0646\u0634\u062F. \u0644\u0637\u0641\u0627\u064B \u06A9\u0645\u06CC \u0628\u0639\u062F \u062F\u0648\u0628\u0627\u0631\u0647 \u062A\u0644\u0627\u0634 \u06A9\u0646\u06CC\u062F."
    }, 500);
  }
  let userId;
  try {
    const result = await env.DB.prepare(`
      INSERT INTO users (phone, first_name, last_name, password_hash)
      SELECT ?, ?, ?, ?
      WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone = ?)
    `).bind(phone, firstName, lastName, passwordHash, phone).run();
    if (result.meta.changes === 0) {
      return json({
        success: false,
        message: "\u0627\u06CC\u0646 \u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646 \u0642\u0628\u0644\u0627\u064B \u062B\u0628\u062A \u0634\u062F\u0647 \u0627\u0633\u062A."
      }, 409);
    }
    userId = result.meta.last_row_id;
  } catch (error) {
    if (/UNIQUE constraint failed/i.test(String(error?.message ?? error))) {
      return json({
        success: false,
        message: "\u0627\u06CC\u0646 \u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646 \u0642\u0628\u0644\u0627\u064B \u062B\u0628\u062A \u0634\u062F\u0647 \u0627\u0633\u062A."
      }, 409);
    }
    console.error("register: user insert failed", safeErrorInfo(error, [phone, password, passwordHash]));
    return json({
      success: false,
      message: "\u062B\u0628\u062A\u200C\u0646\u0627\u0645 \u0627\u0646\u062C\u0627\u0645 \u0646\u0634\u062F. \u0644\u0637\u0641\u0627\u064B \u06A9\u0645\u06CC \u0628\u0639\u062F \u062F\u0648\u0628\u0627\u0631\u0647 \u062A\u0644\u0627\u0634 \u06A9\u0646\u06CC\u062F."
    }, 500);
  }
  try {
    const user = await env.DB.prepare(`
      SELECT id, phone, first_name, last_name, created_at
      FROM users
      WHERE id = ?
    `).bind(userId).first();
    if (!user) {
      throw new Error("user row not found after insert");
    }
    const session = await createSession(env, userId);
    return json({
      success: true,
      message: "\u062B\u0628\u062A\u200C\u0646\u0627\u0645 \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u0627\u0646\u062C\u0627\u0645 \u0634\u062F.",
      user: publicUser(user),
      token: session.token,
      expires_at: session.expires_at
    }, 201);
  } catch (error) {
    console.error("register: post-insert step failed", safeErrorInfo(error, [phone, password, passwordHash]));
    return json({
      success: false,
      account_created: true,
      message: "\u062D\u0633\u0627\u0628 \u0634\u0645\u0627 \u0627\u06CC\u062C\u0627\u062F \u0634\u062F \u0627\u0645\u0627 \u0648\u0631\u0648\u062F \u062E\u0648\u062F\u06A9\u0627\u0631 \u0627\u0646\u062C\u0627\u0645 \u0646\u0634\u062F. \u0644\u0637\u0641\u0627\u064B \u0628\u0627 \u0647\u0645\u06CC\u0646 \u0645\u0634\u062E\u0635\u0627\u062A \u0648\u0627\u0631\u062F \u0634\u0648\u06CC\u062F."
    }, 500);
  }
}
__name(handleRegister, "handleRegister");
async function handleLogin(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({
      success: false,
      message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0646\u0627\u0645\u0639\u062A\u0628\u0631 \u0627\u0633\u062A."
    }, 400);
  }
  const phone = normalizePhone(body.phone);
  const password = String(body.password ?? "");
  if (!/^\+?[0-9]{7,15}$/.test(phone) || password.length < 1 || password.length > 128) {
    return json({
      success: false,
      message: "\u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646 \u06CC\u0627 \u0631\u0645\u0632 \u0639\u0628\u0648\u0631 \u0627\u0634\u062A\u0628\u0627\u0647 \u0627\u0633\u062A."
    }, 401);
  }
  const user = await env.DB.prepare(`
    SELECT id, phone, first_name, last_name, password_hash, created_at
    FROM users
    WHERE phone = ?
    LIMIT 1
  `).bind(phone).first();
  if (!user || !await verifyPassword(password, user.password_hash)) {
    return json({
      success: false,
      message: "\u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646 \u06CC\u0627 \u0631\u0645\u0632 \u0639\u0628\u0648\u0631 \u0627\u0634\u062A\u0628\u0627\u0647 \u0627\u0633\u062A."
    }, 401);
  }
  const session = await createSession(env, user.id);
  return json({
    success: true,
    message: "\u0648\u0631\u0648\u062F \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u0627\u0646\u062C\u0627\u0645 \u0634\u062F.",
    user: publicUser(user),
    token: session.token,
    expires_at: session.expires_at
  });
}
__name(handleLogin, "handleLogin");
async function handleMe(request, env) {
  const user = await authenticatedUser(request, env);
  if (!user) {
    return json({
      success: false,
      message: "\u0646\u0634\u0633\u062A \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A. \u062F\u0648\u0628\u0627\u0631\u0647 \u0648\u0627\u0631\u062F \u0634\u0648\u06CC\u062F."
    }, 401);
  }
  return json({
    success: true,
    user: publicUser(user)
  });
}
__name(handleMe, "handleMe");
async function handleLogout(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return json({
      success: false,
      message: "\u062A\u0648\u06A9\u0646 \u0627\u0631\u0633\u0627\u0644 \u0646\u0634\u062F\u0647 \u0627\u0633\u062A."
    }, 401);
  }
  const token = match[1].trim();
  const tokenHash = await sha256Base64Url(token);
  await env.DB.prepare(`
    DELETE FROM sessions
    WHERE token_hash = ?
  `).bind(tokenHash).run();
  return json({
    success: true,
    message: "\u062E\u0631\u0648\u062C \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u0627\u0646\u062C\u0627\u0645 \u0634\u062F."
  });
}
__name(handleLogout, "handleLogout");
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => {
    const entities = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    };
    return entities[char];
  });
}
__name(escapeHtml, "escapeHtml");
async function handleConfigRequest(request, env) {
  const user = await authenticatedUser(request, env);
  if (!user) {
    return json({
      success: false,
      message: "\u0628\u0631\u0627\u06CC \u062F\u0631\u062E\u0648\u0627\u0633\u062A \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0627\u0628\u062A\u062F\u0627 \u0648\u0627\u0631\u062F \u062D\u0633\u0627\u0628 \u062E\u0648\u062F \u0634\u0648\u06CC\u062F."
    }, 401);
  }
  let body;
  try {
    body = await request.json();
  } catch {
    return json({
      success: false,
      message: "\u0627\u0637\u0644\u0627\u0639\u0627\u062A \u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A."
    }, 400);
  }
  const serverId = String(body.server_id ?? body.server ?? "").trim();
  const modeId = String(body.mode ?? "").trim();
  const volume = String(body.volume ?? "").trim();
  const expiry = String(body.expiry ?? "").trim();
  const note = String(body.note ?? body.description ?? "").trim();
  const deviceCount = Number(normalizeDigits(body.device_count ?? 1));
  const server = SERVERS.find((item) => item.id === serverId);
  const mode = MODES.find((item) => item.id === modeId);
  if (!server) {
    return json({
      success: false,
      message: "\u0633\u0631\u0648\u0631 \u0627\u0646\u062A\u062E\u0627\u0628\u200C\u0634\u062F\u0647 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A."
    }, 400);
  }
  if (!mode) {
    return json({
      success: false,
      message: "\u062D\u0627\u0644\u062A \u0627\u0646\u062A\u062E\u0627\u0628\u200C\u0634\u062F\u0647 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A."
    }, 400);
  }
  if (volume.length < 1 || volume.length > 50 || expiry.length < 1 || expiry.length > 50) {
    return json({
      success: false,
      message: "\u062D\u062C\u0645 \u06CC\u0627 \u0645\u062F\u062A \u0627\u0639\u062A\u0628\u0627\u0631 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A."
    }, 400);
  }
  if (!Number.isInteger(deviceCount) || deviceCount < 1 || deviceCount > 10) {
    return json({
      success: false,
      message: "\u062A\u0639\u062F\u0627\u062F \u062F\u0633\u062A\u06AF\u0627\u0647 \u0628\u0627\u06CC\u062F \u0628\u06CC\u0646 \u06F1 \u062A\u0627 \u06F1\u06F0 \u0628\u0627\u0634\u062F."
    }, 400);
  }
  if (note.length > 1e3) {
    return json({
      success: false,
      message: "\u062A\u0648\u0636\u06CC\u062D\u0627\u062A \u0646\u0628\u0627\u06CC\u062F \u0628\u06CC\u0634\u062A\u0631 \u0627\u0632 \u06F1\u06F0\u06F0\u06F0 \u06A9\u0627\u0631\u0627\u06A9\u062A\u0631 \u0628\u0627\u0634\u062F."
    }, 400);
  }
  let requestId;
  try {
    const result = await env.DB.prepare(`
      INSERT INTO config_requests
      (user_id, volume, expiry, server_id, mode, note, device_count, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, 'pending_payment')
    `).bind(
      user.id,
      volume,
      expiry,
      server.id,
      mode.id,
      note || null,
      deviceCount
    ).run();
    requestId = result.meta.last_row_id;
  } catch {
    return json({
      success: false,
      message: "\u0630\u062E\u06CC\u0631\u0647 \u062F\u0631\u062E\u0648\u0627\u0633\u062A \u062F\u0631 \u062F\u06CC\u062A\u0627\u0628\u06CC\u0633 \u0646\u0627\u0645\u0648\u0641\u0642 \u0628\u0648\u062F."
    }, 500);
  }
  const fullName = `${user.first_name} ${user.last_name}`;
  const requestedAt = (/* @__PURE__ */ new Date()).toISOString();
  const html = `
    <div style="font-family:Arial,sans-serif;direction:rtl;text-align:right;line-height:2">
      <h2>\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u06A9\u0627\u0646\u0641\u06CC\u06AF \u062C\u062F\u06CC\u062F NEXORA VPN</h2>
      <hr>
      <p><b>\u0634\u0645\u0627\u0631\u0647 \u062F\u0631\u062E\u0648\u0627\u0633\u062A:</b> ${escapeHtml(requestId)}</p>
      <p><b>\u0646\u0627\u0645 \u06A9\u0627\u0631\u0628\u0631:</b> ${escapeHtml(fullName)}</p>
      <p><b>\u0634\u0645\u0627\u0631\u0647 \u062A\u0644\u0641\u0646:</b> ${escapeHtml(user.phone)}</p>
      <p><b>\u0634\u0646\u0627\u0633\u0647 \u06A9\u0627\u0631\u0628\u0631:</b> ${escapeHtml(user.id)}</p>
      <p><b>\u062D\u062C\u0645 \u06A9\u0627\u0646\u0641\u06CC\u06AF:</b> ${escapeHtml(volume)}</p>
      <p><b>\u0645\u062F\u062A \u0627\u0639\u062A\u0628\u0627\u0631:</b> ${escapeHtml(expiry)}</p>
      <p><b>\u062A\u0639\u062F\u0627\u062F \u062F\u0633\u062A\u06AF\u0627\u0647 \u0645\u062C\u0627\u0632:</b> ${escapeHtml(deviceCount)} \u062F\u0633\u062A\u06AF\u0627\u0647</p>
      <p><b>\u0633\u0631\u0648\u0631:</b> ${escapeHtml(server.name)} ${escapeHtml(server.flag)}</p>
      <p><b>\u0634\u0646\u0627\u0633\u0647 \u0633\u0631\u0648\u0631:</b> ${escapeHtml(server.id)}</p>
      <p><b>\u062D\u0627\u0644\u062A:</b> ${escapeHtml(mode.name)}</p>
      <p><b>\u062A\u0648\u0636\u06CC\u062D\u0627\u062A:</b><br>${escapeHtml(note || "\u0628\u062F\u0648\u0646 \u062A\u0648\u0636\u06CC\u062D\u0627\u062A")}</p>
      <hr>
      <p><b>\u0648\u0636\u0639\u06CC\u062A:</b> \u062F\u0631 \u0627\u0646\u062A\u0638\u0627\u0631 \u067E\u0631\u062F\u0627\u062E\u062A</p>
      <p><b>\u0632\u0645\u0627\u0646 \u062F\u0631\u062E\u0648\u0627\u0633\u062A (UTC):</b> ${escapeHtml(requestedAt)}</p>
    </div>
  `;
  if (!env.RESEND_API_KEY) {
    return json({
      success: true,
      message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0630\u062E\u06CC\u0631\u0647 \u0634\u062F\u060C \u0627\u0645\u0627 \u0633\u0631\u0648\u06CC\u0633 \u0627\u06CC\u0645\u06CC\u0644 \u062A\u0646\u0638\u06CC\u0645 \u0646\u0634\u062F\u0647 \u0627\u0633\u062A.",
      request_id: requestId,
      device_count: deviceCount,
      status: "pending_payment",
      email_sent: false
    });
  }
  try {
    const response = await fetch(RESEND_URL, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${env.RESEND_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        from: EMAIL_FROM,
        to: [EMAIL_TO],
        subject: `\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u06A9\u0627\u0646\u0641\u06CC\u06AF \u0634\u0645\u0627\u0631\u0647 ${requestId} - ${fullName}`,
        html
      })
    });
    if (!response.ok) {
      return json({
        success: true,
        message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0630\u062E\u06CC\u0631\u0647 \u0634\u062F\u060C \u0627\u0645\u0627 \u0627\u0631\u0633\u0627\u0644 \u0627\u06CC\u0645\u06CC\u0644 \u0646\u0627\u0645\u0648\u0641\u0642 \u0628\u0648\u062F.",
        request_id: requestId,
        device_count: deviceCount,
        status: "pending_payment",
        email_sent: false
      });
    }
  } catch {
    return json({
      success: true,
      message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0630\u062E\u06CC\u0631\u0647 \u0634\u062F\u060C \u0627\u0645\u0627 \u0627\u0631\u062A\u0628\u0627\u0637 \u0628\u0627 \u0633\u0631\u0648\u06CC\u0633 \u0627\u06CC\u0645\u06CC\u0644 \u0628\u0631\u0642\u0631\u0627\u0631 \u0646\u0634\u062F.",
      request_id: requestId,
      device_count: deviceCount,
      status: "pending_payment",
      email_sent: false
    });
  }
  return json({
    success: true,
    message: "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u0634\u0645\u0627 \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u062B\u0628\u062A \u0634\u062F \u0648 \u0628\u0631\u0627\u06CC \u0628\u0631\u0631\u0633\u06CC \u0645\u062F\u06CC\u0631 \u0627\u0631\u0633\u0627\u0644 \u06AF\u0631\u062F\u06CC\u062F.",
    request_id: requestId,
    device_count: deviceCount,
    status: "pending_payment",
    email_sent: true
  });
}
__name(handleConfigRequest, "handleConfigRequest");
async function handleRequest(request, env) {
  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, Authorization",
        "Cache-Control": "no-store"
      }
    });
  }
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();
  if (method === "GET" && (path === "/" || path === "/health")) {
    return json({
      success: true,
      api: "online",
      vpn: "not_configured",
      version: VERSION
    });
  }
  if (method === "GET" && (path === "/api/servers" || path === "/servers")) {
    return json({
      success: true,
      servers: SERVERS
    });
  }
  if (method === "GET" && (path === "/api/modes" || path === "/modes")) {
    return json({
      success: true,
      modes: MODES
    });
  }
  if (method === "POST" && (path === "/api/register" || path === "/register")) {
    return handleRegister(request, env);
  }
  if (method === "POST" && (path === "/api/login" || path === "/login")) {
    return handleLogin(request, env);
  }
  if (method === "GET" && (path === "/api/me" || path === "/me")) {
    return handleMe(request, env);
  }
  if (method === "POST" && (path === "/api/logout" || path === "/logout")) {
    return handleLogout(request, env);
  }
  if (method === "POST" && (path === "/api/config-request" || path === "/config-request")) {
    return handleConfigRequest(request, env);
  }
  return json({
    success: false,
    message: "\u0645\u0633\u06CC\u0631 \u0645\u0648\u0631\u062F\u0646\u0638\u0631 \u067E\u06CC\u062F\u0627 \u0646\u0634\u062F."
  }, 404);
}
__name(handleRequest, "handleRequest");
var worker_default = {
  async fetch(request, env) {
    try {
      if (!env.DB) {
        return json({
          success: false,
          message: "\u0627\u062A\u0635\u0627\u0644 \u062F\u06CC\u062A\u0627\u0628\u06CC\u0633 DB \u062A\u0646\u0638\u06CC\u0645 \u0646\u0634\u062F\u0647 \u0627\u0633\u062A."
        }, 500);
      }
      return await handleRequest(request, env);
    } catch (error) {
      let path = "unknown";
      try {
        path = new URL(request.url).pathname;
      } catch {
      }
      console.error("unhandled error", {
        method: request.method,
        path,
        ...safeErrorInfo(error)
      });
      return json({
        success: false,
        message: "\u062E\u0637\u0627\u06CC \u062F\u0627\u062E\u0644\u06CC \u0633\u0631\u0648\u0631 \u0631\u062E \u062F\u0627\u062F\u0647 \u0627\u0633\u062A."
      }, 500);
    }
  }
};
export {
  worker_default as default
};
//# sourceMappingURL=worker.js.map
