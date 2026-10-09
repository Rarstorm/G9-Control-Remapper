# Contexto de G9 Control

App Android propia (Kotlin + Jetpack Compose, paquete `io.github.sudodiego.g9control`) para configurar el **Legion Gaming Controller G9** por USB, sin la app oficial ("Legion" 2.0.51, analizada con jadx). La tablet es una **Lenovo Legion Y700 Gen 3** (ROM ZUI PRC, Android 15, ZUI 17).

Este archivo junta lo que se sabe del protocolo y del control, lo que se descartó y cómo está hecha la app. Está ordenado por tema; el historial detallado está en git (antes del 9 oct 2026 era un registro por versiones).

**Estado (9 oct 2026):** todo lo planeado está hecho y probado. No hay pendientes del control.

---

## 1. Protocolo GCM (USB)

### Conexión
- El G9 en modo Android es VID 0x3537 / PID 0x1134, fabricante "GameSir", producto "Legion Gaming Controller G9", con 2 interfaces HID (3/0/0). La configuración va por la **última interfaz** (la 1), que tiene 2 endpoints interrupt (IN y OUT); se usa `bulkTransfer` en ambos, igual que la app oficial.
- `"…G9_DFU"` es el modo de actualización: **no tocar** (ni DFU ni firmware).
- `G9Usb` rechaza los VID de Sony (054C) y Microsoft (045E): en DS5 el producto USB se sigue llamando "Legion Gaming Controller G9", y si recibe paquetes GCM vibra y falla.
- Paquete: `[cmd, sub, largo, acción (0 leer / 1 escribir / 2 restaurar), datos…, checksum]`. Checksum = suma de los bytes anteriores mod 256. La respuesta repite cmd, sub y acción.
- Comandos que el G9 declara (cmd sub acción): 01 00, 50, 08, 07 1–8 (0/1), 1E 00/04, 1F (0/1/2), 21 (0/1), 23 01/04 (0/1), 25 01 (0/1), 05 0C (0/1/2), 05 08 (0/1/2), 53 FC/00/FE/FD (0/1/2), 15 01/02.
- Escribir colores (o cualquier cosa) muchas veces seguidas podría desgastar la memoria del control. **No hay comando de vista previa sin guardar** (GcmLightingUtil solo tiene leer, escribir y restaurar). La app guarda con espera y solo lo que cambió.

### Modo (comando 8)
- Leer: `08 02 05 00 chk` (la respuesta trae el modo en [4] y el submodo en [5]). Cambiar: `08 02 07 01 modo sub chk`. La respuesta trae el modo en que queda el control: si responde `… 01 01`, lo rechazó.
- **Modos que el G9 acepta:** 1 = Android, **6 = DS5**, 8 = X-Input (Xbox 360), 12 = X-Input. La tabla oficial (`GCMKeyModeV2`) no coincide con el firmware del G9.
- **Rechazados** (responden `01 01` y el control no se reconecta): 5, 7 (DS4), 9 (HID gamepad), 10 (HID teclado) y 11, con submodos 1, 2 y 3. El 9 y el 10 se probaron por si daban botones extra para los traseros.
- La app oficial nunca pone el G9 en DS5: manda 8, que en el G9 es Xbox.
- En X-Input (VID 045E / PID 028E, una interfaz 255/93/1) y en DS5 (VID 054C / PID 0CE6, "Sony Interactive Entertainment Legion Gaming Controller G9", más "Motion Sensors" y "Touchpad") **no hay canal GCM**: la app no puede configurar ni cambiar de modo desde ahí. Solo se vuelve con **Vista + Menú**.

### Vista + Menú (cambio de modo físico)
- Ciclo: Android → X-Input → DS5 → Android.
- El cambio ocurre a los **2,02–2,03 s** de mantenerlos (medido con getevent). `HOLD_MS = 2000`.
- Tras un cambio, el control **no cuenta los botones que siguen apretados**: para el siguiente cambio hay que soltarlos y volver a mantenerlos. Android sí recibe esas teclas como apretadas después de la reconexión, así que la guía y la app las ignoran hasta que se suelten y muestran «Suelta y vuelve a mantener» (`HoldTracker`, compartido por las dos). Solo se pide soltar si Vista + Menú se mantenían hasta 3 s antes del cambio (si cambió la app por USB, la siguiente pulsación ya cuenta); en ese caso, lo que llega apretado en los primeros 800 ms se toma como mantenido desde antes.
- La tablet no puede simular Vista + Menú: el firmware lee los botones físicos.

