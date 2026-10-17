# 🌪️ Natural Disasters: Supercell

**Realistic tornadoes, supercell storms, tsunamis, floods, earthquakes and sandstorms — driven by a living weather engine.**

> *The sky is alive.* Every biome has its own temperature, humidity and pressure. Where hot, dry air meets cool, humid air, storms are born — and some of them grow into supercells.

---

## ⛈️ Weather Engine

- **Atmospheric model:** temperature, humidity and pressure per biome, with a day/night cycle and slow drift.
- **Fronts & supercells:** deserts next to plains or oceans create unstable fronts that favour violent storms.
- **Storm life cycle:** Rain → Thunderstorm → Rotating **Wall Cloud** → **Tornado Warning** → Touchdown.
- **Alerts:** chat messages and on-screen titles with distance and direction when danger approaches.
- **Volumetric clouds** replace vanilla clouds: white in fair weather, light grey in rain, dark in thunderstorms — with towering cumulonimbus, anvils, rain shafts and lowered wall clouds.

## 🌪️ Tornadoes

- **EF1 to EF5**, with size, rotation speed and destructive power scaling with intensity.
- **Real vortex physics:** mobs, players, items and blocks are pulled in, spun around, lifted and thrown out of the funnel.
- **Block damage by strength:** dirt, leaves and wood are ripped out; stone only gets scratched (stone → cobblestone → gravel).
- **8 tornado types:**

| Type | Description |
|---|---|
| **Classic** | The textbook tornado. Some rope out into a thin, snaking funnel before dying. |
| **Multivortex** | A wide vortex with 2–4 violent suction vortices orbiting inside it. |
| **Wedge** | A massive, dark wall of debris touching the ground. |
| **Rope** | Thin, long and twisting across the sky. |
| **Landspout** | A weak, dusty tornado from ordinary thunderstorms. |
| **El Reno** | The widest tornado ever recorded: rain-wrapped, 4–6 sub-vortices, erratic turns. |
| **Firenado** | Forms when a tornado crosses lava or large fires. Sets everything ablaze. |
| **Waterspout** | Forms only over oceans and rivers and falls apart on land. |

- **Realistic visuals:** turbulent, sunlit funnels that sway and bend (weak tornadoes bend the most), condensation funnels that don't always reach the ground, and a huge **billowing dust cloud** at the base.
- **Real debris:** particles with the texture of the ground being torn up (grass, dirt, sand, wood, leaves), thrown out and falling back down.
- **Ground effects:** dust skirt, inflow dust spiralling in from far away, a shadow on the ground, **cycloidal scour marks**, flattened vegetation and **debarked trees**.
- **Hemisphere-aware:** tornadoes spin counter-clockwise in the north and clockwise in the south.
- **Lag-safe:** at most 150 suspended blocks per tornado (configurable); the oldest debris is removed or dropped as items.

## 🌊 Other Disasters

- **Tsunamis** — a wall of water forms offshore, hits the coast, sweeps entities inland, smashes weak blocks, floods the land and then recedes. Strong earthquakes near the sea can trigger them.
- **Floods** — severe storms make rivers and oceans overflow. Water fills valleys like lakes and spreads shallow over open ground, rising half a block at a time. It is temporary and drains completely, leaving your world untouched.
- **Earthquakes** — camera shake within 150 blocks, fissures tearing the ground open and collapsing cave ceilings.
- **Sandstorms** — desert only. Near-zero visibility, howling wind, and sand layers slowly burying everything.

## 🌎 Climate

- **Northern or Southern hemisphere**, chosen on the *Create New World* screen.
- **El Niño & La Niña** episodes last **months of in-game time**, changing temperature, humidity, storm frequency, tornado activity and droughts, with opposite effects in each hemisphere.

## 📡 Survival Tools

- **Weather Radar** — a working radar screen covering a 500-block radius:
  - Reflectivity map (green → yellow → red → magenta) with **hook echoes** on supercells
  - Thermal map of the area
  - Tornado markers, warning boxes, tsunami fronts and earthquake rings
  - Live readings: temperature, humidity, pressure, wind, El Niño / La Niña
- **Tornado Siren** — wails when an EF2+ tornado is within 200 blocks and outputs a redstone signal.

## ⌨️ Commands

All commands work with both `/disaster` and `/disasters` (operators only).

| Command | What it does |
|---|---|
| `/disasters tornado [category] [type] [distance] [start]` | Spawns a tornado (`start`: `touchdown` or `formed`) |
| `/disasters storm [supercell\|rain]` | Starts a storm system upwind of you |
| `/disasters weather <sun\|cloudy\|rain\|thunder\|auto> [minutes]` | Forces the weather |
| `/disasters tsunami [height] [distance]` | Sends a tsunami from the nearest ocean |
| `/disasters flood [height] [limit]` / `flood stop` | Starts or drains a flood |
| `/disasters earthquake [magnitude]` | Starts an earthquake |
| `/disasters sandstorm` | Starts a sandstorm |
| `/disasters enso [el_nino\|la_nina\|neutral] [months]` | Shows or forces El Niño / La Niña |
| `/disasters hemisphere [north\|south]` | Shows or changes the world's hemisphere |
| `/disasters info` / `clear` | Weather readings / removes every disaster |

## ⚙️ Configuration

Almost everything can be tuned: storm frequency, tornado damage and lifetime, maximum intensity, suspended-block limit, flood height, earthquake and sandstorm chances, El Niño length, siren range, particle density, camera shake, cloud coverage and more.

## 🎨 Compatibility

- **NeoForge 1.21.1** — required on both server and client.
- **Iris shaders** — automatic compatibility mode when a shader pack is active *(experimental)*.

---

*Languages: English and Português (Brasil).*
