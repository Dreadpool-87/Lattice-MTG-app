# Private Oracle model benchmark

The benchmark adapter compares four routes through one Worker: OpenAI API, Gemini Developer API, and the Cloudflare Workers AI models gpt-oss-120b and gpt-oss-20b. It uses 20 fixed Magic: The Gathering cases and five rejection checks. It accepts preset case IDs only, so the benchmark endpoint cannot accept arbitrary chat prompts. A production app still needs its own MTG input design and access controls.

## Local verification

```sh
node --test oracle-benchmark/worker.test.mjs
```

The tests use mock provider responses. They verify request routing and accounting behavior without running live inference.

## Private test run

The Worker expects an `AI` binding for Workers AI and a `BENCHMARK_TOKEN` secret. Set `OPENAI_API_KEY`, `OPENAI_MODEL` and `GEMINI_API_KEY` only for the routes being tested. Keep the token out of the Android app. Verify the account plan and select an API model before starting a run.

```sh
node oracle-benchmark/run.mjs oss-20b oss-120b gemini gpt > oracle-results.jsonl
node oracle-benchmark/report.mjs < oracle-results.jsonl
```

The runner uses `ORACLE_BENCHMARK_URL` and `BENCHMARK_TOKEN` from the environment. It sends one run per selected route with no automatic retries and stops that route on an upstream rate limit. It reports the answer, expected option, response time, and provider-reported token usage. Review the written explanations as well as the multiple-choice score before selecting a model. For Cloudflare, the summary estimates Neurons using the published model rates. Usage missing from a provider stays unknown, and these estimates do not establish the amount billed. Reconcile the live results with the provider dashboards after inference.

Rules source: https://magic.wizards.com/en/rules
Cloudflare model rates: https://developers.cloudflare.com/workers-ai/platform/pricing/
