import {createInterface} from 'node:readline';

const reports = new Map();
// Cloudflare publishes model-specific Neurons per million input/output tokens.
const neuronRates = {'oss-120b': [31818, 68182], 'oss-20b': [18182, 27273]};
for await (const line of createInterface({input: process.stdin})) {
  if (!line.trim()) continue;
  const row = JSON.parse(line);
  if (!reports.has(row.route)) reports.set(row.route, []);
  reports.get(row.route).push(row);
}

for (const [route, rows] of reports) {
  const mtg = rows.filter(row => row.expected);
  const scope = rows.filter(row => !row.expected);
  const upstream = rows.filter(row => row.upstreamCalled === true);
  const known = upstream.filter(row => Number.isInteger(row.usage?.input) && Number.isInteger(row.usage?.output));
  const total = field => known.reduce((sum, row) => sum + row.usage[field], 0);
  const [input, output] = [total('input'), total('output')];
  const times = upstream.map(row => row.elapsedMs).filter(Number.isFinite).sort((a, b) => a - b);
  const rates = neuronRates[route];
  const neurons = rates && known.length > 0 ? Number(((input * rates[0] + output * rates[1]) / 1e6).toFixed(4)) : null;
  console.log(JSON.stringify({
    route, mtgCorrect: mtg.filter(row => row.correct).length, mtgAttempted: mtg.length,
    offTopicRejectedBeforeModel: scope.filter(row => row.correct).length, offTopicAttempted: scope.length,
    upstreamAttempts: upstream.length, requestsWithTokenUsage: known.length,
    inputTokensMeasured: known.length ? input : null, outputTokensMeasured: known.length ? output : null,
    estimatedNeurons: neurons,
    estimatedDailyNeuronsAt300Requests: neurons !== null && known.length === upstream.length
      ? Math.round(300 * neurons / known.length) : null,
    medianProviderMs: times.length ? (times[Math.floor((times.length - 1) / 2)] + times[Math.floor(times.length / 2)]) / 2 : null,
  }));
}