### Luces (comando 5)
- **Tiras** (`05 0C`): escribir `05 0C 0C 01 tira(1 izq, 2 der) H S L efecto velocidad brillo chk`; leer `05 0C 06 00 tira chk`; restaurar `05 0C 06 02 tira`.
  - H es un byte 0–255 (= 0–360°); S y L, 0–100; velocidad y brillo, 0–255 (la app oficial usa un slider 0–100 convertido a 0–255). Confirmado que ambos cambian en todo el rango.
  - Efectos válidos: 1 fijo, 2 respiración, 4 arcoíris, 255 apagado. 0, 3 y 5–8 quedan como fijo. Velocidad 0 no detiene el arcoíris (lo hace muy lento).
  - En arcoíris el control no guarda saturación ni luminosidad; en fijo no guarda el brillo. Los perfiles comparan solo lo que se guarda (`Light.relevant()`).
  - Las tiras se configuran por separado, pero **el arcoíris solo corre si las dos tiras lo tienen** (confirmado en el control, 9 oct 2026; la app oficial fuerza el mismo efecto en ambas). En la app, poner o quitar el arcoíris cambia las dos tiras.
- **Home** (`05 08`, tecla C2): `05 08 0B 01 C2 H S L efecto velocidad chk`. Solo funciona el efecto 1; respiración y 255 no. Para apagarlo: efecto 1 con L = 0.
- Con el gatillo rápido activado, el anillo de Home se enciende al apretar ese gatillo (aviso del firmware, sin opción para quitarlo).

### Zonas muertas (comando 7)
- `07 id 06 acción valor chk`. Ids: 1 L interna, 2 R interna, 3 L externa, 4 R externa, 5 LT inicio, 6 RT inicio, 7 LT fin, 8 RT fin. Valores 0–100.
- La interna es un recorte con reescalado: con 80 %, la señal queda en 0 hasta el 80 % del recorrido y desde ahí se reescala al máximo.
- Los ids 9 y 10 (anti zona muerta) no están habilitados en el G9.
- **No admite la acción 2** (restaurar). Los predeterminados de la app oficial son sticks 10/100 y gatillos 5/95.

### Vibración
- Intensidad (comando 37 = 0x25): `25 01 0D acción lGrip lTrig lPressed lSync rGrip rTrig rPressed rSync chk`. El firmware solo guarda **0/25/50/75/100** (redondea hacia abajo). No admite la acción 2; predeterminado 75. El G9 no tiene motores en los gatillos.
- Vibración inmediata (comando 4, sin checksum): `04 fuerzaL durL fuerzaR durR fuerzaLT durLT fuerzaRT durRT`, fuerza 0–255. La app oficial la manda cada 200 ms mientras se aprieta un gatillo en su pantalla de prueba; no escribe en la memoria.
- La intensidad se aplica a la vibración de los juegos, que solo llega en X-Input/DS5 (en Android la vibración se va a la tablet).

### Gatillo rápido (comando 33 = 0x21)
- Leer `21 00 01 01 chk`; escribir `21 01 L R chk` (1 activado, 2 desactivado).

### Funciones on/off (comando 35 = 0x23)
- `23 sub 06 acción valor chk`; la respuesta trae el estado en el byte 4.
- **En el G9 solo funcionan el sub 1 (bloqueo de diagonales de la cruceta) y el sub 4 (stick izquierdo ↔ cruceta)**, ambos confirmados en el control.
- Los demás (2 batería baja, 3 intercambio de sticks, 5–9 invertir ejes, 16–17 rango original, 18 jugar mientras carga, 19 encendido al acoplar, 21 LT↔LB, 22 RT↔RB) aceptan la escritura pero al releer vuelven a 0.

