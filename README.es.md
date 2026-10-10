# G9 Control

**App de código abierto y sin cuenta para configurar el Legion Gaming Controller G9 en la tablet Lenovo Legion Y700 Gen 3.**

[English](README.md)

> **No está afiliada a Lenovo.** "Legion" y "Lenovo" son marcas de Lenovo. Esta es una app independiente y no oficial. Se comunica con el control por USB con un protocolo reconstruido estudiando la app oficial y probando el control; no contiene código ni recursos de la app oficial.

> **Esta app está hecha con vibe coding.** Todo el código, la investigación del protocolo y este README los escribió Claude (la IA de Anthropic) mediante Claude Code. Yo no escribí el código: describí lo que quería, probé cada versión en mi tablet y mi control, y conté qué funcionaba y qué no.

![Inicio](docs/screenshots/home.png)

## Por qué

La app oficial de Legion funciona, pero pide una cuenta y trae un launcher y muchas cosas extra. G9 Control hace una sola cosa: configurar el control. Sin cuenta, sin acceso a internet y sin publicidad.

Además hace cosas que la oficial no puede:

- **Cambia a modo DS5 por software.** La app oficial manda un modo que deja el G9 en Xbox.
- **Cambia de modo solo según el juego** (X-Input o DS5), con una guía en pantalla para volver a Android.
- **Graba macros con el propio control**, con un editor de línea de tiempo, y permite que los traseros copien Home o las diagonales de los sticks.
- **Perfiles** con respaldo en archivo, **medición del desvío de los sticks** y **navegación completa con el control**.
- **Cuida la memoria del control**: solo escribe lo que cambió, después de una pausa breve, y nunca en bucle.

## Funciones

| | |
|---|---|
| **Luces** | Las dos tiras y el botón Home: fijo, respiración, arcoíris, apagado; color, velocidad y brillo. |
| **Sticks y gatillos** | Zonas muertas internas y externas con vista en vivo, recorrido de los gatillos, gatillo rápido, medición del desvío. |
| **Vibración** | Cinco niveles por lado, prueba con los gatillos. |
| **Controles** | Prueba de botones, intercambio A↔B / X↔Y, bloqueo de diagonales de la cruceta, intercambio stick izquierdo ↔ cruceta. |
| **Traseros** | L4, R4, L5 y R5 copian cualquier botón (incluidos Home y las direcciones de los sticks) o una macro. |
| **Perfiles** | Guardar, aplicar y respaldar configuraciones completas. |
| **Juegos** | Eliges en qué modo se abre cada app; el control cambia al abrirla. |
| **Panel de juego** | L3 + R3 en cualquier juego: captura solo del juego (sin el puntero ni superposiciones) con un aviso con miniatura, Game Assistant y cambio de modo. |
| **Botón Legion** | Tocar: Atrás. Mantener: ajustes rápidos. |
| **Ajustes rápidos** | Íconos Android, X-Input y DS5. |
| **Idiomas** | Español e inglés. |

## Requisitos

- Lenovo Legion Y700 Gen 3 con el Legion Gaming Controller G9 (el control está hecho para esta tablet).
- Probada con el firmware 0.52 del control en ZUI 17 (Android 15). Android 10 o superior.

## Instalación

1. Descarga `G9Control-x.y.z.apk` desde [Releases](../../releases).
2. Ábrela en la tablet. Si Android lo pide, permite instalar apps desde ese origen.
3. Abre G9 Control. Una configuración inicial te guía con el resto:
   - Coloca la tablet en el G9 en **modo Android** (indicador azul oscuro). Si está en X-Input (verde) o DS5 (blanco), mantén **Vista + Menú** unos 2 segundos.
   - Cuando Android pida acceso USB, marca **Usar siempre** y acepta.
   - Opcional: activa el **servicio de accesibilidad** para el cambio automático (ver abajo).
   - Opcional: agrega los íconos **Android / X-Input / DS5** a los ajustes rápidos.

### Sobre el servicio de accesibilidad

El cambio automático por juego, la guía de Vista + Menú, los íconos de ajustes rápidos (desde X-Input/DS5), el panel de juego (L3 + R3) y el botón Legion usan un servicio de accesibilidad. Esto es lo que hace con él:

- **Ve qué app está en pantalla** (cambios de ventana), para cambiar el modo según el juego.
- **Ve los botones del control**, para seguir Vista + Menú en la guía, manejar el panel de juego y el botón Legion y, solo si lo eliges, bloquear los botones mientras hay un aviso en pantalla.
- **Saca capturas cuando se lo pides** (desde el panel, con L3 + R3 mantenidos o con M dos veces en DS5): solo de la ventana del juego, guardadas en la tablet (Imágenes/Screenshots).
- **Hace un solo gesto:** el deslizamiento que abre el Game Assistant de la tablet (Lenovo ZUI) desde su pestaña lateral.

Para capturar solo la ventana del juego, Android exige el permiso de ver el contenido de las ventanas: sin él no dice cuál es la ventana del juego. Por eso, al activarlo, Android avisa que la app puede leer la pantalla. G9 Control usa ese permiso solo para la lista de ventanas (cuál es la del juego): no lee sus textos ni sus botones y no ve lo que escribes. La app no tiene permiso de internet, así que nada sale de la tablet.

Si Android muestra **«Ajuste restringido»** al activarlo: Ajustes → Apps → G9 Control → ⋮ (arriba a la derecha) → **Permitir ajustes restringidos**, y vuelve a intentarlo.

## Limitaciones

- **No actualiza el firmware.** Es a propósito: una actualización que falla puede dejar el control inservible. Si alguna vez lo necesitas, usa la app oficial de Legion una vez.
- La app solo configura el control en **modo Android**. En X-Input y DS5 el control no tiene canal de configuración, así que volver a Android siempre es con Vista + Menú (la app te guía).
- En modo Android, los juegos hacen vibrar la tablet, no el control (comportamiento del firmware).

## Compilar

Con Android Studio (o solo el JDK que trae):

```
./gradlew assembleRelease
```

Sin un archivo `keystore.properties`, la versión release se firma con la clave de depuración. Las notas del protocolo y todo lo que se probó están en [CONTEXTO.md](CONTEXTO.md).

## Licencia

[GPL-3.0](LICENSE). Puedes usarla, estudiarla, modificarla y compartirla; las versiones modificadas deben seguir siendo de código abierto con la misma licencia.
