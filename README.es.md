# G9 Control Remapper

**Remapeo del mando Legion G9 en la tablet Lenovo Legion Y700.** Fork independiente de
[G9Control](https://github.com/sudo-diego/G9Control).

[English](README.md)

> **Es un fork.** Todo el trabajo de base —luces, sticks, vibración, perfiles, panel de juego, modo del
> control por app— viene del [proyecto original de sudo-diego](https://github.com/sudo-diego/G9Control)
> (GPL-3.0). Lo que agrega **este** repo es el remapeo: está en «Lo que agrega este fork», más abajo.
>
> **No está afiliada a Lenovo.** «Legion» y «Lenovo» son marcas de Lenovo, y «GameSir» de GameSir. Es
> una app independiente y no oficial: habla con el control por USB con un protocolo reconstruido
> estudiando la app oficial, y no contiene código ni recursos de ella.

> **Cómo se hizo.** El código de este fork lo escribió una IA —**DeepSeek**, dentro de
> [Hermes Agent](https://hermes-agent.nousresearch.com)**— a pedido de Roberto, que probó cada versión
> en su tablet y en su mando y fue reportando qué funcionaba y qué no. Cada ronda quedó registrada con
> sus mediciones en [`PROGRESS.md`](PROGRESS.md). (El proyecto original lo escribió Claude Code.)

---

## Lo que agrega este fork

El original **configura** el control. Este fork lo convierte en un **remapeador**: cada botón puede
hacer otra cosa, distinta según la app, y el mando puede sustituir al dedo y al ratón.

### 1. Remapeo de botones — página «Gestos»

- **Tres gestos por botón**: *tocar*, *mantener* y *doble toque*, cada uno con su propia acción. Vale
  para cualquier botón del control, incluidos bumpers, gatillos, el botón guía y los traseros.
- **Acciones**, agrupadas por categoría en un menú de dos pasos:
  - *Sistema*: Atrás, Inicio, Recientes, Notificaciones, Captura, Ajustes rápidos.
  - *Media*: reproducir/pausa, pista siguiente y anterior, adelantar y retroceder el video con
    **deslizamiento** o con **doble toque**, detener, silenciar, subir y bajar volumen.
  - *Pantalla*: subir y bajar brillo.
  - *Apps*: abrir una app concreta.
  - *Launcher*: abrir el cajón de apps, página siguiente y página anterior.
  - *Entradas*: una **tecla del teclado** (una por gesto) o una **secuencia de teclas**.
  - *Ratón*: clic, clic derecho, doble clic, arrastre y rueda (ver el punto 3).
- **Por app** — el selector «Aplica a»: *Todas las apps* (regla general) o una app concreta. Las
  reglas de una app **pisan** a las generales y, con «Nada» en los tres gestos, el botón **pasa tal
  cual** a la app, como si la app no estuviera instalada.
- **Intercepción estilo Key Mapper**: un botón con algo asignado se come el evento; uno sin nada
  asignado pasa directo al juego.
- **«Aprender del mando»**: el botón se elige apretándolo, sin buscarlo en una lista.
- **Indicador por app**: el selector de apps muestra qué botones ya tienen reglas ahí.

### 2. Traseros L4 / R4 / L5 / R5, cada uno con su propio comando

El firmware del G9 solo deja que un trasero **copie un botón** (o corra una macro) y, en la práctica,
los cuatro quedan **indistinguibles**: medido con `getevent`, L4 manda exactamente lo mismo que el
botón Y. Por eso el original solo podía hacer que un trasero copiara algo.

Este fork le da a **cada trasero una firma**: una macro cortísima y única (por ejemplo Vista→Menú, en
pasos de 5 ms) que el servicio **reconoce en la entrada y se come**, así que ya sabe **cuál** trasero
se apretó. Con eso:

- cada trasero puede tener su **propio comando**, y
- cada trasero puede hacer **una cosa distinta según la app** — exactamente lo que el firmware no
  puede hacer solo, porque la firma vive en el mando pero la decisión vive en la app.

Los traseros entran en el mismo motor de gestos que el resto de los botones, así que se configuran en
la misma página y con el mismo «Aplica a».

### 3. Ratón por overlay — L3 + R3

Un puntero **dibujado por la app sobre el juego** (al estilo de Mantis Gamepad Pro) para lo que solo
entiende táctil o ratón, **sin root**:

- **clic** donde está el puntero, **clic derecho** (el menú contextual), **doble clic** y **arrastre**,
  inyectados en la posición del cursor;
- **rueda**, también asignable a la cruceta (la cruceta de este mando llega como **eje**, no como
  teclas, así que tiene su propio camino en el código);
- página **«Ratón»** con velocidad/DPI, suavidad, curva y zona muerta;
- el puntero se enciende y se apaga con el botón que elijas y, **mientras está encendido, los demás
  botones quedan para el ratón** (por diseño: si no, cada botón haría dos cosas a la vez).

### Extras que fue necesitando el uso diario

- **Teclas de teclado y macros**: el mando **no** envía teclas de teclado; las inyecta la app con
  Shizuku. Cada gesto tiene la suya.
- **Ajustes rápidos asignable a cualquier botón** (en el original, solo con el botón guía o L3 + R3).
- **Otros mandos**: si conectas un pad que no es el G9 (probado con un GameSir Cyclone 2 por
  Bluetooth), nada de lo del G9 se le aplica: su botón guía abre los Ajustes rápidos y todo lo demás
  pasa tal cual a la app.
- **Reconexión sola** del G9 cuando la tablet duerme: Android le quita el permiso USB al
  desconectarse, y antes la app se quedaba «sin conexión» para siempre.
- **Cajón de apps** con comprobación propia: la app mira si el cajón abrió de verdad y, si no,
  reintenta una vez.
- **Respaldo completo** en un archivo: perfiles **y** ajustes de la app, pensado también para no
  perder nada al cambiar de firma.
- **Firma propia** del proyecto (no la de depuración): `adb install -r` instala encima **conservando
  los datos**.

---

## Lo que ya venía del proyecto original

Configuración del control, con la app oficial como referencia y sin cuenta:

| | |
|---|---|
| **Luces** | Las dos tiras y el botón Home: fijo, respiración, arcoíris, apagado; color, velocidad y brillo. |
| **Sticks y gatillos** | Zonas muertas internas y externas con vista en vivo, recorrido de los gatillos, gatillo rápido, medición del desvío. |
| **Vibración** | Cinco niveles por lado, prueba con los gatillos. |
| **Botones** | Prueba de botones, intercambio A↔B / X↔Y, bloqueo de diagonales de la cruceta, intercambio stick izquierdo ↔ cruceta. |
| **Traseros** | L4, R4, L5 y R5 copian cualquier botón (incluidos Home y las direcciones de los sticks) o una macro. |
| **Perfiles** | Guardar, aplicar y respaldar configuraciones completas. |
| **Juegos** | Eliges en qué modo se abre cada app; el control cambia al abrirla. |
| **Panel de juego** | Manteniendo el botón Legion (o con L3 + R3), en cualquier app: un panel como los ajustes rápidos de Android, con barras de brillo y volumen, captura, Game Assistant, rendimiento, bypass, pantalla 16:9, Atrás, Inicio, Recientes, ajustes rápidos y modo del control. Se maneja con el stick, la cruceta, LB/RB o el dedo, sin quitarle el foco al juego. |
| **Capturas** | Solo del juego (sin el puntero ni superposiciones), con un aviso con miniatura y sonido como en la Switch. Legion + RB (mantener RB saca una ráfaga) o mantener L3 + R3. |
| **Monitor de rendimiento** | Barra sobre el juego con FPS, CPU (frecuencia y temperatura), GPU (uso y temperatura) y batería; se enciende desde el panel. Los FPS necesitan Shizuku. |
| **Rendimiento** | Ahorro, Equilibrado o Bestia (los de Game Assistant) desde el control, sin tocar la pantalla. Necesita Shizuku. |
| **Bypass charging** | Jugando enchufada, el cargador alimenta la tablet y la batería no carga ni se calienta. Manual (también desde el panel) o automático, con la batería y la temperatura mínimas que elijas. Necesita Shizuku. |
| **Límite de carga** | Enchufada, al llegar al nivel que elijas (70 a 90 %) se activa el bypass: la batería deja de cargar. El de ZUI corta siempre en 80 %. Necesita Shizuku. |
| **Pantalla 16:9** | Para un monitor externo: la imagen lo llena sin bordes. |
| **Monitor externo** | Al conectarlo en modo espejo pasa sola a 16:9 y vuelve a 16:10 al desconectarlo. |
| **Otro control** | Si juegas con otro control, el G9 no cambia de modo ni pide volver a Android. |
| **Idiomas** | Español e inglés. |

---

## Estado

- Versión en desarrollo: **`1.4.17-diag`** (`versionCode` 44). El sufijo `-diag` marca los builds de
  diagnóstico de las rondas de prueba en la tablet.
- Probado en: **Lenovo Legion Y700** (el original apunta a la Gen 3; acá se probó en la Gen 5) con el
  **mando Legion G9** de 2026.
- Android 15 / 16, `minSdk` 29. Necesita el **servicio de accesibilidad** para ver los botones y
  **Shizuku** para lo que Android no deja hacer desde una app normal (teclas de teclado, cajón,
  rendimiento, bypass charging).
- **Todavía no hay Releases publicadas**: el APK sale del flujo de GitHub Actions o de una
  compilación local (ver «Compilar»).
- El detalle de cada ronda de prueba, con las mediciones, está en [`PROGRESS.md`](PROGRESS.md).

## Instalar

1. Compila el APK (ver «Compilar») o baja el artefacto `g9control-apk` de la corrida del flujo en
   GitHub Actions.
2. `adb install -r app-release.apk` — o copia el APK a la tablet y ábrelo (si Android lo pide, permite
   instalar apps desde ese origen).
3. Abre la app: el asistente te pide el **servicio de accesibilidad** y, si quieres las funciones que
   lo necesitan, **Shizuku**.
4. Pon la tablet en el G9 en **modo Android** (indicador azul oscuro). Si está en X-Input (verde) o
   DS5 (blanco), mantén **Vista + Menú** unos 2 segundos. Cuando Android pida acceso USB, marca
   **Usar siempre** y acepta.

> Si venías de la app original del autor, hay que **desinstalarla** una vez: está firmada con otra
> clave y Android no deja actualizar encima con firma distinta.

### El servicio de accesibilidad y Shizuku

Son los mismos permisos que pide el original: el servicio de accesibilidad es lo que deja ver qué app
está al frente y los botones del control (y ahora también alimenta el motor de gestos y el ratón), y
Shizuku es lo que deja inyectar teclas de teclado y hacer lo que la ROM solo permite a apps del
sistema. El detalle completo está en el README del
[proyecto original](https://github.com/sudo-diego/G9Control), y la app lo explica paso a paso en su
asistente y en Ajustes → Permisos.

## Compilar

```bash
./gradlew :app:assembleRelease          # sale en app/build/outputs/apk/release/
```

- JDK 17 y el SDK de Android 35.
- La firma se toma de `keystore.properties` (que **no** va a git) apuntando a la clave del proyecto;
  sin ese archivo compila con la clave de depuración.
- En GitHub, el flujo [`android.yml`](.github/workflows/android.yml) es **solo manual**
  (`workflow_dispatch`), nunca automático, y sube el APK como artefacto `g9control-apk`.
- Las notas del protocolo y de lo probado están en [`CONTEXTO.md`](CONTEXTO.md) (documento del
  proyecto original).

## Capturas

> Pendientes de esta versión. Las de `docs/screenshots/` son del proyecto original y se van a reponer
> por capturas de esta app (Gestos, Traseros, Ratón y el panel).

## Licencia y créditos

- **Base:** [G9Control](https://github.com/sudo-diego/G9Control) de
  [sudo-diego](https://github.com/sudo-diego) — el proyecto que reconstruyó el protocolo del control y
  del que sale casi todo lo que hace funcionar a esta app.
- **Este fork:** [Rarstorm/G9-Control-Remapper](https://github.com/Rarstorm/G9-Control-Remapper),
  mantenido por Roberto ([@Rarstorm](https://github.com/Rarstorm)).
- **Licencia:** [GPL-3.0](LICENSE), la misma del original. Puedes usarla, estudiarla, modificarla y
  compartirla; las versiones modificadas deben seguir siendo de código abierto con la misma licencia.
