import { cases, outOfScope } from './cases.mjs';

const routes = ['gpt', 'gemini', 'oss-120b', 'oss-20b'];
const requested = process.argv.slice(2);
if (!requested.length || requested.some(route => !routes.includes(route)) ||
    !process.env.ORACLE_BENCHMARK_URL || !process.env.BENCHMARK_TOKEN) {
  console.error('Supply ORACLE_BENCHMARK_URL, BENCHMARK_TOKEN and one or more route names.');
  process.exit(2);
}
const endpoint = new URL('/benchmark', process.env.ORACLE_BENCHMARK_URL);
if (endpoint.protocol !== 'https:' && !(endpoint.protocol === 'http:' && endpoint.hostname === 'localhost')) {
  throw new Error('Use HTTPS or localhost.');
}

async function measure(route, request) {
  try {
    const result = await fetch(endpoint, {
      method: 'POST',
      redirect: 'error',
      headers: {Authorization: `Bearer ${process.env.BENCHMARK_TOKEN}`, 'Content-Type': 'application/json'},
      signal: AbortSignal.timeout(35_000),
      body: JSON.stringify({route, ...request}),
    });
    return {...await result.json(), httpStatus: result.status};
  } catch {
    return {status: 'transport_error', upstreamCalled: null};
  }
}

function report(row) {
  process.stdout.write(JSON.stringify(row) + '\n');
}

for (const route of new Set(requested)) {
  for (const [index, request] of outOfScope.entries()) {
    const data = await measure(route, request);
    report({route, caseId: `off-topic-${index + 1}`, ...data,
      correct: data.status === 'out_of_scope' && data.upstreamCalled === false});
    if (data.status === 'unauthorized' || data.status === 'transport_error') break;
  }
  for (const test of cases) {
    const result = await measure(route, {caseId: test.id});
    const letter = /^([AB])\b/i.exec(result.answer ?? '')?.[1]?.toUpperCase();
    report({route, caseId: test.id, expected: test.expected, ...result, correct: letter === test.expected});
    if (['not_configured', 'unauthorized', 'transport_error'].includes(result.status) || result.providerStatus === 429) break;
  }
}
