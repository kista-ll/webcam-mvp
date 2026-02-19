# webcam-mvp

Node.js(TypeScript)で実装した、WebRTC用のシンプルなシグナリングサーバです。

## 要件

- ライブラリ: `ws`
- ポート: `3001`
- room機能: `ws://host:3001?room=abc`
- 同じroom内の他クライアントへブロードキャスト（送信元は除外）
- メッセージ形式: JSON文字列 `{ type: "offer"|"answer"|"ice"|"hello", payload: any }`
- 不正JSONや例外は握りつぶさずログ出力
- 接続/切断/room人数をログ出力

## セットアップ

```bash
npm install
```

## 起動

### 開発モード

```bash
npm run dev
```

### ビルド

```bash
npm run build
```

### 本番起動（ビルド後）

```bash
npm run start
```

## テスト方針

`npm install` ができない環境を想定し、CIを2段に分離しています。

1. **build job**（依存取得が許可された環境）
   - `npm ci`
   - `npm run build`
   - `dist/` と `node_modules/` をアーティファクト化
2. **testing job**（依存取得なし）
   - アーティファクトを展開
   - `node tests/smoke.mjs` を実行

これにより testing 側では依存取得不要で検証できます。

## 手動テスト方法

### 1) サーバ起動

```bash
npm run dev
```

### 2) WebSocketクライアントを2つ接続

例: `wscat` を使う場合

```bash
npx wscat -c ws://localhost:3001?room=abc
npx wscat -c ws://localhost:3001?room=abc
```

### 3) 片方のクライアントから送信

```json
{"type":"hello","payload":{"msg":"hi"}}
```

もう片方のクライアントだけが受信し、送信元には返らないことを確認します。

### 4) 不正JSONの確認

```text
not-json
```

サーバ側にエラーログが出ることを確認します。

### 5) 不正フォーマットJSONの確認

```json
{"type":"unknown","payload":{}}
```

サーバ側にフォーマットエラーログが出ることを確認します。

## CI向けスモークテスト

依存インストール済み成果物を前提に、以下で最小の疎通確認を行います。

```bash
node tests/smoke.mjs
```

検証内容:
- 同一roomへは配信される
- 送信元へは返らない
- 別roomへは配信されない
