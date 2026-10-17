# Natural Disasters: Supercell — 1.1.0

## ✨ New

### ⌨️ Weather command
- **`/disasters weather <sun|cloudy|rain|thunder|auto> [minutes]`** — force the weather (default 10 minutes, up to 24 hours).
  - `sun` clears the sky and removes storm cells.
  - `cloudy` gives a light grey overcast sky without rain.
  - `rain` and `thunder` place a storm cell over every player (visible on the radar).
  - `auto` hands control back to the weather engine.
  - While the weather is forced, no new storms form on their own.
- **`/disasters`** now works as an alias of `/disaster` for every command.

### 🌪️ More realistic tornadoes
- **Turbulent funnels** — the column is no longer a perfect cylinder: rotating bulges and irregular striations.
- **Sunlight** — the side facing the sun is brighter, changing with the time of day.
- **Condensation gap** — weak tornadoes (EF1–EF2) have a funnel that hangs above the ground, with only the debris whirl touching down.
- **Billowing dust cloud** at the base — a wide dome of dust that rises, spreads outwards and swirls with the vortex. Its size follows the category, and its colour the type: brown dust, dark smoke (firenado), white spray (waterspout).
- **Real debris** — particles with the texture of the ground being torn up (grass, dirt, sand, wood, biome-tinted leaves) spiral up the funnel, get flung out and fall back down.
- **Inflow** — dust dragged in along the ground from far outside the funnel, turning faster as it gets closer.

### 🟫 Ground effects
- **Cycloidal scour marks** — suction spots inside EF2+ tornadoes carve looping tracks in the grass.
- **Scoured ground** — grass, podzol, mycelium and paths are stripped down to dirt (coarse dirt for EF3+).
- **Debarked trees** — tree trunks that survive the wind lose their bark.
- **Flattened vegetation** — tall grass, ferns and flowers in the inflow are torn away.
- **Dirt kicked up** around the base with the texture of the ground block.
- **Dust skirt** racing around the base and a **shadow** on the ground.

## 🔧 Improvements
- Waterspouts skip ground effects (nothing to scour over water).
- More stable automated tests (tornadoes turning into firenadoes over leftover lava no longer count as a failure).

---

# Natural Disasters: Supercell — 1.1.0 (Português)

## ✨ Novidades

### ⌨️ Comando de clima
- **`/disasters weather <sun|cloudy|rain|thunder|auto> [minutos]`** — força o clima (padrão 10 minutos, até 24 horas).
  - `sun` limpa o céu e remove as células de tempestade.
  - `cloudy` deixa o céu nublado cinza-claro, sem chuva.
  - `rain` e `thunder` colocam uma célula de tempestade sobre cada jogador (aparece no radar).
  - `auto` devolve o controle ao motor de clima.
  - Enquanto o clima está forçado, não se formam tempestades novas sozinhas.
- **`/disasters`** agora funciona como atalho de `/disaster` para todos os comandos.

### 🌪️ Tornados mais realistas
- **Funis turbulentos** — a coluna não é mais um cilindro perfeito: ondulações que giram e estrias irregulares.
- **Luz do sol** — o lado virado para o sol fica mais claro, mudando com a hora do dia.
- **Funil suspenso** — tornados fracos (EF1–EF2) têm o funil de condensação acima do chão; só o redemoinho de detritos toca o solo.
- **Nuvem de poeira** na base — uma cúpula larga de poeira que sobe, se espalha e gira com o vórtice. O tamanho acompanha a categoria e a cor depende do tipo: poeira marrom, fumaça escura (tornado de fogo), borrifo branco (tromba d'água).
- **Detritos de verdade** — partículas com a textura do chão sendo arrancado (grama, terra, areia, madeira, folhas na cor do bioma) sobem em espiral, são arremessadas e caem de volta.
- **Vento de entrada** — poeira puxada pelo chão de longe em direção ao funil, girando cada vez mais rápido.

### 🟫 Efeitos no chão
- **Marcas cicloidais** — pontos de sucção dentro de tornados EF2+ deixam laços na grama.
- **Chão raspado** — grama, podzol, micélio e caminhos viram terra (terra grossa nos EF3+).
- **Árvores descascadas** — troncos que resistem ao vento perdem a casca.
- **Vegetação achatada** — grama alta, samambaias e flores na área de sucção são arrancadas.
- **Terra levantada** em volta da base com a textura do bloco do chão.
- **Saia de poeira** girando em volta da base e **sombra** no chão.

## 🔧 Melhorias
- A tromba d'água não aplica efeitos no chão (não há o que raspar sobre a água).
- Testes automáticos mais estáveis (tornados que viram tornado de fogo sobre lava que sobrou de outro teste não contam mais como falha).
