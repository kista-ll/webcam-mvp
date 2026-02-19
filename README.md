# webcam-mvp (Android / Kotlin)

WebRTC で **Android 端末のカメラ映像を送信**する最小構成アプリです。  
受信側（viewer-web）が offer を作成する前提で、Android 側は `offer` 受信後に `answer` を返します。

## 実装済み要件

- UI
  - Signaling URL 入力（`room` が無ければ room 入力値を自動付与）
  - room 入力（デフォルト `abc`）
  - Connect / Disconnect
  - ローカルプレビュー `SurfaceViewRenderer`
  - 状態表示 `iceConnectionState`
- WebRTC
  - `PeerConnectionFactory` 初期化（`EglBase`）
  - `Camera2Enumerator` で `VideoCapturer` 作成（前面優先）
  - `VideoSource -> VideoTrack` 作成
  - ローカルプレビュー表示
  - `PeerConnection` に `videoTrack` を `addTrack`
  - `offer` 受信 -> `setRemoteDescription`
  - `createAnswer` -> `setLocalDescription` -> `answer` 送信
  - ICE candidate 送受信
- Signaling
  - OkHttp WebSocket
  - JSON: `{type:"offer"|"answer"|"ice", payload:any}`
- 権限
  - `INTERNET`, `CAMERA`（`RECORD_AUDIO` 不要）

## 使い方

1. Android Studio でこのフォルダを開く
2. Gradle Sync
3. 実機で起動（カメラ利用のため実機推奨）
4. Signaling URL と Room を入力して Connect

### 入力例

- URL に room を含む場合:  
  `ws://192.168.0.5:3001?room=abc`
- URL に room を含まない場合:  
  `ws://192.168.0.5:3001` + room `abc`  
  → 自動で `ws://192.168.0.5:3001?room=abc` にして接続

## Signaling メッセージ形式

### offer / answer

```json
{
  "type": "offer",
  "payload": {
    "sdp": "...",
    "type": "offer"
  }
}
```

```json
{
  "type": "answer",
  "payload": {
    "sdp": "...",
    "type": "answer"
  }
}
```

### ice

```json
{
  "type": "ice",
  "payload": {
    "candidate": "candidate:...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

## よくある落とし穴

1. **localhost 問題**
   - Android 実機から見た `localhost` は「実機自身」です。
   - PC 上で signaling サーバーを動かしているなら、`ws://<PCのLAN IP>:3001` を使ってください。

2. **Windows の IP 指定**
   - `ipconfig` で IPv4 アドレスを確認し、Wi-Fi の同一ネットワーク上で接続します。
   - 例: `ws://192.168.0.5:3001?room=abc`

3. **ファイアウォール (FW)**
   - Windows Defender Firewall などで `3001` ポート受信がブロックされると接続できません。
   - signaling サーバーのポートを許可してください。

4. **サーバー bind アドレス**
   - サーバーが `127.0.0.1` bind だと他端末から接続不可です。
   - `0.0.0.0` bind で起動してください。

5. **Android のカメラ権限拒否**
   - 初回権限ダイアログで拒否すると映像取得できません。
   - 設定アプリから CAMERA を許可してください。

## 主な構成ファイル

- `app/src/main/java/com/example/webrtccamera/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
