# Secret World Auto: itinerary progress and test APKs

November 2026

## Summary

The app can now plan, save and run **sightseeing itineraries**. The assistant
suggests them or the user builds them by hand; they are reviewed on an animated
map, and when the trip starts the route goes to the user's navigation app while
Secret World keeps guiding by voice.

There are **two APKs**, because I have not been able to test the app on Pleos's
real services:

| APK | For | Voice, listening and AI | Size |
|---|---|---|---|
| `app-demo-release.apk` | An Android phone | Its own, bundled in the APK | ~750 MB |
| `app-pleos-release.apk` | The Pleos **Connect‑W** emulator (x86_64) | The Pleos platform's | ~60 MB |

**Download:** shared Google Drive folder: `[LINK TO THE DRIVE FOLDER]`

## Background: why there are two versions

I was confused about the environment at first. I was testing on the
**Pleos Connect v2.1.5** emulator, which runs on my Apple Silicon Mac (arm64).
That image **does not have working versions of the Pleos services** I need:

- Its on-device speech synthesis **only speaks Korean**; in Spanish, Italian and
  English it returns "Unsupported locale".
- Its on-device LLM is a 0.5B model that answers in the wrong language and makes
  up facts.
- The server mode of these services needs credentials (`registerApp`) that I do
  not have.

So I developed the app with **its own voice, speech recognition and LLM**
bundled in the APK, and I have tested it on a phone (Motorola moto g75 5G).

The emulator that does represent the current platform is **Connect Waterfall**
(`POIP_EMULATOR_33_2685.JG.CW.SYS.KOR.033.005.260810.030000-x86_64`, shared under
*Shared with me › Pleos connect emulator › connect waterfall_emulator*). But it
**only runs on x86_64**, and I have nowhere to run it: it does not boot on an
Apple Silicon Mac. It needs a Windows or Linux PC with hardware virtualization.

So that someone with that setup can test it, I prepared the **Pleos version**:
the same app, but speaking, listening and thinking with the platform's services.

## Itinerary progress

### Creating an itinerary

- **Suggested by the assistant, by voice.** "Plan me an itinerary for this
  afternoon", "a three-hour one with history". If something is missing (how much
  time, what the user likes) it asks in a single sentence, and it remembers
  preferences on the device.
- **By hand.** On the home screen, each place's **+** adds it to an itinerary. On
  the itinerary map, nearby places can be tapped to add them. By voice: "add the
  Cerro de la Popa".
- **Adding to an existing one.** If none is open, the app asks which one the place
  goes to, or whether to start a new one.
- **A realistic plan.** Each stop has a visit time based on the kind of place (a
  castle is not a square), the arrival time at each one, and lunch when it fits.
  The route starts at the first stop, not at the car.

### Presenting and changing it

- **Cinematic presentation.** When an itinerary is proposed, the camera flies over
  the stops on a simplified night map, like in a video game. The route is drawn
  leg by leg and the voice names every stop with its visit time.
- **Voice changes on the same map.** "Start with the castle", "leave the cathedral
  for the end", "more time at the museum", "remove the square". After a change,
  the app says only what changed and the new total.
- **Save or start, however it is said.** "Save it", "I like it", "start the
  itinerary", "let's go", "later"… There is no need to say "yes". Anything the
  grammar does not understand is passed to the LLM.

### My itineraries

- Several itineraries saved on the device, shown like a **game's level select**:
  the list on the left and the map in the background, which flies to the selected
  itinerary.
- From there an itinerary can be started, edited or deleted, or a new one created,
  suggested or by hand.
- Changes to a saved itinerary are saved immediately.

### On the road

- When the trip starts, the route is handed to the **user's navigation app**
  (Google Maps or Waze) and Secret World shrinks to a **floating window** that only
  takes voice commands.
- The app **does not speak when the route starts**: the navigation app's voice
  takes priority. It will still describe a place if asked while driving.
- "End the trip" brings it back to full screen.
- Asking about a place does not leave the itinerary: the camera zooms in on it and
  returns when the description ends.

### Around the itinerary

- **Home screen on the map**, with the places to see within an action radius set
  in Settings (1 to 10 km). If there is very little inside, the radius widens on
  its own.
- **Searches work like a radar** (restaurants, fuel, parking…): they start close by,
  depending on what is being looked for, and widen until they find enough. They
  also work for another area ("restaurants in Bocagrande"); the map then centres
  there.
- **Place images**, from Wikipedia/Wikidata for sights and satellite view for
  businesses. The source for business photos is still pending a decision (see
  [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md), item 14).
- **First-launch loading screen**, like a video game's, while the voice and models
  are unpacked (Android APK only).

## Android APK (`app-demo-release.apk`)

The full version, with voice, speech recognition and LLM bundled. This is the
one I have tested.

### Requirements

Those of the device I tested it on, a **Motorola moto g75 5G**:

| | Requirement |
|---|---|
| System | Android 16 (as on the moto g75). It starts on Android 8.1 or later, but I have only tested it on Android 16 |
| Processor | 64-bit ARM (**arm64‑v8a**). It does not run on 32-bit or x86 processors |
| Memory | **8 GB of RAM**, as on the moto g75. The LLM (Gemma 3 1B) needs several GB free while loading |
| Storage | **3 GB free**: the APK takes ~750 MB, and on first launch it unpacks voices and models into ~1.5 GB more |
| Connection | Internet, for the map, the place catalogue and the images |
| Sensors | **GPS** and **microphone**, with permissions granted |
| Navigation app | Google Maps or Waze installed, to receive the itinerary route |

### Installation

1. Download `app-demo-release.apk` from the Drive folder.
2. Allow installing apps from unknown sources for the browser or file manager
   being used.
3. Open the APK and install it. It is signed with a test key; no store is needed.
4. On first launch, the loading screen takes a few minutes while it unpacks voices
   and models. This happens only once.
5. Grant location and microphone access.

## Pleos emulator APK (`app-pleos-release.apk`)

The same app, with no voice or AI of its own:

| Piece | Where it comes from |
|---|---|
| Listening | The platform's speech recognizer (`ai.umos.ambientai`) |
| Speaking | The Pleos caas speech synthesis |
| LLM | The caas on-device LLM, only for what the grammar does not understand |
| Map | Mapbox (the emulator needs internet) |

Pleos has not published the client libraries for these services yet. I wrote the
clients by hand from the contract I read out of the caas 4.2.9 in the
Connect‑W image: they may break with another version.

**Not yet tested on Connect‑W.** On the v2.1.5 emulator I confirmed that the
APK installs, receives the Pleos permissions and finds the services, but the
emulator was so slow that I could not see it speak or listen.

How to install it, what to check and which logs to send:
[PLEOS-TEST.md](PLEOS-TEST.md).

## Pending

- **Test the Pleos APK on Connect‑W**: confirm whether its voice speaks Spanish or
  Italian and whether the speech recognizer responds.
- **Pleos's official libraries** (NaviHelper, `sdk.tts`, `sdk.stt` and the LLM
  client) and the `registerApp` credentials, so I can replace the hand-written clients.
- **The source for business photos** (Google Places or Mapillary), pending
  approval.
