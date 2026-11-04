// TuLana · servidor de IA (Cloudflare Worker)
// Guarda la clave de la IA para que la app no la lleve dentro y nadie tenga que poner la suya.
//
// Variables del Worker (Settings → Variables and Secrets):
//   GEMINI_API_KEY     (secreta) clave de Google AI Studio  ← opción gratuita
//   ANTHROPIC_API_KEY  (secreta) clave de la API de Claude  ← opción de pago, se usa si no hay de Gemini
//   APP_TOKEN          (opcional) contraseña que la app manda en cada petición
//   GEMINI_MODEL       (opcional) por defecto "gemini-flash-lite-latest"
//   CLAUDE_MODEL       (opcional) por defecto "claude-haiku-5-5"

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "content-type, x-tulana-token",
  "access-control-allow-methods": "GET, POST, OPTIONS",
};
const reply = (status, body) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "content-type": "application/json; charset=utf-8" } });

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
    const provider = env.GEMINI_API_KEY ? "gemini" : env.ANTHROPIC_API_KEY ? "claude" : null;
    if (req.method === "GET") return reply(200, { ok: !!provider, servicio: "TuLana IA", proveedor: provider || "falta la clave" });
    if (req.method !== "POST") return reply(405, { error: "method" });
    if (env.APP_TOKEN && req.headers.get("x-tulana-token") !== env.APP_TOKEN) return reply(401, { error: "bad_token" });
    if (!provider) return reply(503, { error: "no_provider" });

    let b;
    try { b = await req.json(); } catch { return reply(400, { error: "bad_request" }); }
    const system = String(b.system || "").slice(0, 16000);
    const messages = (Array.isArray(b.messages) ? b.messages : []).slice(-16).map(m => ({
      role: m.role === "assistant" ? "assistant" : "user",
      text: String(m.text || "").slice(0, 16000),
      image: typeof m.image === "string" && m.image.length < 7_000_000 ? m.image : null,
    })).filter(m => m.text || m.image);
    if (!messages.length) return reply(400, { error: "bad_request" });
    const maxTokens = Math.min(Math.max(+b.max_tokens || 1024, 64), 4096);
    const json = !!b.json;

    try {
      const text = provider === "gemini"
        ? await gemini(env, system, messages, maxTokens, json)
        : await claude(env, system, messages, maxTokens);
      return reply(200, { text });
    } catch (e) {
      return reply(e.status || 502, { error: e.code || "other" });
    }
  },
};

function upstreamError(status) {
  if (status === 429) return { status: 429, code: "rate_limited" };
  if (status === 401 || status === 403) return { status: 502, code: "server_key" };
  if (status >= 500) return { status: 503, code: "overloaded" };
  return { status: 502, code: "other" };
}

async function gemini(env, system, messages, maxTokens, json) {
  const model = env.GEMINI_MODEL || "gemini-flash-lite-latest";
  const contents = messages.map(m => ({
    role: m.role === "assistant" ? "model" : "user",
    parts: [
      ...(m.image ? [{ inline_data: { mime_type: "image/jpeg", data: m.image } }] : []),
      ...(m.text ? [{ text: m.text }] : []),
    ],
  }));
  const body = {
    contents,
    generationConfig: { maxOutputTokens: maxTokens, temperature: json ? 0.1 : 0.6, ...(json ? { responseMimeType: "application/json" } : {}) },
    ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
  };
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw upstreamError(res.status);
  const data = await res.json();
  return (data.candidates?.[0]?.content?.parts || []).map(p => p.text || "").join("").trim();
}

async function claude(env, system, messages, maxTokens) {
  const msgs = [];
  for (const m of messages) {
    const content = [
      ...(m.image ? [{ type: "image", source: { type: "base64", media_type: "image/jpeg", data: m.image } }] : []),
      ...(m.text ? [{ type: "text", text: m.text }] : []),
    ];
    const last = msgs[msgs.length - 1];
    if (last && last.role === m.role) last.content.push(...content); else msgs.push({ role: m.role, content });
  }
  while (msgs.length && msgs[0].role !== "user") msgs.shift();
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": env.ANTHROPIC_API_KEY, "anthropic-version": "2023-06-01" },
    body: JSON.stringify({ model: env.CLAUDE_MODEL || "claude-haiku-5-5", max_tokens: maxTokens, ...(system ? { system } : {}), messages: msgs }),
  });
  if (!res.ok) throw upstreamError(res.status);
  const data = await res.json();
  return (data.content || []).filter(c => c.type === "text").map(c => c.text).join("").trim();
}
