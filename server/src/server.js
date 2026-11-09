import http from 'node:http';
import { mkdirSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { gigaChatProvider } from './gigachat.js';
import { Quota } from './quota.js';
import { ApiError, generate, validateInput } from './gemini.js';

export function createServer({ quota, key, model, provider = generate }) {
  let busy = false;
  const send = (res, status, data) => {
    if (res.destroyed) return;
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...(status === 429 ? { 'Retry-After': '120' } : {}) });
    res.end(JSON.stringify(data));
  };
  const server = http.createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/healthz') return send(res, 200, { ready: Boolean(key && model) });
    const action = /^\/api\/(study|test|question)$/.exec(req.url ?? '')?.[1];
    if (req.method !== 'POST' || !action) return send(res, 404, { error: 'Не найдено' });
    let ownsSlot = false;
    const controller = new AbortController();
    res.on('close', () => { if (!res.writableEnded) controller.abort(); });
    try {
      if (!key || !model) throw new ApiError(503, 'Сервер обработки ещё не настроен.');
      if (busy) throw new ApiError(429, 'Сервер обрабатывает другую лекцию. Повторите позже.');
      const installation = req.headers['x-installation-id'];
      if (typeof installation !== 'string' || !/^[a-f0-9-]{36}$/i.test(installation)) throw new ApiError(400, 'Некорректный идентификатор установки.');
      if (!req.headers['content-type']?.startsWith('application/json')) throw new ApiError(415, 'Ожидается JSON.');
      const chunks = []; let bytes = 0;
      for await (const chunk of req) {
        bytes += chunk.length;
        if (bytes > 512000) throw new ApiError(413, 'Слишком большой запрос.');
        chunks.push(chunk);
      }
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { throw new ApiError(400, 'Некорректный JSON.'); }
      const input = validateInput(body);
      if (action === 'question' && !input.question) throw new ApiError(400, 'Введите вопрос.');
      if (busy) throw new ApiError(429, 'Сервер занят. Повторите позже.');
      // UTF-8 bytes conservatively bound input tokens; failed requests retain their reserved budget.
      const reservedTokens = Buffer.byteLength(JSON.stringify(input), 'utf8') + 12000;
      if (!quota.reserve(installation, action, reservedTokens)) throw new ApiError(429, 'Достигнут лимит обработки. Повторите позже.');
      busy = true; ownsSlot = true;
      const result = await provider(action, input, { key, model, signal: controller.signal });
      send(res, 200, result);
    } catch (error) {
      send(res, error instanceof ApiError ? error.status : 502, { error: error instanceof ApiError ? error.message : 'Обработка прервана. Попробуйте позже.' });
    } finally { if (ownsSlot) busy = false; }
  });
  server.requestTimeout = 120000; server.headersTimeout = 15000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const integer = (name, fallback) => {
    const value = Number(process.env[name] ?? fallback);
    if (!Number.isSafeInteger(value) || value < 1) throw new Error(`Invalid ${name}`);
    return value;
  };
  mkdirSync('data', { recursive: true });
  const quota = new Quota('data/quota.sqlite', {
    dailyRequests: integer('DAILY_REQUESTS', 50), dailyTokens: integer('DAILY_TOKEN_BUDGET', 500000),
    minuteRequests: integer('MINUTE_REQUESTS', 3), minuteTokens: integer('MINUTE_TOKEN_BUDGET', 250000),
  });
  const gigaKey = process.env.GIGACHAT_AUTH_KEY?.trim();
  const server = createServer(gigaKey ? {
    quota, key: gigaKey, model: process.env.GIGACHAT_MODEL || 'GigaChat-2-Pro',
    provider: gigaChatProvider({ scope: process.env.GIGACHAT_SCOPE }),
  } : { quota, key: process.env.GEMINI_API_KEY, model: process.env.GEMINI_MODEL });
  server.listen(integer('PORT', 8787), process.env.HOST ?? '127.0.0.1', () => console.log('Lectograph API started'));
  process.on('SIGTERM', () => server.close(() => { quota.close(); process.exit(0); }));
}