### Distribución A/B (comando 30 = 0x1E)
- Leer `1E 00 chk` → `1E 10` (normal) o `1E 04`. Escribir `1E 04 chk` intercambia A↔B y X↔Y (como M + A); `1E 10 chk` lo vuelve a normal.

### Botones traseros (comando 31 = 0x1F, sin byte de largo)
- `1F acción origen destino extra chk`; acción 0 leer, 1 escribir, 2 restaurar. Origen: L4 1, R4 2, L5 3, R5 4. La respuesta trae el destino en el byte 3.
- Destino = código KeyCodeUtil − 256: ↑ B0, ↓ B1, ← B2, → B3, A B4, B B5, X B7, Y B8, LB BA, RB BB, Vista BC, Menú BD, LT BE, RT BF, L3 C0, R3 C1, Home C2 (funciona aunque la app oficial no lo ofrece); stick izq. EF–F6 y stick der. F7–FE en el orden ←, ↖, ↑, ↗, →, ↘, ↓, ↙.
- **Sin asignar: acción 2** (`1F 02 src 00 00`). Escribir destino 0 lo deja como A.
- Teclas de teclado como destino: se guardan pero no envían nada. **Los traseros solo pueden copiar botones existentes**; no hay modo con botones extra.

### Macros (comando 83 = 0x53)
- Primero los ajustes `53 FD 0B btn 01 modo bucle intHi intLo sens chk`, después los pasos `53 00 len btn [esperaHi esperaLo botón mantenerHi mantenerLo]×n chk` (len = 5n + 5, ≤ 11 pasos por paquete; máximo 12 eventos). **Si se escriben los ajustes después de los pasos, los pasos se borran.**
- Tiempos en ms (pasos de 5); la espera cuenta desde que se soltó el paso anterior. Leer pasos `53 FE 06 btn 00` (tiempos ×16 en la respuesta); leer ajustes `53 FD 06 btn 00`; borrar `53 FC 05 btn`. Con macro, el mapeo del trasero lee FF FF.
- Modo 0 = completa al presionar; modo 1 = solo mientras se mantiene. La sensibilidad escrita como 1 se relee como 0 (no se usa).

### Información y restablecimiento
- `01 01` sin checksum → `C9 …` (20 bytes): hardware [8].[7] = 0.1, firmware [12].[11] = 0.52, protocolo GCM [18].[17] = 2.8 (MAC y fecha en ceros).
- `50` sin checksum → `50` + 16 bytes de identificador.
- Restaurar: solo luces, traseros y ajustes de macro admiten la acción 2. La app escribe los predeterminados oficiales para zonas muertas y vibración. Fábrica leída: tiras en fijo celeste (hue 7F, sat 100, lum 50, vel. 128, brillo 128) y Home blanco.
- Comando 21 (0x15): `15 01` / `15 02` parecen restablecer cada stick con valores calculados por el control. No se usa.

### No existe en el G9
- Curvas de respuesta, polling, giroscopio (en DS5 "Motion Sensors" manda valores fijos), tiempo de reposo configurable, luces sin guardar.
- Comandos 0x26, 0x27, 0x54, 0x55, 0x56 y 0xFF (de otros modelos): probados solo como lectura, ninguno responde.

---

## 2. El control y Android

- **Nombres en Android:** Android = "GameSir Legion Gaming Controller G9" (+ "… Consumer Control"); X-Input = "Microsoft X-Box 360 pad"; DS5 = "Sony Interactive Entertainment Legion Gaming Controller G9".
- **Botón M:** es la tecla de función del firmware (M + L4/R4/L5/R5 asigna traseros, M + LT/RT gatillo rápido, M + A intercambia A↔B, M + stick/cruceta luces, M + Vista/Menú vibración, M + botón turbo, M + Home 2 s borra turbos). **No llega a Android como tecla.** M dos veces en Android = KEY_POWER + KEY_VOLUMEDOWN (captura de pantalla); en DS5 = clic del touchpad; en X-Input, nada.
- **Consumer Control:** el control tiene botones propios de volumen y encendido; por eso existe ese segundo dispositivo.
- Home llega como BUTTON_MODE (si Key Mapper no lo intercepta). Tiene retraso porque el firmware espera el doble toque y Home + cruceta.
- Sin manejo de la app, L3/R3 llevan a Inicio.
- El gamepad declara 16 botones (BTN 0x130–0x13F), sin códigos para traseros.
- Jack de 3,5 mm: solo funciona en Android. Vibración de juegos: solo en X-Input/DS5. Son límites del firmware.
- Indicador por modo (manual): Android azul oscuro, DS5 blanco, Xbox verde, G-Touch celeste. Reposo tras 10 min sin uso.
- Vista + Menú + Home = calibración.

