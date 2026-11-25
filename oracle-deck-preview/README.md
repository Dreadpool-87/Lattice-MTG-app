# Oracle deck explanation preview

Build the separate debug app with `gradle -PoracleDeckPreview=true :app:assembleDebug`.
Its application ID ends in `.oraclepreview`. The regular Android builds and Firebase
chat use their existing configuration.

With a deck selected in Oracle, choose **Explain deck check · Preview**. Enter a
preview code issued by the Worker administrator. The code stays in memory while
the dialog is open. The app sends only a model choice, counts and local check
statuses. It does not send deck or card names or chat history. Commander color
identity compares cached symbols against the selected commanders and is a
partial check.

The Worker requires an `AI` binding, a SQLite-backed `BUDGET` Durable Object
binding for `PreviewBudget`, and a `PREVIEW_CODE` secret. Its allowance is 24
authorized attempts per UTC day, shared by both model choices. Attempts are
reserved before inference and are not retried. The Worker stores daily request
counts and Cloudflare-reported Neurons, with the metered request count tracked
separately. The app displays the reported units for the response.

Run the handler checks with `node --test oracle-deck-preview/handler.test.mjs`.
