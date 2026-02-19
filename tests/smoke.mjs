import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const connect = (url) =>
  new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error(`Connection timeout: ${url}`));
    }, 5000);

    ws.onopen = () => {
      clearTimeout(timer);
      resolve(ws);
    };
    ws.onerror = (event) => {
      clearTimeout(timer);
      reject(new Error(`WebSocket error: ${event?.message ?? 'unknown'}`));
    };
  });

const waitForMessage = (ws, timeoutMs = 3000) =>
  new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Message timeout')), timeoutMs);
    ws.onmessage = (event) => {
      clearTimeout(timer);
      resolve(typeof event.data === 'string' ? event.data : String(event.data));
    };
  });

const expectNoMessage = (ws, timeoutMs = 800) =>
  new Promise((resolve, reject) => {
    let gotMessage = false;
    ws.onmessage = () => {
      gotMessage = true;
      reject(new Error('Unexpected message received'));
    };
    setTimeout(() => {
      if (!gotMessage) {
        resolve();
      }
    }, timeoutMs);
  });

const server = spawn('node', ['dist/server.js'], { stdio: ['ignore', 'pipe', 'pipe'] });

server.stdout.on('data', (buf) => process.stdout.write(`[server] ${buf}`));
server.stderr.on('data', (buf) => process.stderr.write(`[server:err] ${buf}`));

try {
  await wait(700);

  const sender = await connect('ws://127.0.0.1:3001?room=abc');
  const receiver = await connect('ws://127.0.0.1:3001?room=abc');
  const otherRoom = await connect('ws://127.0.0.1:3001?room=xyz');

  const payload = { type: 'hello', payload: { msg: 'hi' } };
  sender.send(JSON.stringify(payload));

  const received = await waitForMessage(receiver);
  assert.deepEqual(JSON.parse(received), payload);

  await expectNoMessage(sender);
  await expectNoMessage(otherRoom);

  sender.close();
  receiver.close();
  otherRoom.close();

  console.log('Smoke test passed');
} finally {
  server.kill('SIGTERM');
}
