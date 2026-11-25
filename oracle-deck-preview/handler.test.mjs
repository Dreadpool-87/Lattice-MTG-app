import test from "node:test";
import assert from "node:assert/strict";
import handler from "./handler.mjs";

const code = "private-test-code-only";
const check = { model: "120b", commander: true, cards: 100, lands: 38, copyWarning: true, identity: "flagged" };

const request = (body = check, auth = code) => new Request("https://preview.invalid/explain", {
  method: "POST",
  headers: { Authorization: `Bearer ${auth}` },
  body: typeof body === "string" ? body : JSON.stringify(body),
});

function fixtures(limit = 1) {
  const seen = { attempts: 0, infer: [], usage: [] };
  const env = {
    PREVIEW_CODE: code,
    BUDGET: { getByName: () => ({
      reserve(day) {
        assert.match(day, /^\d{4}-\d{2}-\d{2}$/);
        return seen.attempts >= limit ? null : limit - ++seen.attempts;
      },
      record(_day, neurons) { seen.usage.push(neurons); },
    }) },
    AI: { run(model, payload) {
      seen.infer.push([model, payload]);
      return { choices: [{ message: { content: "Example explanation." }, finish_reason: "stop" }],
        usage: { neurons: 8.5 } };
    } },
  };
  return { env, seen };
}

test("wrong code and arbitrary or oversized input never reserve a request", async () => {
  const { env, seen } = fixtures();
  assert.equal((await handler.fetch(request(check, "wrong-preview-code"), env)).status, 401);
  assert.equal((await handler.fetch(request({ ...check, prompt: "Unrelated" }), env)).status, 422);
  assert.equal((await handler.fetch(request({ ...check, model: "constructor" }), env)).status, 422);
  assert.equal((await handler.fetch(request(" ".repeat(513)), env)).status, 422);
  assert.equal(seen.attempts, 0);
  assert.equal(seen.infer.length, 0);
});

test("models share a cap and the response reports metered usage", async () => {
  const { env, seen } = fixtures(2);
  const first = await handler.fetch(request(), env);
  assert.deepEqual(await first.json(), {
    status: "ok", text: "Example explanation.", neurons: 8.5, remaining: 1,
  });
  const second = await handler.fetch(request({ ...check, model: "20b" }), env);
  assert.equal((await second.json()).remaining, 0);
  const limited = await handler.fetch(request(), env);
  assert.equal(limited.status, 429);
  assert.deepEqual(seen.infer.map(([model]) => model), ["@cf/openai/gpt-oss-120b", "@cf/openai/gpt-oss-20b"]);
  assert.deepEqual(seen.usage, [8.5, 8.5]);
  for (const [, payload] of seen.infer) {
    assert.equal(payload.max_tokens, 512);
    assert.match(payload.messages[1].content, /Playable cards: 100. Lands: 38/);
    assert.match(payload.messages[1].content, /Full format legality was not checked/);
  }
});

test("truncated outputs still count and retain usage without displaying incomplete text", async () => {
  const { env, seen } = fixtures();
  env.AI.run = () => ({
    choices: [{ message: { content: "Truncated" }, finish_reason: "length" }],
    usage: { neurons: 17 },
  });
  assert.equal((await handler.fetch(request(), env)).status, 502);
  assert.deepEqual(seen.usage, [17]);
  assert.equal((await handler.fetch(request(), env)).status, 429);
});

const ask = (body, token = code) => new Request("https://preview.invalid/ask", {
  method: "POST",
  headers: { Authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});
const cardQuestion = {
  model: "20b",
  scope: "card",
  question: "Can this creature attack on the turn it enters?",
  context: { name: "Example creature", text: "Haste" },
};
const deckQuestion = {
  model: "20b",
  scope: "deck",
  question: "What does the curve say about my opening plays?",
  context: { format: "Casual", analytics: "60 cards. 23 lands.", cards: ["mainboard: 4 × Example creature ({W})"] },
};

test("ask requires a bounded question about exactly one deck or card context", async () => {
  const { env, seen } = fixtures();
  assert.equal((await handler.fetch(ask(cardQuestion, "wrong-preview-code"), env)).status, 401);
  for (const input of [
    { ...cardQuestion, scope: "general" },
    { ...cardQuestion, context: null },
    { ...cardQuestion, question: " ".repeat(400) },
    { ...cardQuestion, question: "a".repeat(401) },
    { ...cardQuestion, model: ["20b"] },
    { ...cardQuestion, context: { ...cardQuestion.context, cards: [] } },
    { ...deckQuestion, context: { ...deckQuestion.context, cards: [] } },
    { ...deckQuestion, context: { ...deckQuestion.context, cards: ["á".repeat(160)].flatMap(x => Array(180).fill(x)) } },
  ]) {
    assert.equal((await handler.fetch(ask(input), env)).status, 422);
  }
  assert.equal(seen.attempts, 0);
});

test("ask meters card and deck answers and shares the explanation allowance", async () => {
  const { env, seen } = fixtures(2);
  env.AI.run = (model, payload) => {
    seen.infer.push([model, payload]);
    return { response: "MTG_ANSWER: Here is the explanation.", usage: { neurons: 9 } };
  };
  for (const [index, input] of [cardQuestion, deckQuestion].entries()) {
    const response = await handler.fetch(ask(input), env);
    assert.deepEqual(await response.json(), {
      status: "ok", text: "Here is the explanation.", neurons: 9, remaining: 1 - index,
    });
    assert.match(seen.infer[index][1].messages[0].content, /untrusted data/);
    assert.deepEqual(JSON.parse(seen.infer[index][1].messages[1].content), input);
  }
  assert.equal((await handler.fetch(request(), env)).status, 429);
});

test("scope refusals and unmarked answers do not return arbitrary generated text", async () => {
  for (const output of ["OUT_OF_SCOPE", "Unmarked text", "MTG_ANSWER: "]) {
    const { env } = fixtures();
    env.AI.run = () => ({ response: output, usage: { neurons: 4 } });
    const response = await handler.fetch(ask({ ...cardQuestion, question: "Give me a pasta recipe" }), env);
    const json = await response.json();
    assert.match(json.text, /Magic: The Gathering question about the selected deck or card/);
    assert.equal(json.neurons, 4);
  }
});
