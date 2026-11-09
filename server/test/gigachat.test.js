import test from 'node:test';
import assert from 'node:assert/strict';
import { gigaChatProvider } from '../src/gigachat.js';

test('GigaChat exchanges the key for a cached token and validates lecture quotes', async () => {
  const text = 'Сила измеряется в ньютонах. Это единица измерения силы.';
  let authCalls = 0;
  let completionCalls = 0;
  const provider = gigaChatProvider({ fetchImpl: async (url, request) => {
    if (url.endsWith('/oauth')) {
      authCalls++;
      assert.equal(request.headers.Authorization, 'Basic test-key');
      assert.equal(request.body.get('scope'), 'GIGACHAT_API_PERS');
      assert.match(request.headers.RqUID, /^[a-f0-9-]{36}$/);
      return Response.json({ access_token: 'test-token', expires_at: Math.floor(Date.now() / 1000) + 1800 });
    }
    completionCalls++;
    assert.equal(url, 'https://api.giga.chat/v1/chat/completions');
    assert.equal(request.headers.Authorization, 'Bearer test-token');
    assert.ok(!request.body.includes('test-key'));
    const sourceQuote = completionCalls === 1 ? 'Сила измеряется в ньютонах.' : 'Несуществующая цитата';
    return Response.json({ choices: [{ finish_reason: 'stop', message: {
      content: JSON.stringify({ answer: 'В ньютонах.', sourceQuote }),
    } }] });
  } });
  const config = { key: 'test-key', model: 'GigaChat-2-Pro', signal: new AbortController().signal };
  const input = { text, question: 'В чём измеряется сила?' };
  assert.equal((await provider('question', input, config)).answer, 'В ньютонах.');
  await assert.rejects(provider('question', input, config), /источник ответа/);
  assert.equal(authCalls, 1);
});
