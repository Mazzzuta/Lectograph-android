import { DatabaseSync } from 'node:sqlite';

export class Quota {
  constructor(path, limits) {
    this.db = new DatabaseSync(path);
    this.limits = limits;
    this.db.exec('CREATE TABLE IF NOT EXISTS quota (key TEXT, bucket TEXT, requests INTEGER NOT NULL, tokens INTEGER NOT NULL, PRIMARY KEY(key,bucket))');
  }
  reserve(installation, action, tokens, now = new Date()) {
    const day = now.toISOString().slice(0, 10), minute = now.toISOString().slice(0, 16);
    const entries = [
      [`install:${installation}:${action}`, day, { study: 3, test: 3, question: 10 }[action], Infinity, 0],
      ['global-day', day, this.limits.dailyRequests, this.limits.dailyTokens, tokens],
      ['global-minute', minute, this.limits.minuteRequests, this.limits.minuteTokens, tokens],
    ];
    this.db.exec('BEGIN IMMEDIATE');
    try {
      for (const [key, bucket, requests, budget, amount] of entries) {
        const used = this.db.prepare('SELECT requests,tokens FROM quota WHERE key=? AND bucket=?').get(key, bucket);
        if ((used?.requests ?? 0) + 1 > requests || (used?.tokens ?? 0) + amount > budget) return false;
      }
      for (const [key, bucket, , , amount] of entries)
        this.db.prepare('INSERT INTO quota VALUES(?,?,1,?) ON CONFLICT(key,bucket) DO UPDATE SET requests=requests+1,tokens=tokens+excluded.tokens').run(key, bucket, amount);
      this.db.prepare('DELETE FROM quota WHERE bucket<?').run(new Date(now.getTime() - 2 * 86400000).toISOString().slice(0, 10));
      this.db.exec('COMMIT'); return true;
    } finally { if (this.db.isTransaction) this.db.exec('ROLLBACK'); }
  }
  close() { this.db.close(); }
}
