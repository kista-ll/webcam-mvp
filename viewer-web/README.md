# WebRTC 受信ビューア (Vite + TypeScript)

LAN 内で使うことを想定した、シンプルな WebRTC 受信ビューアです。

## 要件

- Node.js 18+
- シグナリング用 WebSocket サーバー（例: `ws://localhost:3001`）

## セットアップ

```bash
npm install
```

## 起動

```bash
npm run dev
```

ブラウザで `http://localhost:5173` を開きます。

## ビルド

```bash
npm run build
```

## ビルド成果物の確認

```bash
npm run preview
```

## 使い方

1. `roomId` を入力（デフォルト: `abc`）
2. `signaling URL` を入力（デフォルト: `ws://localhost:3001`）
3. `Start` を押すと、受信側として以下を実行
   - WebSocket 接続
   - `RTCPeerConnection` 作成
   - `createOffer({ offerToReceiveVideo: true })`
   - `setLocalDescription` 後に offer を送信
4. answer を受け取ったら `setRemoteDescription`
5. ICE candidate を相互送受信（JSON）
6. `ontrack` で受信映像を `<video autoplay playsinline controls>` に表示
7. `Stop` で接続を終了

## シグナリングメッセージ例

```json
{
  "type": "offer",
  "roomId": "abc",
  "sdp": { "type": "offer", "sdp": "..." }
}
```

```json
{
  "type": "answer",
  "roomId": "abc",
  "sdp": { "type": "answer", "sdp": "..." }
}
```

```json
{
  "type": "candidate",
  "roomId": "abc",
  "candidate": {
    "candidate": "candidate:...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

> LAN 想定のため TURN は未設定です（必要なら `RTCPeerConnection` の `iceServers` を追加してください）。
