import { shouldUploadPosition } from "./pickup-state";
import { validPickupCoordinates } from "./pickup-mobile";

type GeolocationReader = Pick<Geolocation, "watchPosition" | "clearWatch">;
export function watchPickupPosition(options: {
  geolocation: GeolocationReader;
  send: (position: GeolocationPosition, signal: AbortSignal) => Promise<void>;
  onMessage: (message: string) => void;
  onStopped: () => void;
  onSendError: (error: unknown) => void;
  now?: () => number;
}) {
  let active = true;
  let watchId: number | null = null;
  let lastUpload: number | null = null;
  let sending = false;
  let controller: AbortController | null = null;
  const stop = () => {
    active = false;
    if (watchId !== null) options.geolocation.clearWatch(watchId);
    watchId = null;
    controller?.abort();
    controller = null;
  };
  const failed = (failure: GeolocationPositionError) => {
    if (!active) return;
    options.onMessage(failure.code === 1 ? "위치 권한이 꺼져 있어요. 차량 지도와 상태 알림은 계속 사용할 수 있어요." : "내 위치를 받지 못했어요. 다시 공유하거나 상태 버튼을 사용해 주세요.");
    stop();
    options.onStopped();
  };
  try {
    const id = options.geolocation.watchPosition(position => {
      if (!active || sending) return;
      const now = (options.now ?? Date.now)();
      if (!validPickupCoordinates({ latitude: position.coords.latitude, longitude: position.coords.longitude })
          || !Number.isFinite(position.coords.accuracy) || position.coords.accuracy < 0
          || !Number.isFinite(position.timestamp) || position.timestamp <= 0) {
        options.onMessage("정확한 내 위치를 기다리고 있어요.");
        return;
      }
      if (!shouldUploadPosition(lastUpload, now)) return;
      sending = true;
      lastUpload = now;
      const request = new AbortController();
      controller = request;
      void options.send(position, request.signal)
        .then(() => { if (active) options.onMessage("내 위치를 공유하고 있어요."); })
        .catch(error => { if (active && !request.signal.aborted) options.onSendError(error); })
        .finally(() => { if (controller === request) controller = null; sending = false; });
    }, failed, { enableHighAccuracy: true, timeout: 15_000, maximumAge: 5_000 });
    if (active) watchId = id;
    else options.geolocation.clearWatch(id);
  } catch {
    options.onMessage("이 브라우저에서는 내 위치를 사용할 수 없어요.");
    stop();
    options.onStopped();
  }
  return stop;
}