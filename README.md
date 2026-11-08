# Dash X - Car Launcher

Könnyű, nulla külső függőségű Android car launcher fejegységekre és telefonra.

- **Orbit tárcsa** – 61 szegmensből álló gyűrű: GPS-sebesség (nem kell OBD) vagy óra. Érintés = váltás, hosszú nyomás = menü (szín, teljes képernyő, alapértelmezett launcher, lejátszási infó engedély).
- **6 programozható gomb** – hosszan nyomva bármelyik gombra bármilyen appot rá lehet tenni (vagy a beépített Rádió / Összes app funkciót).
- **Beépített internetes rádió** – világméretű keresés (radio-browser.info), kedvencek (hosszú nyomás), háttérben is szól (foreground service), prev/next a kedvencek között.
- **Now playing** – Spotify / YouTube Music / stb. címe + vezérlés (Értesítés-hozzáférés kell hozzá, a sávra koppintva megnyílik a beállítás). Ha nincs engedély, a gombok általános media-key-t küldenek.

## Build

Push a `main` ágra -> GitHub Actions (`.github/workflows/build.yml`) lebuildeli, az APK-k az **Actions → futás → Artifacts** alatt vannak (`DashX-apk`).
`v0.1.0` formátumú tag pusholásakor Release is készül az APK-val.

Lokálisan: `gradle assembleDebug` (Gradle 8.9, JDK 17).

## Telepítés a fejegységre

1. APK telepítés (pendrive / adb).
2. Első indításnál engedélyezd a helyadatot (sebességhez).
3. Beállítások → Alapértelmezett launcher → Dash X.

## Megjegyzés a rádióhoz

A fejegység *hardveres* FM/DAB tunere gyártónként más, általános módon nem vezérelhető. Ha azt akarod, tedd rá a gyári rádió appot egy gombra (hosszú nyomás → Alkalmazás kiválasztása).