### La tablet (ZUI)
- Para listar apps hace falta `com.android.permission.GET_INSTALLED_APPS` (lo administra `com.zui.safecenter`).
- `ACTION_USER_PRESENT` no llega en esta tablet. Para saber cuándo se desbloqueó, desde `SCREEN_ON` se revisa `KeyguardManager.isKeyguardLocked` cada 300 ms.
- **`am force-stop` desactiva el servicio de accesibilidad** (`adb install -r` no). Si pasa, restaurarlo con:
  `settings put secure enabled_accessibility_services "io.github.sds100.keymapper/.system.accessibility.MyAccessibilityService:io.github.sudodiego.g9control/.AutoModeService"`
- Apps instaladas por APK en Android 13+: puede aparecer "Ajuste restringido" al activar la accesibilidad (Ajustes → Apps → G9 Control → ⋮ → "Permitir ajustes restringidos").
- Probado con **Cocoon Shell** (`rip.moth.cocoonshell`) como launcher y **Key Mapper** para remapeos del sistema. Al abrir un juego desde Cocoon aparecen varias ventanas en < 0,5 s (incluida la barra de juegos de ZUI), por eso `notificationTimeout=0`.
- La tablet de pruebas tiene **debloat**: varias apps de Lenovo/ZUI no están instaladas para el usuario 0 (por ejemplo `com.zui.wifip2p`, Legion Zone, X-Touch). Comprobar con `pm list packages` (sin `-u`) antes de contar con una app del sistema.
- ROM: `TB321FU_CN_OPEN_USER_Q00011.0_V_ZUXOS_1.1.10.138_ST_250626`, build `user`. Nombre interno del modelo: **Kirby** (PRC).

