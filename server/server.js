const http = require("http");

const PORT = Number(process.env.PORT || 8787);
const AGENT_TOKEN = process.env.AGENT_TOKEN || "";
const AI_BASE_URL = (process.env.AI_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, "");
const AI_API_KEY = process.env.AI_API_KEY || "";
const AI_MODEL = process.env.AI_MODEL || "";

const SYSTEM_PROMPT = `
You are Claw Jarvis, a phone assistant for the phone owner's own Android device.
Return strict JSON only:
{
  "reply": "short helpful spoken response",
  "action": "none | reply_notification | type_text | open_app | tap_text | back | home",
  "text": "text to send/type, if any",
  "target": "app package, app label, or visible button text, if any",
  "confidence": 0.0
}

Capabilities:
- Chat naturally with the owner.
- Write, rewrite, summarize, translate, and draft in any language.
- Suggest safe notification replies.
- Open apps, type text, tap visible labels, go back, or go home when the user asks.
- If the owner asks for "dark mode", explain that the phone app treats it as Advanced Owner Mode:
  stronger permission-based automation, not bypassing Android security.

Safety:
- Never approve payments, banking, OTP/2FA sharing, password handling, account deletion,
  security setting changes, lock-screen bypass, hidden surveillance, explicit impersonation,
  or anything illegal.
- For sensitive tasks, action must be "none" and reply should ask the owner to do it manually.
- Keep notification replies short, natural, and not overconfident.
`.trim();

const server = http.createServer(async (req, res) => {
  try {
    if (req.method !== "POST" || req.url !== "/v1/phone-agent") {
      sendJson(res, 404, { error: "not_found" });
      return;
    }

    if (AGENT_TOKEN) {
      const expected = `Bearer ${AGENT_TOKEN}`;
      if (req.headers.authorization !== expected) {
        sendJson(res, 401, { error: "unauthorized" });
        return;
      }
    }

    const body = await readJson(req);
    const response = await askModel(body);
    sendJson(res, 200, response);
  } catch (error) {
    sendJson(res, 500, {
      reply: "I hit a server problem, so I am staying in safe mode.",
      action: "none",
      text: "",
      target: "",
      confidence: 0,
      error: String(error.message || error)
    });
  }
});

server.listen(PORT, () => {
  console.log(`Claw Jarvis agent server listening on http://localhost:${PORT}/v1/phone-agent`);
});

async function askModel(body) {
  const mode = clean(body.mode || "chat");
  const userText = clean(body.userText || "");
  const context = body.context || {};

  if (!AI_API_KEY || !AI_MODEL) {
    return fallback(mode, userText, context);
  }

  const result = await fetch(`${AI_BASE_URL}/chat/completions`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "authorization": `Bearer ${AI_API_KEY}`
    },
    body: JSON.stringify({
      model: AI_MODEL,
      temperature: 0.35,
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        {
          role: "user",
          content: JSON.stringify({
            mode,
            userText,
            context: {
              app: clean(context.app || ""),
              screenText: clean(context.screenText || "").slice(0, 3000),
              notificationTitle: clean(context.notificationTitle || ""),
              notificationText: clean(context.notificationText || "")
            }
          })
        }
      ]
    })
  });

  const json = await result.json();
  if (!result.ok) {
    throw new Error(JSON.stringify(json));
  }
  const content = json.choices?.[0]?.message?.content || "";
  return normalizeModelJson(content);
}

function fallback(mode, userText, context) {
  const lower = userText.toLowerCase();
  if (lower.startsWith("open ")) {
    return response(`Opening ${userText.slice(5).trim()}.`, "open_app", "", userText.slice(5).trim(), 0.75);
  }
  if (lower.startsWith("type ")) {
    return response("Typing that for you.", "type_text", userText.slice(5).trim(), "", 0.75);
  }
  if (lower.startsWith("tap ")) {
    return response(`Tapping ${userText.slice(4).trim()}.`, "tap_text", "", userText.slice(4).trim(), 0.7);
  }
  if (mode === "notification" && context.notificationText) {
    return response("I saw the notification. I will not reply without a model-backed safe response.", "none", "", "", 0.2);
  }
  if (mode === "write" || lower.includes("write") || lower.includes("translate")) {
    return response(
      `Draft request received: ${userText}\n\nConnect AI_API_KEY and AI_MODEL on the server for polished writing in any language.`,
      "none",
      "",
      "",
      0.4
    );
  }
  return response("I am online in fallback mode. Add an AI model key on the server for advanced Jarvis behavior.", "none", "", "", 0.5);
}

function normalizeModelJson(content) {
  const parsed = parseJsonObject(content);
  return response(
    parsed.reply,
    parsed.action,
    parsed.text,
    parsed.target,
    Number(parsed.confidence || 0)
  );
}

function parseJsonObject(content) {
  try {
    return JSON.parse(content);
  } catch (_) {
    const match = String(content).match(/\{[\s\S]*\}/);
    if (match) {
      return JSON.parse(match[0]);
    }
    return { reply: content, action: "none", text: "", target: "", confidence: 0.2 };
  }
}

function response(reply, action, text, target, confidence) {
  const allowed = new Set(["none", "reply_notification", "type_text", "open_app", "tap_text", "back", "home"]);
  return {
    reply: clean(reply || "Done."),
    action: allowed.has(action) ? action : "none",
    text: clean(text || ""),
    target: clean(target || ""),
    confidence: Math.max(0, Math.min(1, Number(confidence || 0)))
  };
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 1_000_000) {
        req.destroy();
        reject(new Error("request_too_large"));
      }
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, status, body) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

function clean(value) {
  return String(value == null ? "" : value).trim();
}
