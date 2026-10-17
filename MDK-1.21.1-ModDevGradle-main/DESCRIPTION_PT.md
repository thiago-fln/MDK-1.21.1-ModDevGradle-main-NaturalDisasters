# 🌪️ Natural Disasters: Supercell

**Tornados realistas, supercélulas, tsunamis, enchentes, terremotos e tempestades de areia — movidos por um motor de clima vivo.**

> *O céu está vivo.* Cada bioma tem temperatura, umidade e pressão próprias. Onde o ar quente e seco encontra o ar frio e úmido, nascem tempestades — e algumas viram supercélulas.

---

## ⛈️ Motor de Clima

- **Modelo atmosférico:** temperatura, umidade e pressão por bioma, com ciclo de dia e noite e variação lenta.
- **Frentes e supercélulas:** desertos ao lado de planícies ou oceanos criam frentes instáveis que favorecem tempestades violentas.
- **Ciclo da tempestade:** Chuva → Tempestade de raios → **Nuvem de parede** girando → **Alerta de tornado** → Toque no solo.
- **Alertas:** mensagens no chat e títulos na tela com distância e direção quando o perigo se aproxima.
- **Nuvens volumétricas** substituem as do Minecraft: brancas no tempo bom, cinza-claro na chuva e escuras nas tempestades — com torres de cúmulo-nimbo, bigornas, cortinas de chuva e nuvens de parede rebaixadas.

## 🌪️ Tornados

- **EF1 a EF5**, com tamanho, velocidade de rotação e poder de destruição de acordo com a intensidade.
- **Física de vórtice real:** mobs, jogadores, itens e blocos são puxados, girados, levantados e arremessados para fora do funil.
- **Dano conforme a resistência:** terra, folhas e madeira são arrancadas; a pedra só é arranhada (pedra → pedregulho → cascalho).
- **8 tipos de tornado:**

| Tipo | Descrição |
|---|---|
| **Clássico** | O tornado de livro. Alguns afinam num funil fino e serpenteante antes de sumir. |
| **Multivórtice** | Um vórtice largo com 2 a 4 sub-vórtices violentos girando dentro dele. |
| **Cunha** | Uma parede enorme e escura de detritos tocando o chão. |
| **Corda** | Fino, longo e se contorcendo pelo céu. |
| **Landspout** | Um tornado fraco e poeirento que nasce de tempestades comuns. |
| **El Reno** | O tornado mais largo já registrado: envolto em chuva, com 4 a 6 sub-vórtices e mudanças bruscas de direção. |
| **Tornado de fogo** | Forma-se quando um tornado passa sobre lava ou grandes incêndios. Incendeia tudo. |
| **Tromba d'água** | Forma-se só sobre oceanos e rios e se desfaz em terra. |

- **Visual realista:** funis turbulentos, iluminados pelo sol, que balançam e se curvam (os fracos se curvam mais), funis de condensação que nem sempre tocam o chão e uma enorme **nuvem de poeira** fervendo na base.
- **Detritos de verdade:** partículas com a textura do chão sendo arrancado (grama, terra, areia, madeira, folhas), lançadas para fora e caindo de volta.
- **Efeitos no chão:** saia de poeira, poeira sendo sugada em espiral de longe, sombra no chão, **marcas cicloidais**, vegetação achatada e **árvores descascadas**.
- **Respeita o hemisfério:** os tornados giram no sentido anti-horário no Norte e no sentido horário no Sul.
- **Sem lag:** no máximo 150 blocos suspensos por tornado (configurável); os detritos mais antigos são removidos ou viram itens no chão.

## 🌊 Outros Desastres

- **Tsunamis** — uma parede de água se forma no mar, atinge a costa, arrasta entidades terra adentro, destrói blocos frágeis, inunda a terra e depois recua. Terremotos fortes perto do mar podem provocá-los.
- **Enchentes** — tempestades fortes fazem rios e oceanos transbordarem. A água enche vales como lagos e se espalha rasa em terreno aberto, subindo de meio em meio bloco. É temporária e escoa por completo, sem estragar o seu mundo.
- **Terremotos** — tremor de câmera num raio de 150 blocos, fendas que se abrem no chão e tetos de cavernas desabando.
- **Tempestades de areia** — só no deserto. Visibilidade quase zero, vento uivando e camadas de areia soterrando tudo aos poucos.

## 🌎 Clima

- **Hemisfério Norte ou Sul**, escolhido na tela *Criar Novo Mundo*.
- **El Niño e La Niña** duram **meses no jogo** e mudam temperatura, umidade, frequência de tempestades, atividade de tornados e secas, com efeitos opostos em cada hemisfério.

## 📡 Ferramentas de Sobrevivência

- **Radar Meteorológico** — uma tela de radar funcional cobrindo um raio de 500 blocos:
  - Mapa de refletividade (verde → amarelo → vermelho → magenta) com **eco em gancho** nas supercélulas
  - Mapa térmico da região
  - Marcadores de tornado, caixas de alerta, frentes de tsunami e anéis de terremoto
  - Leituras ao vivo: temperatura, umidade, pressão, vento e El Niño / La Niña
- **Sirene de Tornado** — dispara quando um tornado EF2+ está a menos de 200 blocos e emite sinal de redstone.

## ⌨️ Comandos

Todos os comandos funcionam com `/disaster` e `/disasters` (só operadores).

| Comando | O que faz |
|---|---|
| `/disasters tornado [categoria] [tipo] [distância] [começo]` | Cria um tornado (`começo`: `touchdown` ou `formed`) |
| `/disasters storm [supercell\|rain]` | Cria uma tempestade a favor do vento de você |
| `/disasters weather <sun\|cloudy\|rain\|thunder\|auto> [minutos]` | Força o clima |
| `/disasters tsunami [altura] [distância]` | Envia um tsunami do oceano mais próximo |
| `/disasters flood [altura] [limite]` / `flood stop` | Inicia ou escoa uma enchente |
| `/disasters earthquake [magnitude]` | Inicia um terremoto |
| `/disasters sandstorm` | Inicia uma tempestade de areia |
| `/disasters enso [el_nino\|la_nina\|neutral] [meses]` | Mostra ou força El Niño / La Niña |
| `/disasters hemisphere [north\|south]` | Mostra ou muda o hemisfério do mundo |
| `/disasters info` / `clear` | Leituras do clima / remove todos os desastres |

## ⚙️ Configuração

Quase tudo pode ser ajustado: frequência de tempestades, dano e duração dos tornados, intensidade máxima, limite de blocos suspensos, altura das enchentes, chance de terremotos e tempestades de areia, duração do El Niño, alcance da sirene, densidade de partículas, tremor de câmera, cobertura de nuvens e mais.

## 🎨 Compatibilidade

- **NeoForge 1.21.1** — obrigatório no servidor e no cliente.
- **Shaders Iris** — modo de compatibilidade automático quando um shader pack está ativo *(experimental)*.

---

*Idiomas: English e Português (Brasil).*
