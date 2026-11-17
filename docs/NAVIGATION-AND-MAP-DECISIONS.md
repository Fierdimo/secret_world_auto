# Secret World Auto: how the app will work with the car's navigation

November 2026

## Summary

While researching Pleos, I confirmed something that changes how the app should
look in the car: Pleos is built on **Android Automotive OS (AAOS)**, and on these
systems the map and the route guidance belong to the **car's own navigation
app**. Secret World should not compete with it. It should sit next to it.

These are my decisions:

1. **In the car, Secret World is a panel, not a full-screen map app.** It is
   roughly the left-hand panel of the current version: places with photos,
   searches, itineraries and voice.
2. **Routes are drawn and guided by the car's navigation app.** When the driver
   starts a trip, Secret World hands over the destination and the stops.
3. **Secret World still shows its points on a map** while exploring and while
   building an itinerary, because no navigation app will draw them for us. That
   map will be **our own, without Mapbox**, built on open-source software and
   OpenStreetMap data.
4. **Mapbox stays only in the phone version**, which becomes the **proof of
   concept**. Its licence covers phones, not cars.
5. **Everything built so far still counts.** Above all, the voice commands do not
   depend on how the screen looks, and they carry over as they are.

The rest of this document explains why.

## How Pleos and Android Automotive work

### The app runs in the car

There are two very different ways of putting an app on a car screen:

| | Pleos / Android Automotive | Android Auto / CarPlay |
|---|---|---|
| Where the app runs | **In the car's computer** | On the phone |
| The car screen is | The device itself | An external display |
| Without a phone | Works | No app |
| Vehicle data (speed, position) | Available | Not available |

Pleos is the first kind. Secret World is installed in the car, from the Pleos
App Market, and works without a phone.

### What the screen allows

- **A wide, landscape screen.** The Pleos emulator images (the current one,
  Connect Waterfall, runs Android Automotive 14) have a short, wide display.
- **Driving restrictions.** While the car moves, Android Automotive only allows
  simple, low-distraction screens: large touch targets, short lists, no typing.
  Audio is the only channel that is always available, which is why the voice
  matters so much.
- **The navigation app owns the map.** On the home screen and in split-screen,
  the car's navigation is the main element, and other apps share the screen
  with it.

### Display modes

| Mode | What it means for Secret World |
|---|---|
| **Full screen** | Only when parked: browsing in detail, reading a place's page |
| **Split screen, next to the navigation app** | The main mode while driving: Secret World on one side, the navigation map on the other |
| **Minimised / background** | Secret World keeps narrating by voice while the navigation app takes the whole screen |

Android Automotive supports split screen. How Pleos exposes it (which app
category we must declare, what sizes we get) is something I still have to
confirm myself by testing it on the current Pleos emulator, Connect Waterfall.
That emulator only runs on a PC with an x86_64 processor (Windows or Linux),
and I do not have one yet: it does not run on my Mac.

## The navigation apps

### Pleos's own navigation

Pleos ships its own navigation app (`ai.umos.maps.android.navigation.app`) and a
navigation service that other apps can talk to through a library called
**NaviHelper**. I checked the service in the Pleos emulator: it is present and
it answers. The library lets an app:

- set a destination and add, remove or reorder stops;
- start route guidance;
- read where the car is, the active route and how much is left;
- receive events such as "next manoeuvre" and "arrived at destination".

What I could not find is a way to **put our own markers on its map**. The
service declares a permission for "map customisation", which suggests it may be
possible, but none of the library functions I could see does it. I still have
to confirm this myself on the Connect Waterfall emulator, once I have an x86_64
PC to run it. The NaviHelper library itself (version 2.0.2 or later) has not been
published by Pleos yet.

One thing I found that contradicts common advice online: Pleos's navigation does
**not** respond to the standard Android "open this location" request (`geo:`),
and the Google Maps link format does not apply. The hand-over has to go through
NaviHelper.

### Google Maps, Waze and others

On cars with Google built in, and on Android Auto, the navigation app is
usually Google Maps or Waze. Neither lets another app draw on its map: we can
only pass them a destination (and, in Waze, a few stops). Android offers one
middle ground for place apps: a ready-made "places with map" screen where the
system itself draws a map with **a handful of our points**, with no routes and a
small limit on how many (I still have to confirm the exact number).

### What this means

**No navigation app can be relied on to show our points.** If we want the
driver to see where places are, Secret World has to draw that map itself. Routes
are different: those the navigation app draws and guides much better than we
could, so we leave them to it.

