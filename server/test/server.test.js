import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Quota } from '../src/quota.js';
import { createServer } from '../src/server.js';
import { generate, validateResult } from '../src/gemini.js';

const limits = { dailyRequests: 50, dailyTokens: 500000, minuteRequests: 50, minuteTokens: 500000 };
test('installation and global quotas are atomic and survive rejected reservations', () => {
  const q = new Quota(':memory:', limits);
  const now = new Date('2026-11-08T00:00:00Z');
  for (let i = 0; i < 3; i++) assert.equal(q.reserve('one', 'study', 100, now), true);
  assert.equal(q.reserve('one', 'study', 100, now), false);
  assert.equal(q.reserve('two', 'study', 600000, now), false);
  assert.equal(q.reserve('two', 'study', 100, now), true);
  q.close();
});
test('tests with invented source quotes are rejected', () => {
  const questions = [{ question: 'Что?', options: ['А', 'Б', 'В', 'Г'], correctIndex: 0, explanation: 'Ответ', sourceQuote: 'Выдуманная цитата' }];
  assert.throws(() => validateResult('test', { questions }, 'Настоящая лекция'), /проверить/);
});
test('daily lecture limit stops requests before calling Gemini', async t => {
  const quota = new Quota(':memory:', limits); let calls = 0;
  const server = createServer({ quota, key: 'test-key', model: 'test', provider: async () => { calls++; return { detailed: 'Конспект' }; } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.close(); quota.close(); });
  const url = `http://127.0.0.1:${server.address().port}/api/study`;
  for (let i = 0; i < 4; i++) {
    const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'x-installation-id': 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' }, body: JSON.stringify({ text: 'Достаточно длинный текст настоящей лекции для теста.' }) });
    assert.equal(response.status, i === 3 ? 429 : 200); await response.text();
  }
  assert.equal(calls, 3);
});
test('provider receives a bounded structured request with the key only in a header', async () => {
  let request;
  const result = await generate('study', { text: 'Содержимое лекции' }, {
    key: 'secret', model: 'test-model', signal: new AbortController().signal,
    fetchImpl: async (url, options) => {
      request = { url, options };
      return new Response(JSON.stringify({ candidates: [{ finishReason: 'STOP', content: { parts: [{ text: JSON.stringify({ short: 'Краткое содержание', detailed: 'Подробное содержание лекции', clarifications: [], studentQuestions: [] }) }] } }] }));
    },
  });
  assert.equal(request.options.headers['x-goog-api-key'], 'secret');
  assert.equal(request.url.includes('secret'), false);
  assert.equal(JSON.parse(request.options.body).generationConfig.maxOutputTokens, 8192);
  assert.equal(result.short, 'Краткое содержание');
});
