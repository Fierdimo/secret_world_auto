# Testing the Pleos version on the Connect‑W emulator

I need someone to run Secret World Auto on the Pleos **Connect Waterfall**
emulator, `POIP_EMULATOR_33_2685.JG.CW.SYS.KOR.033.005.260810.030000-x86_64`
(Android Automotive 14, API 34, x86_64, caas 4.2.9). It is shared under
*Shared with me › Pleos connect emulator › connect waterfall_emulator*.

I cannot run it myself: the image is x86_64 and does not boot on my Apple
Silicon Mac. This guide covers installing the emulator, installing the app, and
what I need back.

## 1. What you need

| | Requirement |
|---|---|
| Computer | A PC with an **x86_64** processor (Intel or AMD), on **Windows 10/11** or **Linux** |
| Virtualization | Hardware virtualization enabled in the BIOS (**Intel VT‑x** or **AMD‑V**). On Windows, the *Android Emulator hypervisor driver* or *Windows Hypervisor Platform*; on Linux, **KVM** |
| Memory | **16 GB of RAM** or more. The emulator alone takes 4 GB |
| Disk | **25 GB free**: the download is ~11.6 GB and the unpacked image ~11 GB |
| Software | **Android Studio** (or the command-line SDK) with **Android Emulator** and **Android SDK Platform‑Tools** (`adb`) installed |
| Network | Internet in the emulator: the map, the place catalogue and the images need it |
| Microphone | A microphone on the PC, to test listening |

## 2. Installing the emulator

The image is not in Android Studio's catalogue, so it has to be added by hand. It
already includes everything the emulator needs: the kernel (`kernel-ranchu`), the
disks (`system.img`, `vendor.img`, `userdata.img`…) and its own `config.ini`.

### 2.1 Unpack the image

Unpack `POIP_EMULATOR_33_2685…-x86_64.tar`. Inside there is a single folder:

```
RELEASE/RELEASE_CONNECT_W_U_2026-08-10_03_53/
```

### 2.2 Copy it into the SDK as a system image

Copy the **contents** of that folder (not the folder itself) to:

| | Path |
|---|---|
| Windows | `%LOCALAPPDATA%\Android\Sdk\system-images\android-34\pleos_connect_w\x86_64\` |
| Linux | `~/Android/Sdk/system-images/android-34/pleos_connect_w/x86_64/` |

If the SDK is somewhere else, Android Studio shows its location under
*Settings › Languages & Frameworks › Android SDK*.

### 2.3 Create the virtual device

Create the folder `Pleos_Connect_W.avd` inside the AVD folder (`%USERPROFILE%\.android\avd\`
on Windows, `~/.android/avd/` on Linux) and put this `config.ini` inside it:

```ini
AvdId=Pleos_Connect_W
avd.ini.displayname=Pleos Connect-W
image.sysdir.1=system-images/android-34/pleos_connect_w/x86_64/
target=android-34
tag.id=android-automotive
tag.display=Android Automotive
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=6
hw.ramSize=4096
disk.dataPartition.size=6G
hw.lcd.width=1408
hw.lcd.height=792
hw.lcd.density=160
hw.initialOrientation=landscape
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.audioInput=yes
hw.gps=yes
hw.keyboard=yes
hw.mainKeys=no
hw.camera.back=none
hw.camera.front=none
PlayStore.enabled=false
```

Next to that folder, create `Pleos_Connect_W.ini` with the full path to it:

```ini
avd.ini.encoding=UTF-8
path=C:\Users\<you>\.android\avd\Pleos_Connect_W.avd
target=android-34
```

(On Linux: `path=/home/<you>/.android/avd/Pleos_Connect_W.avd`.)

The device then appears in Android Studio's *Device Manager*.

### 2.4 Start it

From Android Studio's *Device Manager*, or from a terminal:

```
emulator -avd Pleos_Connect_W -no-snapshot-load
```

The first boot is slow. It is ready when `adb shell getprop sys.boot_completed`
returns `1`. If the screen stays black, try `-gpu swiftshader_indirect`.

> I wrote these steps from the files in the image; I have not been able to run
> them. If any step fails, the error from the emulator's terminal is what I need.

## 3. Installing the app

Download `app-pleos-release.apk` from the shared Drive folder and run:

```
adb install -r app-pleos-release.apk
adb shell pm grant com.secretworld.auto.pleos android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.secretworld.auto.pleos android.permission.RECORD_AUDIO
adb shell am start -n com.secretworld.auto.pleos/com.secretworld.auto.MainActivity
```

The Pleos permissions (`pleos.car.permission.TTS_SERVICE`, `STT_SERVICE` and
`LLM_SERVICE`) are granted automatically on install.

To set a location: in the emulator's *Extended controls › Location*, or
`adb emu geo fix <longitude> <latitude>` (for example,
`adb emu geo fix -75.515 10.406` for Cartagena).

This version has no voice or AI of its own. It uses the platform's:

| Piece | Where it comes from |
|---|---|
| Listening | Android's `SpeechRecognizer`, which on Pleos is `ai.umos.ambientai/.AmbRecognitionService` |
| Speaking | The caas, `ITtsOnDeviceStream` |
| LLM | The caas, `IOnDeviceLLM`, only for what the app's grammar does not understand |
| Map | OpenStreetMap with MapLibre (needs internet). Mapbox was removed on 17 November 2026 |

If the platform cannot speak the language or listen, or its LLM does not answer
a test question within 10 seconds, the app notices on start-up, says so once
with a crossed-out microphone, and works entirely by touch: the itinerary
questions and the proposal appear as cards over the map with every answer as
a button. That is how it is being tested on the older local emulator (v2.1.5),
which has neither voice nor a usable LLM.

Pleos has not published client libraries for these services yet, so I wrote the
speech and LLM clients by hand from the contract I read out of the caas 4.2.9 in
this image. See [PLEOS-SDK-SURFACE.md](PLEOS-SDK-SURFACE.md).

## 4. What to check

1. **It opens and shows the map** with nearby places.
2. **It speaks.** Tap "listen" on a place, or ask it something. On the older
   v2.1.5 emulator the on-device voice **only spoke Korean**, and Spanish, Italian
   and English returned `UpdateTtsError`. I want to know whether Connect‑W does
   the same.
3. **It listens.** Say "Secret, what's nearby?". The emulator needs the PC's
   microphone (*Extended controls › Microphone*).
4. **The LLM answers** something the grammar does not understand.

## 5. What I need back

The app's log from the moment it opens:

```
adb logcat -s SWPleos SWVoice SWVoiceInput SWNarration
```

The key lines:

- `SWPleos: reconocedores: … · caas: 4.2.9`: which speech recognizer the system resolves, and the caas version.
- `SWPleos: TTS <= UpdateTtsStart / UpdateTtsDone / UpdateTtsError …`: whether the Pleos voice spoke, and in which language it fails.
- `SWPleos: LLM de Pleos vinculado`: whether the LLM connected.
- `SWVoice: «…» → …`: what it heard and how it understood it.

(The log messages are in Spanish; sending them as they are is fine.)

If the app closes, also the error:

```
adb logcat -d | grep -E "FATAL|ANR in|com.secretworld.auto.pleos"
```

(On Windows PowerShell, use `findstr` instead of `grep`.)

A screenshot of the app running helps too.