## The Mapbox licence problem

The current version uses **Mapbox** for its map, because the Secret World phone
app already uses it.

Technically, Mapbox works on Android Automotive. The problem is the licence:

- Mapbox's standard terms cover **mobile and web apps**.
- Using it **inside a vehicle** (an app installed in the car's infotainment
  system and distributed with the car) falls under Mapbox's **automotive
  offering**, which is a separate commercial agreement and an expensive one.
- The contract that covers the phone app **does not cover the car**.

So Mapbox cannot be the map in the car without a new automotive agreement. It is
fine for the phone demo, which is what it stays as.

### The alternative: our own map, without Mapbox

Since our map only needs to show **points**, not routes, it can be built with
open-source pieces and no per-vehicle licence:

| Piece | Choice | Why |
|---|---|---|
| Map engine | **MapLibre** | The open-source continuation of the engine Mapbox used, BSD licence. Works on Android Automotive and is almost a drop-in replacement, so the change is contained |
| Map data | **OpenStreetMap**, hosted by us or by a provider such as MapTiler | Free to use with attribution; no contract per car |
| Style | Our own day and night styles, matching the app's light and dark themes | As the app does today |
| Offline fallback | The simple map the app already draws itself | Points still show with no network |

Before committing to it, I want to check that MapLibre runs well on Pleos
hardware and how much map data the catalogue's regions need.

## How the app and the navigation will work together

### Who draws what

| Moment | Who draws | What the driver sees |
|---|---|---|
| **Exploring** ("Sights", searches) | Secret World, on its own map | Places as points, the action radius, the selected place highlighted |
| **Building an itinerary** | Secret World, on its own map | The stops as **numbered points in order**, plus nearby places to add. No route line |
| **Starting the trip** | The car's navigation | Secret World hands over the destination and stops; the route, guidance and turn-by-turn voice are the navigation's |
| **On the road** | The car's navigation, with Secret World alongside or in the background | Secret World narrates the places, stays quiet before a tricky junction and tells the stop's story on arrival |

### How each action maps to the navigation

Every action that involves the navigation goes through a real NaviHelper call,
so nothing shown to a client depends on something the car cannot do:

| What the driver does in Secret World | What happens in the navigation (NaviHelper) |
|---|---|
| "Take me there" | Set the destination and start guidance |
| Start an itinerary | Set the destination and add each stop |
| Remove or reorder a stop | Remove or add stops and recalculate |
| — | "Next manoeuvre" event: Secret World pauses its narration |
| — | "Arrived" event: Secret World tells the stop's story |

### Showing it to clients now

Until we have a car or the NaviHelper library, the best way to show this is a
**split-screen demo on a tablet or phone**: one half plays the part of the
car's navigation, the other half is Secret World working with it. The
navigation half will only do what NaviHelper can do, so the demo does not
promise anything the car cannot deliver. When the library arrives, that half is
replaced by the real connection without changing Secret World's side.

## What the current version proves

The phone version with Mapbox becomes the **proof of concept**. Most of it
carries over to the car unchanged, because it does not depend on the map:

- **Voice commands.** Wake word, understanding what the driver says (grammar
  first, an on-device language model for anything else), replies spoken aloud,
  and hands-free use. None of this depends on how the screen looks: it works
  the same with a full map, a panel or the screen off. On Pleos it can use the
  car's own voice services instead of the bundled ones; there is already a
  branch prepared for that.
- **Itinerary conversation.** Suggesting a plan, adjusting it by voice ("start
  with the castle", "remove the square"), saving it and starting it.
- **Places and searches.** The catalogue, Wikipedia places, photos, and the
  "radar" search that widens until it finds enough.
- **Saved itineraries** and settings (language, action radius, theme).
- **Recovery when the connection drops**, which matters even more in a car.

What changes for the car:

- The **layout**: the panel becomes the app, next to the navigation.
- The **map**: Mapbox is replaced by our own map, and it shows points only.
- The **route line and the cinematic flight along it** are dropped: the
  navigation draws the route.
- The **hand-over** goes through NaviHelper instead of Google Maps or Waze.

## Next steps

1. Build the split-screen demo for clients, on the current phone version.
2. Try MapLibre with OpenStreetMap data as the map for the car version, and
   check how it runs on Pleos hardware.
3. Adapt the layout to a panel next to the navigation, starting from the
   current left-hand panel.
4. When I have an x86_64 PC, run the Connect Waterfall emulator to check split
   screen, markers on Pleos's map and the hand-over through NaviHelper.
