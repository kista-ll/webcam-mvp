import './style.css';

type SignalMessage =
  | { type: 'offer'; roomId: string; sdp: RTCSessionDescriptionInit }
  | { type: 'answer'; roomId: string; sdp: RTCSessionDescriptionInit }
  | { type: 'candidate'; roomId: string; candidate: RTCIceCandidateInit };

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) {
  throw new Error('#app not found');
}

app.innerHTML = `
  <main class="container">
    <h1>WebRTC 受信ビューア</h1>
    <section class="controls">
      <label class="field">
        Room ID
        <input id="roomId" value="abc" />
      </label>
      <label class="field">
        Signaling URL
        <input id="signalingUrl" value="ws://localhost:3001" />
      </label>
      <div class="buttons">
        <button id="start">Start</button>
        <button id="stop" disabled>Stop</button>
      </div>
    </section>

    <section class="status">
      <div>connectionState: <strong id="connectionState">idle</strong></div>
      <div>iceConnectionState: <strong id="iceState">idle</strong></div>
      <div class="log" id="log"></div>
    </section>

    <video id="remoteVideo" autoplay playsinline controls></video>
  </main>
`;

const roomInput = document.querySelector<HTMLInputElement>('#roomId');
const signalingInput = document.querySelector<HTMLInputElement>('#signalingUrl');
const startButton = document.querySelector<HTMLButtonElement>('#start');
const stopButton = document.querySelector<HTMLButtonElement>('#stop');
const remoteVideo = document.querySelector<HTMLVideoElement>('#remoteVideo');
const connectionStateEl = document.querySelector<HTMLElement>('#connectionState');
const iceStateEl = document.querySelector<HTMLElement>('#iceState');
const logEl = document.querySelector<HTMLElement>('#log');

if (
  !roomInput ||
  !signalingInput ||
  !startButton ||
  !stopButton ||
  !remoteVideo ||
  !connectionStateEl ||
  !iceStateEl ||
  !logEl
) {
  throw new Error('Required UI element is missing');
}

let ws: WebSocket | null = null;
let pc: RTCPeerConnection | null = null;

const setLog = (message: string): void => {
  logEl.textContent = message;
};

const updateStates = (): void => {
  connectionStateEl.textContent = pc?.connectionState ?? 'idle';
  iceStateEl.textContent = pc?.iceConnectionState ?? 'idle';
};

const stopSession = (): void => {
  if (ws) {
    ws.onopen = null;
    ws.onmessage = null;
    ws.onerror = null;
    ws.onclose = null;
    ws.close();
    ws = null;
  }

  if (pc) {
    pc.onicecandidate = null;
    pc.ontrack = null;
    pc.onconnectionstatechange = null;
    pc.oniceconnectionstatechange = null;
    pc.close();
    pc = null;
  }

  if (remoteVideo.srcObject) {
    remoteVideo.srcObject = null;
  }

  updateStates();
  setLog('Stopped');
  startButton.disabled = false;
  stopButton.disabled = true;
};

const sendSignal = (message: SignalMessage): void => {
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
};

const handleAnswer = async (message: Extract<SignalMessage, { type: 'answer' }>): Promise<void> => {
  if (!pc) {
    return;
  }
  await pc.setRemoteDescription(new RTCSessionDescription(message.sdp));
  setLog('Answer applied');
  updateStates();
};

const handleCandidate = async (message: Extract<SignalMessage, { type: 'candidate' }>): Promise<void> => {
  if (!pc) {
    return;
  }
  await pc.addIceCandidate(message.candidate);
};

const startSession = (): void => {
  if (pc || ws) {
    stopSession();
  }

  const roomId = roomInput.value.trim() || 'abc';
  const signalingUrl = signalingInput.value.trim() || 'ws://localhost:3001';

  pc = new RTCPeerConnection();
  updateStates();

  pc.onconnectionstatechange = updateStates;
  pc.oniceconnectionstatechange = updateStates;

  pc.ontrack = (event: RTCTrackEvent): void => {
    const [stream] = event.streams;
    if (stream) {
      remoteVideo.srcObject = stream;
      setLog('Remote track received');
    }
  };

  pc.onicecandidate = (event: RTCPeerConnectionIceEvent): void => {
    if (!event.candidate) {
      return;
    }

    sendSignal({
      type: 'candidate',
      roomId,
      candidate: event.candidate.toJSON(),
    });
  };

  ws = new WebSocket(signalingUrl);

  ws.onopen = async (): Promise<void> => {
    if (!pc) {
      return;
    }

    try {
      const offer = await pc.createOffer({
        offerToReceiveVideo: true,
      } as RTCOfferOptions & { offerToReceiveVideo?: boolean });
      await pc.setLocalDescription(offer);

      sendSignal({
        type: 'offer',
        roomId,
        sdp: offer,
      });

      setLog('Offer sent');
      startButton.disabled = true;
      stopButton.disabled = false;
      updateStates();
    } catch (error) {
      console.error(error);
      setLog('Failed to create/send offer');
      stopSession();
    }
  };

  ws.onmessage = async (event: MessageEvent): Promise<void> => {
    try {
      const message = JSON.parse(String(event.data)) as SignalMessage;
      if (message.roomId !== roomId) {
        return;
      }

      if (message.type === 'answer') {
        await handleAnswer(message);
      } else if (message.type === 'candidate') {
        await handleCandidate(message);
      }
    } catch (error) {
      console.error(error);
      setLog('Invalid signaling message');
    }
  };

  ws.onerror = (): void => {
    setLog('WebSocket error');
  };

  ws.onclose = (): void => {
    setLog('WebSocket closed');
    stopSession();
  };
};

startButton.addEventListener('click', startSession);
stopButton.addEventListener('click', stopSession);
