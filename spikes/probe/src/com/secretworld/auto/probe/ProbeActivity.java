package com.secretworld.auto.probe;

import android.app.Activity;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Sonda de alcance para Pleos Connect. No es parte del producto: existe para
 * responder, contra el emulador real, que hay disponible en la plataforma.
 * Todo el resultado va tambien a logcat con el tag SWPROBE.
 */
public class ProbeActivity extends Activity {

    private static final String TAG = "SWPROBE";
    private final StringBuilder out = new StringBuilder();
    private TextView view;
    private TextToSpeech tts;
    private AudioTrack track;
    private SpeechRecognizer recognizer;
    private boolean recognizerProbed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(13f);
        view.setTextColor(Color.WHITE);
        view.setPadding(40, 40, 40, 40);
        scroll.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        try {
            requestPermissions(new String[]{
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.RECORD_AUDIO",
                    "android.car.permission.CAR_INFO",
                    "android.car.permission.CAR_SPEED",
                    "android.car.permission.CAR_POWERTRAIN",
                    "android.car.permission.CAR_ENERGY",
                    "android.car.permission.CAR_EXTERIOR_ENVIRONMENT",
                    "android.car.permission.CAR_MILEAGE",
            }, 1);
        } catch (Throwable t) {
            line("requestPermissions fallo: " + t);
        }
        runProbe();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        runProbe();
    }

    private void runProbe() {
        out.setLength(0);
        section("1. SISTEMA");
        probeSystem();
        section("2. FEATURES");
        probeFeatures();
        section("3. PANTALLAS");
        probeDisplays();
        section("4. PERMISOS CONCEDIDOS");
        probeGrantedPermissions();
        section("5. VEHICLE SDK (spike 3)");
        probeCar();
        section("6. VOZ - RECONOCIMIENTO (spike 1)");
        probeSpeech();
        probeRecognizerLive();
        section("7. VOZ - SINTESIS TTS (spike 2)");
        probeTts();
        probeTtsEngine();
        section("8. NAVEGACION (spike 4)");
        probeNavigation();
        section("9. AUDIO Y MEDIA (spike 5)");
        probeAudio();
        probeAudioTrack();
        section("10. UBICACION");
        probeLocation();
        section("11. PAQUETES DE LA PLATAFORMA");
        probePlatformPackages();
        section("12. SERVICIOS DE PLEOS: PERMISOS Y VINCULACION");
        probePleosServices();
        section("FIN");
        view.setText(out.toString());
    }

    // ---------------------------------------------------------------- sondas

    private void probeSystem() {
        line("modelo        : " + Build.MODEL);
        line("fabricante    : " + Build.MANUFACTURER);
        line("dispositivo   : " + Build.DEVICE);
        line("Android       : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        line("fingerprint   : " + Build.FINGERPRINT);
        line("abis          : " + String.join(", ", Build.SUPPORTED_ABIS));
        line("locale        : " + getResources().getConfiguration().getLocales().get(0));
    }

    private void probeFeatures() {
        PackageManager pm = getPackageManager();
        String[] wanted = {
                "android.hardware.type.automotive",
                "android.software.car.templates_host",
                "android.hardware.microphone",
                "android.hardware.location.gps",
                "android.software.leanback",
        };
        for (String f : wanted) {
            line(pad(f) + (pm.hasSystemFeature(f) ? "SI" : "no"));
        }
    }

    private void probeDisplays() {
        DisplayManager dm = getSystemService(DisplayManager.class);
        Display[] displays = dm.getDisplays();
        line("pantallas: " + displays.length);
        for (Display d : displays) {
            DisplayMetrics m = new DisplayMetrics();
            d.getRealMetrics(m);
            line("  id=" + d.getDisplayId() + " '" + d.getName() + "' "
                    + m.widthPixels + "x" + m.heightPixels
                    + " densidad=" + m.densityDpi + "dpi"
                    + " estado=" + d.getState());
        }
    }

    private void probeGrantedPermissions() {
        String[] perms = {
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.RECORD_AUDIO",
                "android.car.permission.CAR_INFO",
                "android.car.permission.CAR_SPEED",
                "android.car.permission.CAR_POWERTRAIN",
                "android.car.permission.CAR_ENERGY",
                "android.car.permission.CAR_EXTERIOR_ENVIRONMENT",
                "android.car.permission.CAR_MILEAGE",
        };
        for (String p : perms) {
            boolean ok = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
            line(pad(p.replace("android.car.permission.", "car:")
                      .replace("android.permission.", "")) + (ok ? "CONCEDIDO" : "denegado"));
        }
    }

    private void probeCar() {
        Car car = null;
        try {
            car = Car.createCar(this);
        } catch (Throwable t) {
            line("Car.createCar fallo: " + t);
            return;
        }
        if (car == null) {
            line("Car.createCar devolvio null: no hay car service");
            return;
        }
        line("Car conectado : " + car.isConnected());
        CarPropertyManager cpm;
        try {
            cpm = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
        } catch (Throwable t) {
            line("getCarManager(PROPERTY_SERVICE) fallo: " + t);
            return;
        }
        if (cpm == null) {
            line("CarPropertyManager null");
            return;
        }
        try {
            List<CarPropertyConfig> all = cpm.getPropertyList();
            line("propiedades visibles con nuestros permisos: " + all.size());
        } catch (Throwable t) {
            line("getPropertyList fallo: " + t);
        }

        int[] ids = {
                VehiclePropertyIds.INFO_MAKE,
                VehiclePropertyIds.INFO_MODEL,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                VehiclePropertyIds.PERF_ODOMETER,
                VehiclePropertyIds.GEAR_SELECTION,
                VehiclePropertyIds.PARKING_BRAKE_ON,
                VehiclePropertyIds.IGNITION_STATE,
                VehiclePropertyIds.FUEL_LEVEL,
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                VehiclePropertyIds.NIGHT_MODE,
        };
        String[] names = {
                "INFO_MAKE", "INFO_MODEL", "PERF_VEHICLE_SPEED", "PERF_ODOMETER",
                "GEAR_SELECTION", "PARKING_BRAKE_ON", "IGNITION_STATE",
                "FUEL_LEVEL", "EV_BATTERY_LEVEL", "NIGHT_MODE",
        };
        for (int i = 0; i < ids.length; i++) {
            String value;
            try {
                if (!cpm.isPropertyAvailable(ids[i], 0)) {
                    value = "no disponible";
                } else {
                    Object v = cpm.getProperty(ids[i], 0).getValue();
                    value = String.valueOf(v);
                }
            } catch (Throwable t) {
                value = "ERROR " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage());
            }
            line(pad(names[i]) + value);
        }
        try {
            car.disconnect();
        } catch (Throwable ignored) {
        }
    }

    private void probeSpeech() {
        line(pad("isRecognitionAvailable") + SpeechRecognizer.isRecognitionAvailable(this));
        try {
            line(pad("isOnDeviceRecognitionAvailable")
                    + SpeechRecognizer.isOnDeviceRecognitionAvailable(this));
        } catch (Throwable t) {
            line(pad("isOnDeviceRecognitionAvailable") + "no soportado: " + t);
        }
        listServices("android.speech.RecognitionService");
        line("--- quien atiende ACTION_RECOGNIZE_SPEECH ---");
        listActivities(new Intent("android.speech.action.RECOGNIZE_SPEECH"));
        line("--- quien atiende ACTION_VOICE_SEARCH_HANDS_FREE ---");
        listActivities(new Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE"));
    }

    private void probeTts() {
        listServices("android.intent.action.TTS_SERVICE");
        line("--- quien atiende CHECK_TTS_DATA ---");
        listActivities(new Intent("android.speech.tts.engine.CHECK_TTS_DATA"));
    }

    private void probeNavigation() {
        line("--- geo: (traspaso estandar de destino) ---");
        listActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:37.5665,126.9780?q=Gyeongbokgung")));
        line("--- google.navigation: ---");
        listActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=37.5665,126.9780")));
        line("--- CATEGORY_APP_MAPS ---");
        Intent maps = new Intent(Intent.ACTION_MAIN);
        maps.addCategory("android.intent.category.APP_MAPS");
        listActivities(maps);
        line("--- componentes de los servicios de Pleos ---");
        for (String p : new String[]{
                "ai.pleos.playground.service.navi",
                "ai.pleos.playground.service.vehicle",
                "ai.pleos.playground.account",
                "ai.pleos.gleoai.model.provider"}) {
            dumpComponents(p);
        }
    }

    private void probeAudio() {
        AudioManager am = getSystemService(AudioManager.class);
        line(pad("volumen MUSIC") + am.getStreamVolume(AudioManager.STREAM_MUSIC)
                + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        try {
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .build();
            int r = am.requestAudioFocus(req);
            line(pad("requestAudioFocus") + (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    ? "CONCEDIDO" : "resultado=" + r));
            am.abandonAudioFocusRequest(req);
        } catch (Throwable t) {
            line(pad("requestAudioFocus") + "ERROR " + t);
        }
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300);
            line(pad("ToneGenerator") + "pitido lanzado (comprobar si suena)");
        } catch (Throwable t) {
            line(pad("ToneGenerator") + "ERROR " + t);
        }
        line("--- MediaBrowserService en el sistema ---");
        listServices("android.media.browse.MediaBrowserService");
    }

    private void probeLocation() {
        LocationManager lm = getSystemService(LocationManager.class);
        List<String> providers = lm.getAllProviders();
        line("providers: " + providers);
        for (String p : providers) {
            try {
                line(pad(p) + (lm.isProviderEnabled(p) ? "habilitado" : "deshabilitado"));
            } catch (Throwable t) {
                line(pad(p) + "ERROR " + t);
            }
        }
    }

    private void probePlatformPackages() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        int n = 0;
        for (PackageInfo p : pkgs) {
            if (p.packageName.startsWith("ai.pleos") || p.packageName.startsWith("ai.umos")) {
                line("  " + p.packageName + "  v" + p.versionName);
                n++;
            }
        }
        line("total ai.pleos/ai.umos: " + n + " de " + pkgs.size() + " paquetes instalados");
    }

    /** Prueba definitiva de TTS: instanciar el motor, no solo preguntar al PackageManager. */
    private void probeTtsEngine() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    line(pad("TextToSpeech.onInit")
                            + (status == TextToSpeech.SUCCESS ? "SUCCESS" : "ERROR(" + status + ")"));
                    if (status == TextToSpeech.SUCCESS) {
                        try {
                            line(pad("  motor por defecto") + tts.getDefaultEngine());
                            line(pad("  voces") + (tts.getVoices() == null ? "null" : tts.getVoices().size()));
                            Locale[] ls = {new Locale("es", "ES"), Locale.ITALIAN, Locale.ENGLISH, Locale.KOREAN};
                            for (Locale l : ls) {
                                line(pad("  idioma " + l) + tts.isLanguageAvailable(l));
                            }
                        } catch (Throwable t) {
                            line("  fallo consultando el motor: " + t);
                        }
                    }
                    view.setText(out.toString());
                }
            });
            List<TextToSpeech.EngineInfo> engines = tts.getEngines();
            line(pad("getEngines()") + (engines == null ? "null" : engines.size() + " motores"));
            if (engines != null) {
                for (TextToSpeech.EngineInfo e : engines) {
                    line("  " + e.name + "  (" + e.label + ")");
                }
            }
        } catch (Throwable t) {
            line(pad("TextToSpeech") + "ERROR " + t);
        }
    }

    /** Prueba de que el camino de audio funciona de verdad: PCM generado y reproducido. */
    private void probeAudioTrack() {
        try {
            final int rate = 44100;
            final int frames = rate; // 1 segundo
            short[] pcm = new short[frames];
            for (int i = 0; i < frames; i++) {
                pcm[i] = (short) (Math.sin(2 * Math.PI * 440 * i / rate) * 8000);
            }
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(frames * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            int written = track.write(pcm, 0, frames);
            track.play();
            line(pad("AudioTrack escrito") + written + " de " + frames + " muestras");
            line(pad("AudioTrack estado") + track.getPlayState() + " (3 = PLAYING)");
            view.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        int head = track.getPlaybackHeadPosition();
                        line(pad("AudioTrack cabezal 1,5s") + head + " frames"
                                + (head > 0 ? "  => EL AUDIO FLUYE DE VERDAD" : "  => NO SE REPRODUCE NADA"));
                    } catch (Throwable t) {
                        line("cabezal: ERROR " + t);
                    }
                    view.setText(out.toString());
                }
            }, 1500);
        } catch (Throwable t) {
            line(pad("AudioTrack") + "ERROR " + t);
        }
    }

    /** Arranca un reconocimiento real para distinguir un motor vivo de un stub. */
    private void probeRecognizerLive() {
        line("--- reconocimiento en vivo ---");
        if (recognizerProbed) {
            line("  ya probado en esta ejecucion, no se repite");
            return;
        }
        if (checkSelfPermission("android.permission.RECORD_AUDIO")
                != PackageManager.PERMISSION_GRANTED) {
            line("  sin RECORD_AUDIO, no se prueba");
            return;
        }
        recognizerProbed = true;
        try {
            if (recognizer != null) {
                try { recognizer.destroy(); } catch (Throwable ignored) {}
                recognizer = null;
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            if (recognizer == null) {
                line("  createSpeechRecognizer devolvio null => NO HAY MOTOR");
                return;
            }
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) {
                    line("  onReadyForSpeech => EL MOTOR ESTA VIVO Y ESCUCHANDO");
                    view.setText(out.toString());
                }
                @Override public void onError(int e) {
                    line("  onError " + e + " (" + errName(e) + ")");
                    line("  " + (e == SpeechRecognizer.ERROR_CLIENT
                            ? "=> fallo de cliente: probable stub o sin implementacion"
                            : "=> el motor respondio, la tuberia existe"));
                    view.setText(out.toString());
                }
                @Override public void onResults(Bundle b) {
                    line("  onResults => " + b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                    view.setText(out.toString());
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle b) {}
                @Override public void onEvent(int i, Bundle b) {}
            });
            Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
            it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            recognizer.startListening(it);
            line("  startListening lanzado, esperando callback...");
        } catch (Throwable t) {
            line("  ERROR " + t);
        }
    }

    private String errName(int e) {
        switch (e) {
            case 1: return "NETWORK_TIMEOUT";
            case 2: return "NETWORK";
            case 3: return "AUDIO";
            case 4: return "SERVER";
            case 5: return "CLIENT";
            case 6: return "SPEECH_TIMEOUT";
            case 7: return "NO_MATCH";
            case 8: return "RECOGNIZER_BUSY";
            case 9: return "INSUFFICIENT_PERMISSIONS";
            case 10: return "TOO_MANY_REQUESTS";
            case 11: return "SERVER_DISCONNECTED";
            case 12: return "LANGUAGE_NOT_SUPPORTED";
            case 13: return "LANGUAGE_UNAVAILABLE";
            case 14: return "CANNOT_CHECK_SUPPORT";
            default: return "?";
        }
    }

    private void probePleosServices() {
        String[] perms = {
                "pleos.car.permission.STT_SERVICE",
                "pleos.car.permission.TTS_SERVICE",
                "pleos.car.permission.LLM_SERVICE",
                "pleos.car.permission.NAVI_ROUTE",
                "pleos.car.permission.NAVI_ROUTE_SEARCH",
                "pleos.car.permission.FUSED_LOCATION",
                "pleos.car.permission.CAR_INFO",
                "pleos.car.permission.CAR_DRIVE",
                "pleos.car.permission.CAR_ENERGY",
        };
        line("--- permisos pleos.car.permission.* solicitados ---");
        for (String p : perms) {
            boolean ok = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
            line(pad("  " + p.replace("pleos.car.permission.", "")) + (ok ? "CONCEDIDO" : "denegado"));
        }

        line("--- vinculacion a los servicios ---");
        bindProbe("SpeechService (STT/TTS)", "ai.pleos.playground.caas",
                "ai.pleos.playground.caas.intent.action.SPEECH_START");
        bindProbe("LLMService", "ai.pleos.playground.caas",
                "ai.pleos.playground.caas.intent.action.LLM_START");
        bindProbe("NaviService", "ai.pleos.playground.service.navi",
                "ai.pleos.playground.intent.action.NAVI_START");

        // SPEECH_START y LLM_START son las acciones del intent-filter, pero onBind
        // (caas 4.2.9) no las reconoce y devuelve null. Despacha por estas otras,
        // que no estan en el filtro: hay que nombrar el componente.
        line("--- vinculacion por las acciones que despacha onBind ---");
        String caas = "ai.pleos.playground.caas";
        String speech = caas + ".service.speech.SpeechService";
        String llm = caas + ".service.llm.LLMService";
        String act = caas + ".intent.action.";
        bindProbe("STT servidor", caas, speech, act + "SPEECH_SERVER_START");
        bindProbe("STT on-device", caas, speech, act + "SPEECH_ON_DEVICE_START");
        bindProbe("TTS stream servidor", caas, speech, act + "TTS_STREAM_SERVER_START");
        bindProbe("TTS stream on-device", caas, speech, act + "TTS_STREAM_ON_DEVICE_START");
        bindProbe("LLM servidor", caas, llm, act + "LLM_SERVER_START");
        bindProbe("LLM on-device", caas, llm, act + "LLM_ON_DEVICE_START");

        line("--- proveedor de modelos on-device ---");
        // La autoridad cambia entre versiones; el paquete es el mismo.
        boolean authority = getPackageManager()
                .resolveContentProvider("ai.umos.gleo.provider", 0) != null;
        line("  autoridad ai.umos.gleo.provider: " + (authority ? "presente" : "ausente"));
        boolean pkgPresent;
        try {
            getPackageManager().getPackageInfo("ai.pleos.gleoai.model.provider", 0);
            pkgPresent = true;
        } catch (PackageManager.NameNotFoundException e) {
            pkgPresent = false;
        }
        line("  paquete ai.pleos.gleoai.model.provider: "
                + (pkgPresent ? "instalado" : "AUSENTE (sin modelos on-device)"));
    }

    private void bindProbe(final String label, String pkg, String action) {
        bindProbe(label, pkg, null, action);
    }

    private void bindProbe(final String label, String pkg, String cls, String action) {
        Intent it = new Intent(action);
        if (cls != null) it.setClassName(pkg, cls); else it.setPackage(pkg);
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                String desc;
                try {
                    desc = service.getInterfaceDescriptor();
                } catch (Throwable t) {
                    desc = "descriptor ilegible: " + t;
                }
                line("  " + label + " => CONECTADO");
                line("      componente : " + name.flattenToShortString());
                line("      interfaz   : " + desc);
                line("      binder vivo: " + service.pingBinder());
                view.setText(out.toString());
                onLive(label, service);
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                line("  " + label + " => desconectado");
            }
            @Override public void onNullBinding(ComponentName name) {
                line("  " + label + " => onNullBinding: el servicio existe pero NO DEVUELVE BINDER");
                view.setText(out.toString());
            }
            @Override public void onBindingDied(ComponentName name) {
                line("  " + label + " => onBindingDied");
            }
        };
        try {
            boolean r = bindService(it, conn, BIND_AUTO_CREATE);
            line("  " + label + ": bindService devolvio " + r
                    + (r ? "  (esperando callback)" : "  => NO SE PUEDE VINCULAR"));
        } catch (Throwable t) {
            line("  " + label + ": EXCEPCION " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
        }
    }

    // ------------------------------------------- llamadas reales a la caas

    // Contrato leido del bytecode de la caas 2.2.7.1: descriptores, codigos de
    // transaccion y orden de argumentos. Sirve para medir, no para el producto.
    private static final String LLM_ON_DEVICE = "ai.pleos.playground.caas.data.llm.aidl.IOnDeviceLLM";
    private static final String LLM_CALLBACK = "ai.pleos.playground.caas.data.llm.aidl.ILLMCallback";
    private static final String TTS_ON_DEVICE = "ai.pleos.playground.caas.service.speech.aidl.ITtsOnDeviceStream";
    private static final String TTS_CALLBACK = "ai.pleos.playground.caas.service.speech.aidl.ITtsCallback";

    private final java.util.Set<String> called = new java.util.HashSet<>();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    // Los callbacks se guardan para que el GC no se lleve el binder local.
    private final java.util.List<android.os.Binder> callbacks = new java.util.ArrayList<>();

    private void onLive(String label, IBinder service) {
        if (!called.add(label)) return;
        if (label.equals("LLM on-device")) callLlmOnDevice(service);
        if (label.equals("TTS stream on-device")) callTtsOnDevice(service);
    }

    private void callLlmOnDevice(final IBinder llm) {
        final String app = getPackageName();
        line("--- LLM on-device: llamada real ---");
        transact("LLM registerCallback", llm, LLM_ON_DEVICE, 1, callback("LLM", LLM_CALLBACK, 3), app);
        transact("LLM initialize", llm, LLM_ON_DEVICE, 3, app);
        // La carga del modelo es asincrona: se pregunta mas tarde, en dos idiomas.
        // Despues de las pruebas de TTS, para no competir por la CPU con la sintesis.
        main.postDelayed(new Runnable() {
            @Override public void run() {
                transact("LLM generateContentStream it", llm, LLM_ON_DEVICE, 5, app, "req-it",
                        "Descrivi il Colosseo di Roma in due frasi, come una guida turistica.");
            }
        }, 75000);
    }

    private void callTtsOnDevice(final IBinder tts) {
        final String app = getPackageName();
        line("--- TTS on-device: llamada real ---");
        transact("TTS registerCallback", tts, TTS_ON_DEVICE, 1, callback("TTS", TTS_CALLBACK, 2), app);
        final AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        String[][] cases = {
                {"ko-KR", "콜로세움에 오신 것을 환영합니다."},
                {"it-IT", "Benvenuti al Colosseo, il piu grande anfiteatro del mondo romano."},
                {"en-US", "Welcome to the Colosseum, the largest amphitheatre of the Roman world."},
                {"es-ES", "Bienvenidos al Coliseo, el mayor anfiteatro del mundo romano."},
        };
        // El idioma se guarda por app y speak es asincrono: uno cada vez, con margen.
        for (int i = 0; i < cases.length; i++) {
            final String[] c = cases[i];
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    transact("TTS setLocale " + c[0], tts, TTS_ON_DEVICE, 6, c[0], app);
                    transact("TTS speak " + c[0], tts, TTS_ON_DEVICE, 4, c[1], true, app, attrs);
                }
            }, i * 15000L);
        }
    }

    private final java.util.Map<String, StringBuilder> streams = new java.util.HashMap<>();

    /**
     * Binder local que hace de callback AIDL; onResponse es el codigo 1, con
     * {@code strings} argumentos. Los trozos de un stream se juntan y se
     * escriben al cerrarse, para no desbordar logcat.
     */
    private android.os.Binder callback(final String label, final String descriptor, final int strings) {
        android.os.Binder cb = new android.os.Binder() {
            @Override
            protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                    throws android.os.RemoteException {
                if (code == INTERFACE_TRANSACTION) {
                    reply.writeString(descriptor);
                    return true;
                }
                if (code == FIRST_CALL_TRANSACTION) {
                    data.enforceInterface(descriptor);
                    String cmd = data.readString();
                    String id = data.readString();
                    String payload = strings > 2 ? data.readString() : "";
                    if (cmd != null && cmd.contains("Partial")) {
                        synchronized (streams) {
                            StringBuilder sb = streams.get(id);
                            if (sb == null) {
                                streams.put(id, sb = new StringBuilder());
                                line("  " + label + " <= primer trozo de " + id);
                            }
                            sb.append(payload);
                        }
                    } else {
                        StringBuilder sb;
                        synchronized (streams) { sb = streams.remove(id); }
                        if (sb != null) line("  " + label + " <= texto de " + id + ": " + sb);
                        line("  " + label + " <= " + cmd + " | " + id + " | " + payload);
                    }
                    if (reply != null) reply.writeNoException();
                    refresh();
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        cb.attachInterface(null, descriptor);
        callbacks.add(cb);
        return cb;
    }

    private void transact(String label, IBinder target, String descriptor, int code, Object... args) {
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            for (Object a : args) {
                if (a instanceof String) data.writeString((String) a);
                else if (a instanceof Boolean) data.writeInt((Boolean) a ? 1 : 0);
                else if (a instanceof IBinder) data.writeStrongBinder((IBinder) a);
                else if (a instanceof android.os.Parcelable) {
                    // Parcelable propio de la caas: marca de no nulo y su AudioAttributes dentro.
                    data.writeInt(1);
                    data.writeParcelable((android.os.Parcelable) a, 0);
                }
            }
            target.transact(code, data, reply, 0);
            reply.readException();
            line("  " + label + " -> ok");
        } catch (Throwable t) {
            line("  " + label + " -> FALLO " + t);
        } finally {
            data.recycle();
            reply.recycle();
            refresh();
        }
    }

    private void refresh() {
        main.post(new Runnable() {
            @Override public void run() { view.setText(out.toString()); }
        });
    }

    // -------------------------------------------------------------- utiles

    private void listServices(String action) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentServices(new Intent(action), 0);
        if (ris.isEmpty()) {
            line("  (ninguno para " + action + ")");
            return;
        }
        for (ResolveInfo ri : ris) {
            ServiceInfo si = ri.serviceInfo;
            line("  " + si.packageName + "/" + si.name);
        }
    }

    private void listActivities(Intent intent) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);
        if (ris.isEmpty()) {
            line("  (nadie lo atiende)");
            return;
        }
        for (ResolveInfo ri : ris) {
            line("  " + ri.activityInfo.packageName + "/" + ri.activityInfo.name);
        }
    }

    private void dumpComponents(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(pkg,
                    PackageManager.GET_SERVICES | PackageManager.GET_ACTIVITIES
                            | PackageManager.GET_PROVIDERS);
            line("  " + pkg + " v" + pi.versionName);
            if (pi.services != null) {
                for (ServiceInfo s : pi.services) {
                    line("    service  " + s.name + (s.exported ? "  [exported]" : ""));
                }
            }
            if (pi.providers != null) {
                for (android.content.pm.ProviderInfo p : pi.providers) {
                    line("    provider " + p.authority + (p.exported ? "  [exported]" : ""));
                }
            }
        } catch (Throwable t) {
            line("  " + pkg + ": " + t.getClass().getSimpleName());
        }
    }

    private String pad(String s) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < 34) b.append(' ');
        return b + ": ";
    }

    private void section(String title) {
        line("");
        line("=== " + title + " ===");
    }

    private synchronized void line(String s) {
        out.append(s).append('\n');
        Log.i(TAG, s);
    }
}
