/**
 * Leer la tarjeta QR con la cámara de la tablet (ADR 033).
 *
 * Dos caminos, y el segundo solo se descarga si hace falta:
 *
 * - **`BarcodeDetector`**, el del propio navegador (Chrome y Edge en Android y
 *   en escritorio). No pesa nada.
 * - **jsQR**, cargado bajo demanda en su propio trozo, para Safari e iPad, que
 *   no lo tienen. Así no cuenta en el JS inicial (ADR 029, presupuesto de 150 kB)
 *   y quien no abre el kiosco no se lo descarga nunca.
 *
 * La cámara es la delantera: la tablet está de cara a quien ficha.
 */

/** Lo que hace falta del `BarcodeDetector` del navegador, que TypeScript aún no declara. */
interface DetectorDelNavegador {
  detect(fuente: CanvasImageSource): Promise<{ rawValue: string }[]>;
}

type Detector = (video: HTMLVideoElement) => Promise<string | null>;

async function detectorDelNavegador(): Promise<Detector | null> {
  const Clase = (globalThis as { BarcodeDetector?: new (opciones: { formats: string[] }) => DetectorDelNavegador })
    .BarcodeDetector;
  if (Clase === undefined) return null;
  try {
    const detector = new Clase({ formats: ['qr_code'] });
    return async (video) => (await detector.detect(video))[0]?.rawValue ?? null;
  } catch {
    // Existe pero no sabe de QR (algún Chrome de escritorio sin el servicio).
    return null;
  }
}

async function detectorJsQr(): Promise<Detector> {
  const { default: jsQR } = await import('jsqr');
  const lienzo = document.createElement('canvas');
  const contexto = lienzo.getContext('2d', { willReadFrequently: true });
  return async (video) => {
    if (contexto === null || video.videoWidth === 0) return null;
    // A la mitad basta para un QR de tarjeta, y cada fotograma cuesta un cuarto.
    lienzo.width = Math.floor(video.videoWidth / 2);
    lienzo.height = Math.floor(video.videoHeight / 2);
    contexto.drawImage(video, 0, 0, lienzo.width, lienzo.height);
    const imagen = contexto.getImageData(0, 0, lienzo.width, lienzo.height);
    return jsQR(imagen.data, imagen.width, imagen.height, { inversionAttempts: 'dontInvert' })?.data ?? null;
  };
}

export interface Lector {
  /** Para: la cámara se apaga y no se lee nada más. */
  parar(): void;
}

/**
 * Enciende la cámara en `video` y llama a `alLeer` con cada QR que vea.
 *
 * @throws si no hay cámara o no se da permiso: quien llama lo explica y ofrece
 *   el PIN, que no necesita cámara
 */
export async function empezarALeer(video: HTMLVideoElement, alLeer: (codigo: string) => void): Promise<Lector> {
  const flujo = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
    audio: false,
  });
  video.srcObject = flujo;
  video.muted = true;
  video.playsInline = true;
  await video.play();

  const detectar = (await detectorDelNavegador()) ?? (await detectorJsQr());
  let parado = false;
  let temporizador: ReturnType<typeof setTimeout> | undefined;

  // Cuatro veces por segundo: de sobra para que la tarjeta se lea al acercarla,
  // sin tener la tablet al máximo todo el día.
  const vuelta = async () => {
    if (parado) return;
    try {
      const codigo = await detectar(video);
      if (codigo !== null && !parado) alLeer(codigo);
    } catch {
      // Un fotograma que no se pudo leer no para el lector.
    }
    if (!parado) temporizador = setTimeout(() => void vuelta(), 250);
  };
  void vuelta();

  return {
    parar() {
      parado = true;
      clearTimeout(temporizador);
      for (const pista of flujo.getTracks()) pista.stop();
      video.srcObject = null;
    },
  };
}
