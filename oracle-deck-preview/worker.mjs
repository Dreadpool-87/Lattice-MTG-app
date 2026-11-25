import { DurableObject } from "cloudflare:workers";
export { default } from "./handler.mjs";

const dailyRequests = 24;

export class PreviewBudget extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx.storage.sql.exec(`
      CREATE TABLE IF NOT EXISTS days (
        day TEXT PRIMARY KEY,
        attempts INTEGER NOT NULL,
        metered INTEGER NOT NULL DEFAULT 0,
        neurons REAL NOT NULL DEFAULT 0
      )
    `);
  }

  reserve(day) {
    const rows = this.ctx.storage.sql.exec(`
      INSERT INTO days (day, attempts) VALUES (?, 1)
      ON CONFLICT(day) DO UPDATE SET attempts = attempts + 1 WHERE attempts < ?
      RETURNING attempts
    `, day, dailyRequests).toArray();
    return rows.length ? dailyRequests - rows[0].attempts : null;
  }

  record(day, neurons) {
    if (neurons === null) return;
    this.ctx.storage.sql.exec(
      "UPDATE days SET metered = metered + 1, neurons = neurons + ? WHERE day = ?",
      neurons, day,
    );
  }
}
