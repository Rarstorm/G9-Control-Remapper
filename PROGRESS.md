# PROGRESS — G9 Remapper

## 2026-10-10
- F1 ✅ Fork `Rarstorm/G9Control` creado (HTTP 202 verificado) y clonado en `C:\Users\Roberto\G9Control`.
- ✅ Rama `g9-remap-v2` creada (base: main = upstream v1.0.0 + commits posteriores).
- ✅ Validación GCM en hardware (mando Legion G9 2026 + Y700 Gen 5): comandos 1F/53/08 responden.
- ✅ Vault: `10-g9-remap-plan.md` (plan + estado) y `00-estado-default.md` actualizados.
- [x] Leer código clave: AutoMode.kt (onKeyEvent/handleKey/onAccessibilityEvent), Shell.kt, Gcm.kt, BackKeys.kt, Profiles.kt, PageAuto.kt, MainActivity.kt, auto_mode_service.xml.
- [x] Diseño anclado a código verificado (ver PLAN.md → "Diseño anclado al código").
- [x] Workflow `android.yml` creado — SOLO `workflow_dispatch` (nunca build automático).
- [x] F2a: build CI EJECUTADO (orden explícita de Roberto, 2026-10-10) → **success**, todos los pasos verdes. Run: https://github.com/Rarstorm/G9Control/actions/runs/38093711718 · Artefacto `g9control-apk` (1,303,200 bytes, id 11684689925).
  - Fix necesario para el verde: `android-actions/setup-android@v3 → @v4` (v3 pedía por default el paquete legacy `tools`, removido por Google → `sdkmanager` exit 1). Commit `7eee2a0`.
- [x] F3 APK instalado en tablet (10-10-2026): renombrado a **"G9 Control Remapper"** (commit 4929821), build verde (run 38095749437), `adb install` = **Success** (versionName 1.2.0, versionCode 5), app lanzada. El G9Control original (firma del autor) se desinstaló antes — el APK del CI va firmado con **debug key** (incompatible encima de otra firma).
- [x] Renombre COMPLETO a "G9 Control Remapper": `app_name` + `auto_service_label` + los 16 textos hardcodeados en 7 archivos. Commits 4929821 y c5a333b. Verificado en el APK (0 "TBF Companion").
- [x] Asistente de configuración (commit 127eb54, build verde run 38099124670): aviso legal/privacidad al inicio (GPL-3, sin relación con Lenovo/GameSir, sin internet, permisos bajo responsabilidad) + la accesibilidad pasa a paso propio (ya no "opcional") con guía del candado y botón a Ajustes de la app (`AutoRules.openAppInfo`).
- [ ] Distribución: GitHub Releases → Obtainium / F-Droid (abierta primero). Pendiente: **keystore propio** (hoy firma debug, cambia por build).
- [x] F4 **motor de gestos** (commit 659e667, build verde run 38099742419): página «Gestos» — por cada botón del control se define qué hace al **tocar / mantener / doble toque**: Atrás, Inicio, Recientes, Notificaciones, Reproducir/Pausa, subir/bajar volumen, Captura. Nuevos `Gestures.kt` (modelo + `GestureEngine`) y `PageGestures.kt`; enganchado en `AutoModeService.handleKey` (tras la guía/panel, y solo si la app no está al frente). Los traseros entran aquí por el botón que mandan (se eligen en «Traseros»). **Pendiente: probar en la tablet.**
- [x] F5 **gestos por app** (commit c4f4381, build verde run 38100998307): en «Gestos», un selector «Aplica a» con **Todas las apps** (perfil general) + cada app. Las reglas propias de una app **pisan** las generales; poner «Nada» en los tres dentro de una app = **dejar pasar** el botón (ahí funciona normal). Se guarda en la misma sp "g9" (`g_rules` general, `g_rules__<paquete>` por app) y el motor resuelve con el paquete al frente (`GestureEngine.onKey(ctx, pkg, …)`). **Pendiente: probar en la tablet.**
- [x] **UI del debug 10-10** (commit f7fcbb8): Traseros editable con aviso + «Reintentar lectura» + chips de TODOS los botones; prueba de botones sin remapeo (Home llega tal cual, `KeyBus.testing`); el brillo sin permiso ya no desaparece (mosaico apagado que lo explica y lleva a «Modificar ajustes del sistema»). **Pendiente: build de CI y prueba en la tablet.**
- [x] **Respaldo COMPLETO** (`Backup.kt`, 11-10-2026): exportar/importar TODO en un archivo — perfiles + la sesión que se está usando (como perfil) + los ajustes de la app (gestos generales y por app, apps con modo automático, tema, idioma, dev tools…). Los ajustes se guardan tal cual (nombre de archivo → clave → [tipo, valor]), así el respaldo sirve entre versiones; se importan los respaldos viejos (v1, solo perfiles). Al importar: escribe los ajustes, suma/reemplaza perfiles y reinicia la app. **Necesario porque cada build del CI firma con clave nueva → para instalar hay que desinstalar y se pierden los datos de la app.**
- [x] `versionCode` 6 / `versionName` 1.3.0 (11-10-2026) para distinguir este build del F5 (1.2.0) instalado.
- [x] 1.3.3 y 1.3.4 **instaladas encima** en la Y700 con la firma propia (ya NO hay que desinstalar nada) — la **1.3.3 la probó Roberto** (importar deja 1 perfil, «Aplicar»/«Guardar como perfil» activos, sobrevive al cierre completo de la app). La **1.3.4** (badges fuera, «Dibujo del control», «Blanco», tres posiciones, vibración que se guarda) queda **pendiente de probar en la tablet**.
- [ ] F6: stick→mouse con curva; botón→tecla/botón de mouse vía Shizuku; abrir app.
- [ ] F7 (pendiente): Shizuku inalámbrico (brillo sin cable), acción «input keyevent» (teclado), **abrir una app asignada** (selector de app por gesto) y **abrir el cajón de apps**.

