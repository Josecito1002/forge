# Commander Spire — modo nuevo en un fork de Forge

Plan de una semana de prueba. Al final del día 3 y del día 7 hay un punto de decisión: seguir o cortar.

## Qué es

Un roguelike tipo Slay the Spire dentro de Forge (Android). El jugador usa un mazo de commander como pool de cartas, empieza cada partida con un mazo chico y lo hace crecer con recompensas después de cada combate. Los combates usan el motor de reglas de Forge contra monstruos automáticos.

Documento de diseño completo (reglas físicas): https://claude.ai/code/artifact/56492e17-a123-4669-9888-b8bde7c6155f

## Reglas que el modo digital debe implementar

- **Preparación:** el mazo de commander se separa en commander, tierras y pool (el resto, barajado).
- **Mazo inicial:** 12 cartas al azar del pool, sin importar coste. Un mulligan único: cambiar 1 carta por la de arriba del pool.
- **Cartas volteadas:** el jugador elige 4 de las 12. En digital, una carta volteada se reemplaza en combate por una carta personalizada "Carta básica": cuesta {1} y es modal: 2 de daño a un monstruo, o prevenir los siguientes 3 de daño a un jugador.
- **Tierras:** no van en el mazo. Cada combate empieza con 3 tierras en el campo y el jugador pone 1 más por turno desde un montón aparte (sin tope). Las 3 iniciales no disparan landfall; una tierra que regresa a la mano se queda en la mano.
- **Commander:** empieza cada combate en la zona de mando; impuesto normal, que se reinicia en cada combate.
- **Companion:** como en Magic normal.
- **Vida:** 40 máxima, se arrastra entre combates.
- **Recompensa por combate:** revelar 3 cartas del pool, tomar 1 o ninguna; las demás al fondo del pool. Élite: además oro y una reliquia. Jefe: revelar 5, tomar 1, curación total.
- **Mapa por acto:** columnas de nodos (combate, élite, evento, tienda, fogata, jefe). 3 actos.
- **Fogata:** curar 12, voltear 1 carta normal o devolver 1 volteada a su lado normal.
- **Remoción contra élites y jefes:** destruir o exiliar les quita 8 de vida en vez de matarlos.

## Lo que ya existe en Forge y se puede reutilizar

Revisado en `forge-gui-mobile/src/forge/adventure/data`:

- `EnemyData`: vida, mazo, `boss`, `equipment`, recompensas y `nextEnemy` (sirve para fases de jefe).
- `RewardData` con `sourceDeck`: saca cartas al azar de un archivo de mazo; se puede filtrar por coste, rareza y tipo. Sirve para las recompensas desde el pool.
- `EffectData.startBattleWithCard`: el enemigo empieza con cartas en el campo. Sirve para las pasivas de monstruo.
- Cartas personalizadas: Forge define las cartas como scripts de texto, así que los monstruos y la "Carta básica" se pueden escribir como cartas propias.

## Por investigar el día 1 (no confirmado)

- Cómo inicia un duelo el Adventure Mode (`DuelScene` o similar) y si se puede llamar con un mazo armado en código y un rival con vida personalizada.
- Cómo evitar que el monstruo pierda por quedarse sin cartas en la biblioteca.
- La forma más simple de que un monstruo "actúe" cada turno: probablemente una carta en su zona de mando con habilidades disparadas al inicio de su turno.
- Si hay forma de mostrar la intención del monstruo antes de su turno (texto en la carta, una etiqueta en la UI).
- Dónde agregar un botón nuevo en el menú principal móvil.

## Plan de la semana

1. **Día 1 — Entorno.** Instalar JDK 17, Maven y el SDK de Android. Hacer fork de Card-Forge/forge, clonar y compilar. Correr `forge-gui-mobile-dev` (la interfaz móvil en la PC) para no compilar el APK cada vez. Revisar los puntos de "Por investigar".
2. **Días 2 y 3 — Un combate.** Botón "Commander Spire" en el menú. Cargar un mazo de commander, armar el mazo de 12 y pelear contra la Rata gigante (10 de vida, ataca por 4) con el motor de Forge.
   - **Punto de decisión:** si al final del día 3 no hay un combate jugable, cortar.
3. **Día 4 — La partida.** Estado de la partida entre combates (vida, mazo, pool), pantalla de recompensa con 3 cartas del pool, cartas volteadas como "Carta básica".
4. **Día 5 — Mapa.** Mapa de nodos simple, fogata y un jefe con fase 2 (`nextEnemy`).
5. **Día 6 — APK.** Compilar e instalar en el teléfono.
6. **Día 7 — Jugar y decidir.** Jugar un acto completo. Si es divertido, seguir con eventos, tienda, élites y actos 2 y 3; si no, cortar.

## Fuera de alcance esta semana

Eventos, tienda, reliquias, actos 2 y 3, varios jugadores y gráficos propios.

## Monstruos de prueba

| Monstruo | Vida | Acciones (en ciclo) |
| --- | --- | --- |
| Rata gigante | 10 | Ataque 4 · Ataque 4 · Fuerza +1 · Ataque 2 y descartas 1 al azar |
| Esqueleto guardián | 14 | Ataque 6 · Escudo 6 · Ataque 5 y escudo 3 · Ataque 6 |
| Rey rata (jefe) | 40 | Invoca 2 ratas · Ataque 8 · Escudo 10 · Ataque 6 a cada jugador. Fase 2 a la mitad de vida: actúa dos veces por turno |

## Licencia

Forge es GPL-3.0. El fork debe quedar público (o con el código disponible para quien reciba el APK).
