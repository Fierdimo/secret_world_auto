# Secret World Auto on Pleos: manual-use demonstration

18 November 2026

## What this is

Two screen recordings of Secret World Auto running **only on Pleos**, in the
Pleos Connect emulator. They show the app **without AI and without voice
commands**. The aim is to show that every other part of the app works by
touch alone: browsing places, reading their pages, changing settings,
building a suggested itinerary and handing the trip to the car's navigation.

The recordings also let anyone see how the app looks and behaves on Pleos
**before installing it in the emulator** themselves.

Voice and the on-device AI are part of the product, but they are left out on
purpose. The Pleos emulator has no working speech or language-model services,
so the app detects this on start-up and switches to touch without blocking
anything. Every question the voice assistant would ask (for example, how much
time you have) appears as a card with all its answers as buttons.

## Test setup

| | |
|---|---|
| Platform | Pleos Connect emulator v2.1.5 (Android Automotive 14), on a Mac |
| App build | Secret World Auto, Pleos variant |
| Voice and AI | Off: not available in the emulator |
| Map | Secret World's own map, built on OpenStreetMap (no Mapbox; see below) |
| Location | Simulated, in the historic centre of Cartagena, Colombia |
| Interface language | English |
| Recording | 30 frames per second, emulator using the Mac's graphics card |

The Pleos home screen gives apps two slots: a **large** landscape one
(1640 × 1400) and a **small** portrait one (800 × 1400). Each recording covers
one of them.

## Video 1: large slot

File: `SecretWorld-Pleos-pantalla-grande.mp4` (1:48)

| Time (approx.) | What is shown |
|---|---|
| 0:00 | Home: the places around the car as a list next to the map, inside the action radius |
| 0:10 | Categories: restaurants, fuel and parking, each one drawn on the map |
| 0:22 | Choosing a place in the list: it is highlighted on the map, with its actions (listen, add to itinerary, take me there) |
| 0:28 | The place's page: photo, type, name and "Take me there" in the header; "See more" for the full text; a map showing where it is |
| 0:42 | Settings: language, action radius and theme, changed by touch (the app switches to the dark theme) |
| 0:54 | My itineraries: saved itineraries, and "+ Suggested" to build a new one |
| 1:00 | The suggested itinerary's single question, answered with a button: 2 h, 3 h, 6 h or all day |
| 1:03 | The presentation of the itinerary: the camera visits each stop in turn, close up, with its name and times |
| 1:25 | The full route with the proposal card: discard, save or start |
| 1:33 | "Start": the trip is handed to the Pleos navigation app, which takes the large slot while Secret World moves to the small one |

## Video 2: small slot

File: `SecretWorld-Pleos-pantalla-pequena.mp4` (1:09)

| Time (approx.) | What is shown |
|---|---|
| 0:00 | The app moves from the large slot to the small one, keeping the itinerary in progress |
| 0:08 | Categories and the list in the narrow layout: map on top, panel below |
| 0:20 | The itinerary in progress ("Now" and "Next" stops) scrolls with the list, so every place stays reachable |
| 0:24 | A place's page in the narrow layout: map folded by default, "Show on map" and "See more" |
| 0:40 | My itineraries in the small slot |
| 0:48 | Settings in the small slot, and the switch back to the light theme |
| 1:00 | The app returns to the large slot |

## Why the map is OpenStreetMap

To avoid licensing problems later, the app no longer uses Mapbox anywhere.
Mapbox's standard terms cover phone and web apps; an app installed in a car
falls under its separate automotive agreement, which we do not have. Building
on open data and open-source software means nothing in the car depends on a
per-vehicle contract.

| Piece | What we use | Why |
|---|---|---|
| Map engine | **MapLibre** | Open-source (BSD licence), the continuation of the engine Mapbox used; runs on Android Automotive |
| Map data and style | **OpenStreetMap**, with OpenFreeMap's free day and night styles | Free to use with attribution; no key and no contract per car |
| Business search | **Nominatim** (by type: restaurants, fuel, parking…) and **Photon** (by name) | OpenStreetMap's own search services |
| Itinerary times and route line | **OSRM** | Open-source routing; the route the driver actually follows is the car's navigation |

Our map only shows **points**: places, the action radius and the stops of an
itinerary. Route guidance is left to the car's navigation app, so we do not
need a commercial navigation map either.

The public OpenStreetMap servers are fine for demonstrations, but they limit
how much they can be used. For the product we will run our own server or use
a paid OpenStreetMap provider, such as MapTiler, without changing the app.

## How the hand-over to the car's navigation works

Secret World does not guide the driver: the car's navigation does. When a trip
starts, Secret World sends the destination to Pleos's navigation service and
brings the navigation app to the front. The Pleos home screen then places the
navigation in the large slot and Secret World in the small one, where it keeps
showing the itinerary. An itinerary is handed over one stop at a time, and
ending it in Secret World also cancels the route in the navigation.

## Limitations of the emulator

- **The navigation app cannot draw its own map in the emulator.** It needs a
  map licence and network services that the emulator does not have, so it
  shows a loading screen and, later, an initialisation error. Its logs show
  that it receives the route Secret World sends. For this reason, in the
  small-slot video the large slot shows the system clock instead.
- **No voice and no AI.** Both are intentionally absent here; this
  demonstration covers manual use only.
- **Changing the theme restarts the app.** When this happens, the settings
  close, the itinerary in progress is lost, and the place name in the header
  briefly shows a wrong neighbourhood before it updates. This is a known
  issue and will be fixed.
