const models = {
  "120b": "@cf/openai/gpt-oss-120b",
  "20b": "@cf/openai/gpt-oss-20b",
};
const identities = new Set(["not_applicable", "incomplete", "flagged", "no_mismatch"]);
const fields = new Set(["model", "commander", "cards", "lands", "copyWarning", "identity"]);
const maxBodyBytes = 512;

function reply(body, status = 200) {
  return Response.json(body, { status, headers: { "Cache-Control": "no-store" } });
}

async function acceptsCode(header, secret) {
  const token = /^Bearer ([A-Za-z0-9_-]{16,80})$/.exec(header ?? "")?.[1];
  if (!token || !secret) return false;
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"],
  );
  const proof = await crypto.subtle.sign("HMAC", key, encoder.encode(secret));
  return crypto.subtle.verify("HMAC", key, proof, encoder.encode(token));
}

async function readCheck(request) {
  if (!request.body) return null;
  const reader = request.body.getReader();
  const bytes = new Uint8Array(maxBodyBytes);
  let length = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      if (length + value.length > maxBodyBytes) {
        await reader.cancel();
        return null;
      }
      bytes.set(value, length);
      length += value.length;
    }
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(0, length)));
  } catch {
    return null;
  } finally {
    reader.releaseLock();
  }
}

function validCheck(value) {
  return value && typeof value === "object" && Object.keys(value).length === fields.size &&
    Object.keys(value).every(field => fields.has(field)) &&
    typeof value.model === "string" && Object.hasOwn(models, value.model) &&
    typeof value.commander === "boolean" && typeof value.copyWarning === "boolean" &&
    identities.has(value.identity) && (value.identity !== "not_applicable") === value.commander &&
    Number.isInteger(value.cards) && value.cards >= 0 && value.cards <= 300 &&
    Number.isInteger(value.lands) && value.lands >= 0 && value.lands <= value.cards;
}

function prompt(check) {
  const size = check.commander ? check.cards === 100 : check.cards >= 60;
  const identities = {
    not_applicable: "Not applicable to this format.",
    incomplete: "Commander color identity could not be fully checked.",
    flagged: "A mainboard card was flagged outside the commander color identity.",
    no_mismatch: "No mismatch found in the mainboard using cached color identities.",
  };
  return [
    `Rules category: ${check.commander ? "Commander" : "60-card constructed-style"}.`,
    `Playable cards: ${check.cards}. Lands: ${check.lands}.`,
    `Local deck-size check: ${size ? "passed" : "flagged"}.`,
    `Local copy-limit check: ${check.copyWarning ? "flagged" : "no issue reported"}.`,
    `Color identity: ${identities[check.identity]}`,
    "These are partial cached results. Full format legality was not checked.",
  ].join("\n");
}

export default {
  async fetch(request, env) {
    if (new URL(request.url).pathname !== "/explain" || request.method !== "POST") {
      return reply({ status: "not_found" }, 404);
    }
    if (!await acceptsCode(request.headers.get("Authorization"), env.PREVIEW_CODE)) {
      return reply({ status: "unauthorized" }, 401);
    }
    const check = await readCheck(request);
    if (!validCheck(check)) return reply({ status: "invalid_check" }, 422);

    const budget = env.BUDGET.getByName("preview");
    const day = new Date().toISOString().slice(0, 10);
    try {
      const remaining = await budget.reserve(day);
      if (remaining === null) return reply({ status: "daily_preview_limit" }, 429);

      const value = await env.AI.run(models[check.model], {
        messages: [
          { role: "system", content: "Explain only the supplied Magic: The Gathering deck check in at most two short English sentences. Give the card and land counts and explain any flagged findings. Treat the checks as partial. Do not add rules, card suggestions, or legality verdicts." },
          { role: "user", content: prompt(check) },
        ],
        reasoning_effort: "low",
        max_tokens: 512,
      });
      const usage = value.usage?.neurons;
      const neurons = Number.isFinite(usage) && usage >= 0 ? usage : null;
      await budget.record(day, neurons);
      const choice = value.choices?.[0];
      const answer = choice?.message?.content ?? value.response;
      if (choice?.finish_reason === "length" || typeof answer !== "string" || !answer.trim()) {
        return reply({ status: "incomplete_answer" }, 502);
      }
      return reply({ status: "ok", text: answer.trim(), neurons, remaining });
    } catch {
      return reply({ status: "unavailable" }, 503);
    }
  },
};