### Game Assistant de ZUI (`com.zui.game.service`)
Analizado con jadx el 9 oct 2026 (v2.3.0.4534, `/system/priv-app/ZuiGameHelper`). La APK y el código descompilado no se incluyen en el repositorio.
- **Quién decide qué es juego:** `ZuiModeService` (servicio `zuimode`, dentro de `system_server`). Usa una lista de la ROM (`game_list_kirby`) más la del usuario (`addUserManagedApp`/`removeUserManagedApp`). En logcat: `ZuiModeService: judgeGameMode …`. Una app de terceros no puede leer ni cambiar esa lista.
- Al entrar a un juego el sistema arranca `GameHelperService` (permiso de firma) y le avisa con `IZuiGameAppStateListener` (`onGameAppStart`/`onGameAppExit`); al salir se detiene. Siempre quedan corriendo `RockerService` (joystick táctil), `ConsoleService` (conexión con PC Legion, no con controles) y `ScreenRecorderService` (Highlights).
- **No maneja controles físicos:** ningún código lee botones ni la conexión de un gamepad. La "vibración 4D" hace vibrar la tablet según toques en juegos específicos (Honor of Kings). "Mapeo de teclas" llama a `com.zui.wifip2p`, que no está instalada.
- **Señal pública de modo juego (confirmada en vivo):** mientras hay juego, `Settings.System game_helper_side_bar` = `lado#arriba#abajo` (lado 1 izquierda, 2 derecha; arriba/abajo en px de la pestaña lateral). Sin juego: `0#0#0`. Con Dolphin dio `2#701#931`. La clave no está declarada en `framework.jar`, así que la restricción de Android 12+ a claves ocultas no debería aplicarle; falta confirmarlo leyéndola desde G9 Control.
- **Abrir el panel:** deslizando horizontalmente desde la pestaña lateral (≥ 100 px, o ≥ 50 px con velocidad ≥ 500). Deslizar desde arriba ya no hace nada (`onTopDown` está vacío en esta versión).
- **Ventanas:** `TYPE_APPLICATION_OVERLAY` (2038), no enfocables (no se navegan con el control). Las de G9 Control (`TYPE_ACCESSIBILITY_OVERLAY`) quedan por encima.
- **Transparencia:** el fondo del panel es fijo en el recurso (`floating_bg_for_pad` = `#d9000000`, negro al 85 %). No hay ajuste. La app no declara recursos `overlayable` y la ROM es `user`: sin root no se puede cambiar con un overlay (RRO). Solo las burbujas de notificación tienen opción de opacidad.
- **Modos de rendimiento** (botón del panel): 1 ahorro de energía, 0 equilibrado, 2 rendimiento ("Bestia", fondo rojo). Se escriben en `content://com.zui.performance.utils.GameModeProvider/contact` (`gameMode`), que es de `com.zui.pp` (uid de sistema) y pide permiso de firma: G9 Control no puede cambiarlos. Se recuerdan por app.
- **Captura:** se conecta a `com.android.systemui/.screenshot.TakeScreenshotService` (igual que la captura normal). Quedan en `Pictures/Screenshots/screenshot_AAAAMMDD_HHMMSS.png`. Un servicio de accesibilidad logra lo mismo con `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`.
- **Grabación:** la del sistema es `com.zui.screenrecorder.ScreenRecorderService` en SystemUI (permiso de firma) y guarda en `Pictures/screenrecorder/`. Highlights es propia (MediaProjection), en `Movies/Highlight`, controlada con acciones `highlight.action.*` de permiso de firma.
- **En Ajustes:** la entrada "Game Assistant" la inyecta la propia app (`OptionActivity` con `com.android.settings.action.IA_SETTINGS`, `keyhint=top_level_game_assistant`); Ajustes solo acepta eso de apps del sistema. Las opciones se guardan en `Settings.Global key_game_assistant_*` (y `key_game_master_switch`).
- **Lo que una app normal puede usar:** abrir `OptionActivity` y `ui.sort.SortActivity` (exportadas sin permiso); leer frecuencias de CPU/GPU en `content://com.zui.game.service.hardwareinfo/{cpu_cur,cpu_max,gpu_cur,gpu_max}` declarando `com.zui.game.service.read.hardwareinfo` (permiso normal; no probado desde una app).

---

## 3. La app

### Estructura
- `MainActivity.kt`: estado (`AppState`), lectura y guardado en el control, navegación con el control (A elige, LB/RB pestañas, mantener B 3 s sale; 1 s sale de la prueba o de grabar una macro), respaldo de perfiles.
- `Ui.kt`: armazón (pestañas, franja de modo, barra inferior con ayudas) y componentes compartidos.
- Páginas: `PageHome`, `PageLights`, `PageSticks`, `PageVibration`, `PageTest` (Controles), `BackKeys` (Traseros) + `Macros`, `PageProfiles`, `PageAuto` (Juegos), `PageDiagnostics` (Ajustes).
- `Gcm.kt` (paquetes), `G9Usb.kt` (USB), `ModeSwitcher.kt` (modo y `Prefs`), `Profiles.kt`, `Theme.kt`, `ControllerArt.kt` (dibujo vectorial del G9).
- `AutoMode.kt`: `AutoModeService` (accesibilidad): cambio de modo por app, retiene Vista/Menú mientras la guía los pide, bloqueo opcional.
- `ModeGuide.kt`: guía y avisos de cambio de modo (ventana de accesibilidad con Compose).
- `HoldTracker.kt`: seguimiento de Vista + Menú, el mismo para la guía y la franja de la app.
- `ModeTiles.kt`: íconos de ajustes rápidos Android, X-Input y DS5 (con el logo G9). `UsbAttachActivity.kt`: guarda el permiso USB al conectar sin abrir la app.

