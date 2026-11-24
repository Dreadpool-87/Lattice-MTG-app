import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
import {cases, outOfScope} from './cases.mjs';

const token = 'test-only';
const env = {BENCHMARK_TOKEN: token};
const call = (body, config = env, key = token) => worker.fetch(new Request('https://example.invalid/benchmark', {
  method: 'POST', headers: {Authorization: `Bearer ${key}`}, body: JSON.stringify(body),
}), config);

test('all five off-topic inputs are refused before model invocation', async () => {
  let calls = 0;
  for (const attempt of outOfScope) {
    const result = await call({route: 'oss-20b', ...attempt}, {...env, AI: {run: () => { calls++; }}});
    assert.equal(result.status, 422);
    assert.equal((await result.json()).upstreamCalled, false);
  }
  assert.equal(calls, 0);
});

test('benchmark rejects sends without the shared token and rejects unknown aliases', async () => {
  const known = {route: 'oss-20b', caseId: cases[0].id};
  assert.equal((await call(known, env, 'wrong')).status, 401);
  assert.equal((await call({...known, route: 'constructor'})).status, 400);
  assert.equal((await call(known)).status, 503);
});

test('both Workers AI routes record usage using trusted fixtures', async () => {
  for (const suffix of ['20b', '120b']) {
    const config = {...env, AI: {run: async (model, request) => {
      assert.equal(model, `@cf/openai/gpt-oss-${suffix}`);
      assert.match(request.messages[1].content, /retain priority/);
      return {response: 'B. The activated ability resolves first.',
        usage: {prompt_tokens: 60, completion_tokens: 19, completion_tokens_details: {reasoning_tokens: 7}}};
    }}};
    const response = await call({route: `oss-${suffix}`, caseId: cases[0].id}, config);
    const payload = await response.json();
    assert.equal(payload.status, 'ok');
    assert.deepEqual(payload.usage, {input: 60, output: 19, reasoning: 7, cached: null});
  }
});

test('provider-specific usage is normalized without adding reasoning twice', async t => {
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    assert.equal(init.redirect, 'error');
    if (url.includes('openai.com')) {
      return Response.json({output: [{content: [{type: 'output_text', text: 'B. The ability.'}]}],
        usage: {input_tokens: 70, output_tokens: 21, output_tokens_details: {reasoning_tokens: 9}}});
    }
    return Response.json({candidates: [{content: {parts: [{text: 'B. The ability.'}]}}],
      usageMetadata: {promptTokenCount: 71, candidatesTokenCount: 13, thoughtsTokenCount: 9}});
  });

  const providers = {...env, OPENAI_API_KEY: 'fixture', OPENAI_MODEL: 'test-gpt', GEMINI_API_KEY: 'fixture'};
  for (const [route, input, output] of [['gpt', 70, 21], ['gemini', 71, 22]]) {
    const response = await call({route, caseId: cases[0].id}, providers);
    const payload = await response.json();
    assert.equal(payload.status, 'ok');
    assert.deepEqual(payload.usage, {input, output, reasoning: 9, cached: null});
  }
});

test('failed upstream calls are counted without leaking exception text', async () => {
  const response = await call({route: 'oss-20b', caseId: cases[0].id}, {...env,
    AI: {run: async () => { throw new Error('private details'); }}});
  assert.equal(response.status, 502);
  const payload = await response.json();
  assert.equal(payload.upstreamCalled, true);
  assert.ok(!JSON.stringify(payload).includes('private details'));
});