## 2026-10-11
- [x] **Firma propia** (el CI ya no firma con clave nueva cada vez): keystore del proyecto `g9control-release.jks`, huella SHA-256 `a9dea6410ff5899c9f319f7af231044c7fd248fee526e3fc52b5cccb0383e049` (válido hasta 2056), 4 secretos en GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) y respaldo en `OneDrive\Documentos\G9Control-firma\` (con `credenciales.txt`). Verificado en el APK del run 38118486827. **Desde ahora `adb install -r` instala encima y conserva los datos** (comprobado: `firstInstallTime` 00:42:25 intacto al subir a 1.3.2).
- [x] Instalado en la Y700: **1.3.1** (versionCode 7, run 38118486827). Hubo que desinstalar el F5 (firma distinta) y **se perdieron sus datos**: `pm uninstall -k` NO permite instalar encima con otra firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) y sin root ni build debuggable no se pueden leer los prefs (`run-as: package not debuggable`).
- [x] **Respaldo probado por Roberto**: `G9Control-respaldo-2026-10-11-0058.json` (2607 B, versión 2) con 1 perfil + ajustes de la app + la sesión viva (traseros X/Y/A/B, zonas muertas, vibración); exportó, modificó e importó.
- [x] **Medido con `getevent`** (`/dev/input/event8`, mando en modo Android): los 4 traseros **mandan la tecla que copian** — L4/R4/L5/R5 → `BTN_NORTH/WEST/BTN_GAMEPAD/BTN_EAST` (Y/X/A/B), **idénticos** a los botones de fábrica. Un trasero es **indistinguible** de su botón: por eso no se puede asignar apretando el trasero, pero sí «Aprender del mando» (el botón físico que quieres copiar).
- [x] **UI del debug 10-10, 2ª pasada** (commit `23b9ae8`, run 38120555121 verde, **versionCode 8 / 1.3.2**, instalada encima): Traseros con «Aprender del mando»; Tablet→Inicio con «Accesos rápidos», «Controles conectados» (nombre real que reporta Android + VID/PID) y «Atajos del mando»; SetupDialog con **un solo scroll** (aviso + pasos juntos) y el aviso del candado «Permitir ajustes restringidos» siempre visible; Gestos **sin preselección**, **sliders de tiempos** (tocar/mantener/doble) y **Home usable con gesto** (`PAD_KEYCODE` incluye Home y `legionKey` lo deja pasar si tiene regla). **Pendiente: probar en la tablet.**
- [x] **adb por red en la Y700**: `adb tcpip 5555` + `192.168.68.102:5555`. Necesario porque el G9 solo funciona con el puerto inferior libre (**con el cable al PC, el G9 queda muerto**). Usar el adb de SuperDisplay (`C:\Program Files\SuperDisplay\adb\adb.exe`, 1.0.40): el de scrcpy es 41 y los dos servidores se matan entre sí.

## 2026-10-11 (2ª parte — bugs que reportó Roberto en la 1.3.2)
Diagnóstico contra el código (la tablet no estaba en red: `adb devices` vacío) + documentación oficial de Android.
- [x] **Pestaña «Perfiles» → «Respaldo»** (`Ui.kt`, `NavItem(Page.PROFILES, …)`): en inglés queda «Backup» (lo que Roberto pidió).
- [x] **Importar: solo entra lo que trae el archivo.** Ya no se agrega la sesión que iba dentro del respaldo (clave `current`, que se sigue guardando solo como referencia) → antes, un respaldo con 1 perfil dejaba 2 (perfil + «Sesión del …»). `Backup.restore`.
- [x] **Bug grave: al importar no se podía aplicar nada ni guardar ningún cambio.** Causa: `importBackup` hace `recreate()` y, al recrearse la app, `autoConnect` sale antes de leer porque el puerto USB sigue abierto (`MainActivity.kt:151`) → `configKnown` queda falso (`MainActivity.kt:1045`) → «Guardar como perfil» y «Aplicar» apagados (`PageProfiles.kt:26,31,59`) y el auto-guardado no escribe **sin ningún aviso** (`MainActivity.kt:103`). Solo se curaba reiniciando el proceso. Arreglo: `onResume` → si el puerto ya está abierto y no hay configuración leída, `readAll()`.
- [x] **Bug: al volver de X-Input (Artemis) el mando quedaba «sin conexión» para siempre.** Volver de X-Input a Android no se puede por software (firmware: `ModeSwitcher.kt:9-12`), y al reconectarse Android le quita el permiso USB (docs: «grants the caller permission … only until the device is disconnected»); la app solo lo volvía a pedir si el `deviceId` era nuevo (`MainActivity.kt:154-157`, `askedDeviceId` se reiniciaba solo al reiniciar el proceso) → nunca lo pedía: había que forzar la detención. Arreglo: al irse el mando se olvida a quién se le pidió el permiso y, al volver en modo Android, se pide otra vez (1,5 s después, una sola vez) + **franja visible con botón «Reconectar»** en la sección G9 (`Ui.kt`, `ReconnectBar`) y en la página Respaldo.
- [x] **1.3.3 (`versionCode` 9) compilada LOCALMENTE e instalada encima** (sin hoyos de CI): Roberto probó en la tablet — importar el respaldo deja **1 perfil**, «Aplicar» y «Guardar como perfil» quedan activos y, al cerrar la app por completo y reabrirla, todo sigue bien. Regla Artemis/X-Input pendiente de volver a probar.

## 2026-10-11 (3ª parte — 1.3.4 local, los comentarios de UI del 10-10 que faltaban)
- [x] **Compilar en la PC** (el CI ya no hace falta para iterar): JDK 17 en `%LOCALAPPDATA%\Android\jdk-17`, SDK en `%LOCALAPPDATA%\Android\Sdk` (platform-tools + android-35 + build-tools 35.0.0), `JAVA_HOME` fijo en el usuario, caché de Gradle en `~/.gradle`. `./gradlew :app:assembleRelease` = 3m15s la 1ª vez, ~1m40s después; el APK sale firmado con la clave del proyecto (`a9dea6…3e049`) → `adb install -r` encima **conservando datos**.
- [x] **1.3.4 (`versionCode` 10, APK local `build-out/app-release-1.3.4-local.apk`) instalada encima** en la Y700 (`firstInstallTime` 00:42:25 intacto, accesibilidad sigue activa):
  1. **Ronda del 10-10 (21:38) que se había perdido** — sus 4 comentarios nunca llegaron a la nota de esa noche: (a) Ajustes: la tarjeta **«Control G9» → «Dibujo del control»** (la sub-leyenda repetida pasó a «Posición del dibujo»); (b) estilo del dibujo: **«Simple» → «Blanco»** (`ControllerSkin.SIMPLE`, solo etiqueta — el valor guardado no cambia); (c) **tercera posición del dibujo: «A la derecha»** (antes sí/no izquierda/centro → ahora `DrawPos` LEFT/CENTER/RIGHT con migración de la preferencia vieja); vale para todas las páginas con dibujo (`DrawingPane` / `LocalDrawPos`).
  2. **Contaminación visual** (lo repitió hoy): fuera los badges **LT/RT/LB/RB de la barra superior**; la explicación queda solo en la barra de abajo. Más sitio para las pestañas.
  3. **Al importar, la app ya no salta a Tablet→Inicio**: se recuerda la pestaña y se vuelve a **«Respaldo»** con el aviso de restauración (`Prefs.restoreTo`/`takeRestoreTo`, se usa una vez y se valida contra `Section.pages`).
  4. **Vibración que no se guardaba al cerrar la app** (10-10, seguía abierto): dos causas — el guardado automático exigía que la lectura hubiera salido bien (`appliedVibration != null`, que tras un reinicio queda en `null`) y, si la app se iba antes de la demora de 500 ms, el cambio se perdía. Ahora **lo que habilita escribir es el control conectado** (luces, sticks y vibración: `SaveStatus` y `watch` ya no dependen de la lectura) y **al pasar a segundo plano se escribe ya** lo pendiente (`flushSaves()` en `onPause`).
- [ ] **Probar la 1.3.4 en la tablet** (los 5 puntos de arriba).
- [ ] **Pregunta abierta**: el panel de juego (mantener Home) — ¿asignable a cualquier botón? Hoy es el botón guía o L3+R3 y las acciones de Gestos no incluyen «panel de juego».
- [ ] F6 (stick→mouse, botones/rueda de mouse) y F7 (Shizuku inalámbrico, «input keyevent», abrir una app, cajón de apps): sin empezar.

## Ronda 4 (pedidos del 11-10 por la tarde, para la próxima sesión)
- [ ] **Panel de juego asignable a cualquier botón** (hoy: botón guía o L3+R3) → hace falta una acción nueva en el motor de gestos.
- [ ] **«Aprender del mando» también en Gestos** (elegir el botón apretándolo en el mando, como en Traseros: `s.learnBack` + `dispatchKeyEvent`).
- [ ] **Indicador de mapeo por app**: en el selector de apps de Gestos, además del «●» actual, listar **qué botones** tienen reglas en esa app.
- [ ] **Diseño acordado para los traseros (11-10)**: darle a cada trasero una **FIRMA** = macro cortísima y única (p. ej. Vista→Menú en <60 ms; el firmware admite 11 pasos con tiempos de 5 ms y modo «completa al tocar»). El servicio **reconoce la firma y se la come** → sabe qué trasero se apretó y decide **por app** qué hacer. La firma va escrita en el mando (global); **el por-app vive en la app** → «traseros por aplicación» dentro de las reglas del firmware. Las teclas de teclado NO las envía el mando (`BackKeys.kt:22`): hay que inyectarlas con Shizuku. **Orden: (1) `input keyevent` con Shizuku, (2) detector de firmas en el servicio, (3) UI fusionada.**
- [ ] **FUSIÓN Traseros + Gestos** (rediseño de una página con selector «Aplica a»): el trasero→botón es **global del mando** (firmware, `setBackKey`) y «Aprender del mando» solo en «Todas las apps»; lo que se guarda **por app** es lo que ese botón HACE (motor de gestos por paquete).
- [x] **Verificado**: el interruptor que Roberto recordaba es «Juego» → «Panel de juego» → **«Botón guía»** (`AutoRules.legionKeys`). **No hace falta apagarlo** para darle un gesto a Home: `AutoMode.kt:907` deja pasar el evento al motor de gestos antes de mirar el switch. Falta confirmarlo en la tablet.
- Spec completa: nota `2026-10-11-ronda4-traseros-gestos.md` del vault.

## 2026-10-11 (4ª parte — Shizuku y el permiso de pantalla en la tablet)
- [x] **Permiso de pantalla DADO** a la app: `adb shell pm grant io.github.sudodiego.g9control android.permission.WRITE_SECURE_SETTINGS` → verificado `granted=true`. Habilita la **pantalla 16:9**, el **slider de brillo** del panel de juego y la parte del **bypass inteligente**. **Este permiso SÍ sobrevive a los reinicios** (a diferencia de Shizuku). La app lo muestra «Activo» tras redibujar la página (lo lee al componer: cambiar de pestaña y volver, o reabrir la app).
- [x] **Shizuku corriendo otra vez** (`shizuku_server`, uid shell; 3 apps autorizadas). Ojo: en Android 11+ **sin root no arranca sola y hay que repetir el arranque tras CADA reinicio**, y el método desde la computadora que documenta Shizuku vale solo para **Android 10 o menor** (por eso no existe el `start.sh` aquí): se arranca desde la app con **«Iniciar por depuración inalámbrica»** (el emparejamiento es una sola vez).
- [x] **Permiso de Shizuku para nuestra app: DADO** por Roberto en el momento (la fila «Shizuku» de Ajustes → Permisos decía **«Activo»**; era la otra fila, «Permiso de pantalla», la que faltaba y ya se dio arriba). Al empezar la próxima sesión: comprobar que las dos filas dicen «Activo».

## 2026-10-11 (5ª parte — Ronda 4, punto 1: teclas de teclado con Shizuku → 1.3.5)
- [x] **Acción nueva `GestureAction.KEY` («Tecla del teclado»)**: inyecta una tecla con `input keyevent` por Shizuku (el mando NO envía teclas de teclado: `BackKeys.kt:22`). En `Shell.kt`: `keyEvent(code)` + patrón `KEY_EVENT` en `allowed()`.
- [x] **La tecla vive en la regla** (`GestureRule.kc`), una sola por botón para los tres gestos. El formato guardado pasa de 4 a 5 campos (`key|tap|long|dbl|kc`) y el parseo **tolera los respaldos viejos de 4** (kc = 0), así que un respaldo anterior se importa igual.
- [x] **Motor de gestos**: `fire` ahora recibe `(GestureAction, kc)`; `runGestureAction(a, kc)` manda la tecla en coroutine y avisa con `panel.message(...)` si Shizuku no está listo.
- [x] **UI de «Gestos»**: selector de tecla (grupos Atajos / Flechas / Letras / Números / F1–F12) que aparece solo cuando algún gesto es «Tecla del teclado», más el aviso de que necesita Shizuku iniciado.
- [x] **1.3.5 (versionCode 11) compilada en local**: `build-out/app-release-1.3.5-local.apk`, `BUILD SUCCESSFUL in 43s`, firmada con la clave del proyecto (huella `A9:DE:A6:…:E0:49` verificada). Strings nuevos presentes en el `classes.dex` del APK.
- [ ] **Instalar en la Y700 y probar** (pendiente del GO).
- [ ] Ronda 4 puntos 2 y 3 (detector de firmas, UI fusionada Traseros+Gestos) — sin empezar.

## 2026-10-11 (6ª parte — «Aprender del mando» en Gestos → 1.3.6)
- [x] **1.3.5 INSTALADA y PROBADA por Roberto** en la Y700 (`firstInstallTime` 00:42:25 intacto): la tecla de teclado funciona («funcionó muy bien»), probada apretando el botón con gesto dentro de un campo de texto.
- [x] **«Aprender del mando» también en Gestos** (punto 3 de la ronda): botón en la página Gestos; el próximo botón que aprietes en el control queda elegido como «botón con gestos» (`AppState.learnGesture` + `learnedPad`), resuelto en `MainActivity.dispatchKeyEvent` con `padKeyOf` y **consumiendo** el evento. Se cancela al cambiar de página y en `onPause`.
- [x] **Mientras se aprende, el servicio no remapea** (`syncBus()` → `KeyBus.testing`), porque un botón con reglas se consumiría antes de llegar a la app (mismo puente que usa la prueba de botones).
- [x] **1.3.6 (versionCode 12)** compilada en local (`build-out/app-release-1.3.6-local.apk`, `BUILD SUCCESSFUL in 31s`), firmada con la clave del proyecto (huella `A9:DE:A6:…:E0:49`); strings nuevos verificados en el `classes.dex`.
- [ ] **Probar en la tablet** el botón «Aprender del mando» de Gestos.

## 2026-10-11 (7ª parte — indicador por app, panel de juego como gesto, sliders plegables → 1.3.7)
- [x] **«Aprender del mando» en Gestos: PROBADO** por Roberto.
- [x] **Indicador de lo que tiene cada app (punto 4 de la ronda)**: `gestureSummary()` resume botones y qué hace cada uno («LT → Atrás, tecla A · Home → Panel de juego»). Se enseña **en el selector de apps** (Todas las apps + cada una con «●») y en un bloque **«Lo que tiene esta app»** dentro de la página, sin abrir botón por botón.
- [x] **«Panel de juego» como acción de gesto** (punto 1 de la ronda): `GestureAction.PANEL` → `panel.show()`. Ya se puede asignar a cualquier botón, no solo al guía o L3+R3.
- [x] **Sliders de tiempos plegables** (pedido del 11-10 tarde): los tres sliders ocupaban mucho; ahora hay **3 botones en una línea** (Tocar / Mantener / Doble, cada uno con su valor) y al tocar uno aparece **solo su slider debajo**.
- [x] **1.3.7 (versionCode 13)** compilada en local e **instalada encima** en la Y700 (datos intactos, accesibilidad y Shizuku siguen).
- [ ] **Falta de la ronda**: fusión Traseros + Gestos en una página con «Aplica a», y el **detector de firmas** de los traseros.

## 2026-10-11 (8ª parte — FIRMAS de los traseros + traseros por app → 1.3.8)
- [x] **`Signatures.kt` (nuevo)**: cada trasero lleva una **firma** = macro cortísima única (Vista/Menú, 10 ms por paso y 5 ms entre pasos → ~25 ms), imposible de repetir a mano. Ninguna firma es el principio de otra (L4 = Vista→Menú, R4 = Menú→Vista, L5 = Vista→Vista→Menú, R5 = Menú→Menú→Vista).
- [x] **Escritura**: `MainActivity.writeSignatures()` graba las 4 firmas en el mando (ajustes PRIMERO, luego los pasos, y relee para confirmar). Botón **«Preparar firmas en el mando»** en la página de gestos.
- [x] **Detector** (`SignatureDetector`): enganchado en `handleKey` tras `legionKey`. Mientras dura la ventana (80 ms) retiene Vista/Menú; si la secuencia se completa dispara el trasero y **se come** los botones; si no, los reenvía con Shizuku (`input keyevent`) para que un Vista/Menú normal no se pierda. Solo vigila si hay reglas de trasero configuradas (en general o en la app al frente).
- [x] **Traseros POR APP**: cada trasero tiene un código interno (`PADDLE_KEYCODE`, negativo) y entra en el mismo motor de gestos, así que se le asigna qué hace **en cada app** con el «Aplica a». `runPaddle()` resuelve la regla de la app al frente.
- [x] **Fusión (1ª parte)**: los traseros ya se configuran **dentro de la página de gestos** (chips L4/R4/L5/R5 + «Preparar firmas»); el resumen por app los incluye («L4 → Panel de juego»). La página «Traseros» sigue para lo que es del mando (qué botón copia cada uno y su macro).
- [x] **1.3.8 (versionCode 14)** compilada e **instalada encima** en la Y700 (datos intactos, accesibilidad y Shizuku siguen).
- [ ] **Probar en la tablet**: grabar firmas y asignarle a un trasero una acción distinta en una app.

## 2026-10-11 (9ª parte — RONDA 5: los 4 puntos del registro → 1.3.9)
Los 4 puntos de `2026-10-11-sesion-registro-modificaciones.md`, en una sola pasada. Commit **`855751c`**, **1.3.9 (versionCode 15)**, compilada en local, instalada encima y **verificada en pantalla** (capturas + `uiautomator`).

### Punto 1 — Traseros: huecos + «Borrar todos»
- [x] Fuera los `FlowRow`: cada fila **llena el ancho** con chips de igual peso (LT LB RB RT / A B X Y / L3 R3 Vista Menú / Home + cruceta / Sin asignar + Macro + sticks izq./der.). `TargetChip` y `StickDirChip` aceptan `modifier`; los sticks se estiran con `fillMaxWidth()`.
- [x] Botón **«Borrar todos»** (`MainActivity.clearAllBackKeys()`): recorre los 4 traseros, escribe `MacroGcm.clear` si tenían macro + `Gcm.clearBackKey` y **relee** cada uno para confirmar; actualiza `appliedBackKeys` y avisa en el estado.

### Punto 2 — El panel se llama «Ajustes rápidos» en TODA la app
- [x] `GamePanel.header()` ya no pone el nombre de la app al frente: pone **«Ajustes rápidos»** (y `sub = false`).
- [x] Pestaña `Ui.kt`: «Juego» → **«Ajustes rápidos»**.
- [x] `PageDiagnostics` y `PageTabletHome`: «Panel de juego» → «Ajustes rápidos» (y «Mantener: panel de juego» → «Mantener: ajustes rápidos»).
- [x] El mosaico que abre los ajustes de Android: **«Ajustes rápidos de Android»** (subtítulo «Android»).

### Punto 3 — Página Gestos
- [x] Bloque de detalle: **«Botones mapeados: "app"»** (el nombre del alcance elegido entre comillas).
- [x] Selector «Aplica a» **solo con el puntito ●** y el nombre (los resúmenes largos estorbaban).
- [x] **BUG de los traseros**: `runPaddle()` solo ejecutaba `rule.tap`; ahora llama a `GestureEngine.onPress(...)` con la pulsación ya resuelta → **«Un toque» y «Doble toque» funcionan** en los traseros.
- [x] **«Mantener» en un trasero no se puede** (el mando manda una firma de ~25 ms; no hay forma de distinguir cuánto lo mantienes): la página lo avisa con un texto cuando el botón elegido es un trasero.
- [x] «Un toque» / «Doble toque» / «Mantener», en ese orden, tanto en los tres gestos como en los botones de tiempos (y el slider que se despliega es el que corresponde).
- [x] Los 3 textos (i) seguidos → **un solo (i) desplegable «Cómo funciona»**.
- [x] Acciones nuevas: **«Abrir una app»** (`GestureAction.OPEN_APP` + `GestureRule.pkg`, selector de app; se abre con `Shell.amStart` = Shizuku `am start` — sin Shizuku intenta `ACTION_MAIN` normal), **«Abrir el cajón de apps»** (`APP_DRAWER`, deslizamiento hacia arriba) y **«Página siguiente / anterior»** (`PAGE_NEXT` / `PAGE_PREV`, deslizamiento horizontal). Investigué: **no existe acción de accesibilidad** para esto; el deslizamiento se hace con `dispatchGesture` y por eso funciona en el launcher.
- [x] `GestureAction.PANEL` se muestra como **«Ajustes rápidos»** en los resúmenes.

### Punto 4 — Copy del botón de firmas
- [x] «Preparar firmas en el mando» → **«Grabar los traseros en el mando»**; nota nueva en llano: «Toca ese botón una sola vez: se graba en el mando qué botón es cada trasero. Después, cada trasero puede hacer una cosa distinta en cada app.»

### Verificación
- [x] `./gradlew :app:assembleRelease --offline` → **BUILD SUCCESSFUL** (antes, un error de compilación: un `invoke` del motor sin el paquete; corregido).
- [x] `adb install -r` → **Success**; `versionCode=15`, `versionName=1.3.9`, `firstInstallTime=2026-10-11 00:42:25` **intacto**.
- [x] App lanzada por adb: **sin crash**; accesibilidad y `shizuku_server` activos.
- [x] En pantalla: pestaña «Ajustes rápidos», Traseros con filas llenas + «Borrar todos», «Botones mapeados: "Todas las apps"», resumen con «Ajustes rápidos», tiempos 200/330/500 ms en el orden nuevo.
- [ ] **Probar en la tablet con el mando conectado** (hoy salía «Sin conexión»): traseros (Un toque / Doble toque), «Borrar todos», «Abrir una app» (con y sin Shizuku), cajón y páginas del launcher.
- [ ] **Fusión visual** pendiente de la ronda pasada: una sola página «Botones». 
