import { cases } from './cases.mjs';

const byId = new Map(cases.map(test => [test.id, test]));
const models = {
  gpt: {provider: 'openai', envKey: 'OPENAI_API_KEY'},
  gemini: {provider: 'google', envKey: 'GEMINI_API_KEY', defaultModel: 'gemini-3.8-flash'},
  'oss-120b': {provider: 'cloudflare', defaultModel: '@cf/openai/gpt-oss-120b'},
  'oss-20b': {provider: 'cloudflare', defaultModel: '@cf/openai/gpt-oss-20b'},
};
const instruction = 'Answer only the Magic: The Gathering rules question given. State A or B first, then explain in one short sentence. Do not invent a rule number.';
const maxOutputTokens = 768;

const reply = (body, status = 200) => Response.json(body, {status, headers: {'Cache-Control': 'no-store'}});
const count = value => Number.isSafeInteger(value) && value >= 0 ? value : null;

async function post(url, auth, body) {
  const result = await fetch(url, {
    method: 'POST',
    redirect: 'error',
    headers: {'Content-Type': 'application/json', ...auth},
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(30_000),
  });
  if (!result.ok) throw Object.assign(new Error('Provider request failed'), {status: result.status});
  return result.json();
}

async function ask(route, model, prompt, env) {
  switch (route.provider) {
    case 'openai': {
      const data = await post('https://api.openai.com/v1/responses',
        {Authorization: `Bearer ${env.OPENAI_API_KEY}`},
        {model, instructions: instruction, input: prompt, max_output_tokens: maxOutputTokens, store: false});
      const text = data.output?.flatMap(item => item.content ?? [])
        .filter(item => item.type === 'output_text').map(item => item.text).join('\n');
      const usage = data.usage;
      return {text, input: usage?.input_tokens, output: usage?.output_tokens,
        reasoning: usage?.output_tokens_details?.reasoning_tokens, cached: usage?.input_tokens_details?.cached_tokens};
    }
    case 'google': {
      const data = await post(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
        {'x-goog-api-key': env.GEMINI_API_KEY}, {
          systemInstruction: {parts: [{text: instruction}]},
          contents: [{role: 'user', parts: [{text: prompt}]}],
          generationConfig: {maxOutputTokens},
        });
      const usage = data.usageMetadata;
      const text = data.candidates?.[0]?.content?.parts?.filter(part => !part.thought)
        .map(part => part.text ?? '').join('\n');
      const output = count(usage?.candidatesTokenCount);
      const reasoning = count(usage?.thoughtsTokenCount);
      return {text, input: usage?.promptTokenCount,
        output: output === null ? null : output + (reasoning ?? 0), reasoning, cached: usage?.cachedContentTokenCount};
    }
    case 'cloudflare': {
      const data = await env.AI.run(model, {
        messages: [{role: 'system', content: instruction}, {role: 'user', content: prompt}],
        max_tokens: maxOutputTokens,
      });
      const usage = data.usage;
      const text = data.response ?? data.choices?.[0]?.message?.content;
      return {text, input: usage?.prompt_tokens ?? usage?.input_tokens,
        output: usage?.completion_tokens ?? usage?.output_tokens,
        reasoning: usage?.completion_tokens_details?.reasoning_tokens ?? usage?.output_tokens_details?.reasoning_tokens,
        cached: usage?.prompt_tokens_details?.cached_tokens ?? usage?.input_tokens_details?.cached_tokens};
    }
  }
}

export default {
  async fetch(request, env) {
    if (!env.BENCHMARK_TOKEN || request.headers.get('Authorization') !== `Bearer ${env.BENCHMARK_TOKEN}`) {
      return reply({status: 'unauthorized', upstreamCalled: false}, 401);
    }
    if (request.method !== 'POST' || new URL(request.url).pathname !== '/benchmark') {
      return reply({status: 'not_found', upstreamCalled: false}, 404);
    }

    let body;
    try {
      const raw = await request.text();
      if (raw.length > 512) return reply({status: 'out_of_scope', upstreamCalled: false}, 422);
      body = JSON.parse(raw);
    } catch {
      return reply({status: 'invalid_request', upstreamCalled: false}, 400);
    }

    const test = byId.get(body?.caseId);
    if (!test || Object.keys(body).some(key => key !== 'caseId' && key !== 'route')) {
      return reply({status: 'out_of_scope', upstreamCalled: false}, 422);
    }
    const route = Object.hasOwn(models, body.route) ? models[body.route] : null;
    if (!route) return reply({status: 'unknown_route', upstreamCalled: false}, 400);
    const model = route.provider === 'openai' ? env.OPENAI_MODEL : route.defaultModel;
    if (!model || (route.envKey ? !env[route.envKey] : !env.AI)) {
      return reply({status: 'not_configured', upstreamCalled: false}, 503);
    }

    const prompt = `${test.question}\nA. ${test.options[0]}\nB. ${test.options[1]}`;
    const start = performance.now();
    try {
      const data = await ask(route, model, prompt, env);
      const answer = typeof data.text === 'string' ? data.text.trim() : '';
      return reply({
        status: answer ? 'ok' : 'no_answer', upstreamCalled: true, model, elapsedMs: Math.round(performance.now() - start),
        usage: {input: count(data.input), output: count(data.output), reasoning: count(data.reasoning), cached: count(data.cached)},
        answer,
      });
    } catch (error) {
      return reply({status: 'upstream_error', upstreamCalled: true, model,
        elapsedMs: Math.round(performance.now() - start), providerStatus: count(error.status)}, 502);
    }
  },
};