### Decisiones
- **Guardado automático** con espera (luces 2 s tras 0,6 s sin cambios; sticks 0,7 s; vibración 0,5 s), solo lo que cambió, sin guardar mientras se arrastra un slider, y verificando al releer.
- **Cambio por app** con un servicio de accesibilidad (`typeWindowStateChanged`, sin leer contenido): avisa al instante y Android lo mantiene vivo. X-Input y DS5 se activan solos desde Android; para volver, guía de Vista + Menú.
- **Avisos** (Ajustes → Avisos de cambio de modo): al abrir un juego (tarjeta o aviso pequeño), al salir (aviso pequeño) y cuando la app cambia el modo (etiqueta). Cada uno con su bloqueo: Nada / Botones (botones y pantalla) / Todo (también sticks y gatillos). Salida de emergencia: mantener la pantalla 2 s.
- **Bloquear sticks y gatillos** solo se puede si la ventana de la guía toma el foco (el servicio filtra teclas, no movimientos): el juego pierde el foco y puede pausarse. Por eso es opcional.
- Dentro de la app, Vista, Menú, LT, RT, L3 y R3 no hacen nada (LT/RT se usan para probar gatillos y vibración).
- **Franja de modo** (app, fuera de Android): se comporta como la guía (barra, soltar y volver a mantener, «¡Bien! Una vez más», «Listo» en verde). Lleva a Android o al modo que se toque en Inicio → Modo del control.
- **Ajustes rápidos:** el ícono del modo actual queda encendido. Desde Android, X-Input/DS5 cambian por USB (con la etiqueta del modo si el servicio está activo). Desde otro modo, el servicio cierra el panel y muestra el aviso pequeño de Vista + Menú hacia ese modo (reemplaza la guía que hubiera). Sin el servicio, abren la app, que guía con la franja.
- El widget se quitó (9 oct 2026): poco útil y no se veía desde la app.
- Con G9 Control al frente, el servicio quita su guía (si no, se quedaría con Vista y Menú y la franja de la app no avanzaría) y la vuelve a mostrar al salir si sigue pendiente volver a Android.
- La etiqueta del modo (cuando la app cambia el modo) se puede apagar en Ajustes.
- Las ventanas de la guía siempre cubren la pantalla (sin bloqueo no reciben toques): así las animaciones del aviso pequeño no se recortan.
- Perfiles en SharedPreferences (JSON): luces, zonas muertas, gatillo rápido, cruceta, vibración, traseros y macros. Al aplicar, solo se escribe lo que difiere. Respaldo exportable a un archivo.

### Compilar, instalar y depurar
- Debug: `.\gradlew.bat assembleDebug`. Release: `.\gradlew.bat assembleRelease` (R8; `proguard-rules.pro` conserva los nombres de los enum), ~2,4 MB.
- **Firma (desde el 9 oct 2026):** la release se firma con una clave propia; `keystore.properties` (en la raíz, fuera de git) indica dónde está. Sin ese archivo se firma con la clave de depuración, así cualquiera puede compilar. Las APK firmadas con claves distintas no se instalan una encima de otra: hay que desinstalar (se pierden los ajustes; exportar los perfiles antes).
- Respaldos de APK en `respaldos/` (no van a git).
- Atajos de depuración (solo APK debug): `adb shell am start -n io.github.sudodiego.g9control/.MainActivity --es g9hex "08,02,05,00"` envía bytes con checksum; `g9raw` sin checksum. Separar con comas. En cualquier versión: Ajustes → tocar "Versión" 5 veces → Consola.
- Registros: `adb logcat -s G9Usb` (USB) y `G9Auto` (servicio).

---

## 4. Ideas descartadas (no volver a proponer)
- Vibración generada a partir del audio: no es precisa.
- Copiar la vibración de la tablet al control con Shizuku: invasivo (vibrarían los dos a la vez).
- Remapear el doble M o Home con Shizuku: lo resuelve con Key Mapper. Respaldo previo: tag `antes-shizuku`.
- Modo de prueba especial para GameNative.
- G-Touch (modo 2): depende de un servicio propio de la app oficial.
- Probar comandos al azar en escritura (riesgo de DFU).
- Apagar luces al bloquear la tablet: posible, pero sumaría escrituras.
