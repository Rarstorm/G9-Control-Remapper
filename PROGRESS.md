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

## 2026-10-11 (10ª parte — BUG de L4 en los traseros: diagnóstico y arreglo → 1.3.11)
Roberto reportó que **L4 no hacía nada** con sus gestos de «Gestos» (en 1.3.7/1.3.8 el toque sí funcionaba). Esta vez se midió en vez de suponer.

### Diagnóstico (pruebas, no teoría)
- `logcat`: el canal de la app funciona (tag `G9Auto`); al apretar **A** llega `DIAG key k=96` (KEYCODE_BUTTON_A) y el kernel lo registra en `/dev/input/event8`.
- Al apretar **L4**: **no llegaba NADA** — ni al servicio ni al kernel (`getevent` en el mando: 0 eventos en toda la ventana). La app no tenía nada que hacer: el mando no mandaba nada.
- **Causa raíz**: al mando le faltaban las «marcas» (macros-firma) de los traseros. Se pierden al **reasignar un trasero** o con **«Borrar todos»** (`Gcm.clearBackKey` = restaurar). En 1.3.9 ese borrado existía y **no avisaba de nada**: todo se veía «bien» y el trasero estaba vacío.
- **Arreglo inmediato**: volver a grabar las marcas → L4 volvió a funcionar. Confirmado en el kernel: **16 pares `BTN_SELECT → BTN_START`** encadenados (~18-20 ms cada uno), uno por pulsación; y en el log: `DIAG sig FIN L4 [109, 108]` + `firma de L4 … (regla=GestureRule(key=-11, tap=KEY, long=NOTIFICATIONS, dbl=PANEL, kc=31))`.

### Arreglo de fondo
- [x] **`preparePaddles()` (automático)**: si un trasero tiene gestos y en el mando le falta su marca, la app se la escribe **sola** (macro + `Gcm.setBackKey(id, 0xFF)` si el mando no lo pone solo) y **confirma releyendo** (mapping == 0xFF). Se dispara al **guardar un gesto de un trasero** en «Gestos». Nada de botones técnicos.
- [x] **`writeSignatures()` = `preparePaddles(loud = true)`**: el botón manual (ahora **«Preparar los traseros»**) comprueba los cuatro y avisa si alguno no quedó.
- [x] **Avisos en llano**: en «Gestos» y en «Traseros» la app avisa si un trasero no está preparado («al apretarlos no manda nada») y «Borrar todos» avisa de que desprepara los traseros.
- [x] **«Mantener» ya no se ofrece en los traseros**: el mando manda un pulso de ~19 ms, así que **es imposible** distinguir si lo mantienes (probado: `BTN_SELECT DOWN/UP` y `BTN_START DOWN/UP` juntos). Se ocultó la fila y se explica en una línea; si Roberto quiere Notificaciones en L4, va en «Doble toque».
- [x] **Copy sin jerga**: fuera «firmas», «grabar», «0xFF»; el único botón dice «Preparar los traseros» y su nota explica en una línea cuándo hace falta.
- [x] Verificado en pantalla: en «Traseros» los cuatro aparecen como **«L4/R4/L5/R5 Macro»** (el mando los tiene), y no queda la palabra «firma» en la interfaz.
- [ ] **Pendiente**: que Roberto confirme la 1.3.11-diag (asignar en «Gestos» → se prepara sola) y después **quitar los logs `DIAG`**, subir la versión limpia y cerrar el registro.

## 2026-10-11 (11ª parte — L5/R5 y el «Files»: dos causas reales → 1.3.15-diag)
### El mando FUSIONA pasos seguidos del mismo botón (dato nuevo del firmware)
Las firmas de L5/R5 eran `Vista,Vista,Menú` y `Menú,Menú,Vista`. Al apretar L5 llegaban a Android **solo dos botones**
(`109,108` = la firma de L4): el firmware mantiene el botón apretado entre pasos iguales y lo entrega como **un solo
botón largo**. Por eso L5/R5 nunca se reconocían: la app leía la firma de L4/R4. Comprobado con las pulsaciones de
Roberto (log `G9Auto`) y con las macros grabadas leídas por USB (`53 FE 06 01..04`): el mando **sí** tenía los 4 pasos
distintos guardados, el problema era cómo los emite.
- **Firmas nuevas (1.3.15)**: L4 `Vista,Menú` · R4 `Menú,Vista` · L5 `Vista,Menú,Vista` · R5 `Menú,Vista,Menú`
  (sin dos pasos iguales seguidos). L5/R5 son el principio de L4/R4, así que el detector **espera 60 ms**
  ([`EXTEND_MS`]) por si viene el botón que falta antes de disparar la corta.
- **Reescritura automática**: `preparePaddles` ya no se fía de que el trasero apunte a su macro: compara **los pasos**
  y reescribe si no son los de ahora; y se llama solo al leer el mando si algún trasero tiene gestos.
### Vista/Menú: el reenvío del detector abría «Files» y se comía los gestos
`SignatureDetector` retenía Vista/Menú 80 ms y, al no ser firma, los **reenviaba** con `input keyevent`: en esta ROM
el reenvío de Menú abría la app **Files** (buscador) y, como el servicio no ve las teclas inyectadas, los gestos de
Menú de esa app nunca se disparaban. Ahora, si esa app usa Vista/Menú como botones de gestos, el botón se le pasa al
motor de gestos ([`lostSignatureKey`]); solo se reenvía si la app no los usa.
### Otros arreglos de la misma ronda
- **Regresión propia**: bajé la ventana del detector a 45 ms y **L4 dejó de responder** (la firma tarda ~20 ms pero el
  sistema puede tardar más). Ventana a 90 ms.
- **Guardado silencioso**: si el control no está conectado, el guardado automático se saltaba el cambio **sin decir
  nada**. Ahora avisa («El control no está conectado: el cambio no se guardó.»).
- **Aviso de guardado global** (idea de Roberto, del de Luces): mismo aviso en **todas** las pestañas, con cuenta
  atrás + barra mientras está en cola, spinner al escribir y **✓ verde** al terminar; se enciende con cualquier
  escritura real (`Gcm.isWrite`, gancho en `G9Usb.onWrite`).
- **«Botones mapeados» agrupado** por gesto: «Un toque:», «Doble toque:», «Mantener:» con `botón → acción`.
- **Lección de proceso**: un `ZipFile` abierto en el kernel de Python bloqueaba `app-release.apk` y hacía fallar
  `packageRelease` («Unable to delete directory»). Cerrar el handle antes de compilar.



## 2026-10-11 (12ª parte — RONDA 6: intercepción estilo Key Mapper, secuencias por app, ADB inalámbrico → 1.3.19-diag)
Roberto volvió de probar la **1.3.15-diag** con tres quejas; salió a probar la **1.3.19-diag**.

**1) Guardado y marcas (1.3.11 → 1.3.16-diag).**
- El mando **había perdido las marcas de los traseros** (getevent: 0 pares Vista/Menú antes de la prueba, 16 después): los gestos estaban guardados, lo que faltaba era que el mando dijera cuál trasero se apretó. La app ahora **verifica y rehace las marcas sola** al leer el mando (`preparePaddles(loud = false)` si algún trasero tiene gestos) → ya no hay que remapear.
- Sin control conectado el guardado **se saltaba en silencio**; ahora lo dice (`state.fail`). El aviso de guardado es global (cuenta atrás + barra), **verde solo 3 s**, y distingue **«Guardado en el control»** (mando) de **«Guardado en la app»** (gestos/perfiles).
- «Botones mapeados» agrupado en **Un toque / Doble toque / Mantener**.

**2) Intercepción estilo Key Mapper (1.3.18-diag) — lo que Roberto exigió, y tenía razón.**
- Antes se interceptaba **el botón entero**; ahora: **con «Un toque» = Nada el botón NO se intercepta** (bypass directo, sin retardo ni reinyección) y la app solo **escucha**: captura la **segunda** pulsación de un doble toque (`GestureEngine.listen()`) y, en un mantener, mide la duración y dispara al soltar. Con «Un toque» asignado sí se captura (ese toque *es* la acción).
- **Lo imposible sin root:** fabricar un mando virtual como hace KeyMapper → un doble toque deja pasar su primera pulsación al juego. Todo lo demás se puede.
- **Salida por app** (`AutoMode`): si esa app tiene reglas para Vista/Menú/Home, esos botones son suyos y pasan tal cual — era lo que rompía **EDEN** (los gamepads ignoran teclas inyectadas).
- **Reenvío con origen de mando:** `Shell.keyEventPad` = `input gamepad keyevent <n>` (los juegos sí lo atienden), con caída automática a `input keyevent`; patrón nuevo en la lista blanca de Shizuku.

**3) «Secuencia de teclas» — la macro por app (1.3.19-diag).**
- `GestureAction.COMBO` + `GestureRule.combo` (**7º campo** de la regla, `key|tap|long|dbl|kc|pkg|combo`, leyendo respaldos viejos) + `gestureActionName` con el resumen de la secuencia.
- Editor de pasos en «Gestos»: se añade/quita cada paso; teclas del **mando** (A, B, X, Y, LB, RB, LT, RT, L3, R3, Vista, Menú) y de **sistema** (Atrás, Inicio, Recientes), además de flechas/letras/números/F1–F12.
- En ejecución se manda tecla a tecla con ~70 ms de pausa y **origen de mando**. Ejemplo de Roberto resuelto: **R5 con «Aplica a: GameHub» = Atrás, Atrás, Arriba, Abajo, A**; al salir de GameHub vuelve lo general (por app, como el resto de gestos).

**4) «Página anterior» y los desplegables (1.3.19-diag).**
- La acción **sí existía** (deslizamiento al revés): el fallo real era que la **lista de acciones se cortaba** y las últimas no se veían → todos los desplegables con `Modifier.heightIn(max = 420.dp)` (acciones, teclas y apps).

**5) Verificación (herramientas, no memoria).**
- `assembleRelease` **BUILD SUCCESSFUL** (1.3.18 y 1.3.19); instalada **por adb con el cable** (`HA2HA6AQ`) → `Success`, **`firstInstallTime` 2026-10-11 00:42:25 intacto** (datos conservados) y **sin crash** al abrir.
- **ADB inalámbrico re-validado:** lo activé desde el cable (`adb tcpip 5555`) y comprobé que responde sin cable (`192.168.68.106:5555`); el PC también la ve por USB. Se pierde al reiniciar la tablet.
- **El G9 no estaba en la tablet** (`dumpsys input`: ningún gamepad; la app con cero tráfico USB) → **la prueba de los traseros/botones con el mando queda en manos de Roberto**.

**Pendiente:** (1) prueba de Roberto con la 1.3.19-diag (traseros sin remapear, bypass con «Un toque: Nada» en EDEN, secuencia R5 en GameHub, páginas del launcher, «Abrir una app» con y sin Shizuku); (2) si aprueba → **F6 del plan: mouse y sus botones** (stick → mouse con curva; botón → tecla/botón de mouse); (3) arrastra la **fusión visual** («Gestos» + «Traseros» → página «Botones»), los 5 puntos de UI de la 1.3.4 y el gesto en **Home** con «Botón guía» encendido.

## 2026-10-12 (13ª parte — RONDA 7: los 9 puntos de Roberto → 1.3.20-diag)

**1) El toque que se confundía con el nativo (Brave, Cocoon, NextPlayer… no solo Brave).**
- Causa: al entrar a una app, `lastPackage` podía ir un paso atrás (el evento del mando llega antes del cambio de ventana) y el botón no encontraba su regla para esa app → pasaba tal cual (en Brave, «Y» abría su menú).
- Arreglo: si el botón no tiene regla para el paquete que creemos al frente, se le pregunta a la ventana activa (`rootInActiveWindow.packageName`) y se corrige `lastPackage` — solo cuando hace falta, no en cada evento.
- Además `GestureEngine.reset()` al cambiar de app: lo que quedaba a medias (un doble toque esperando su segundo, un mantener midiéndose) ya no se arrastra a la app nueva. Eso era «se come el primer toque al recién entrar».

**2) Una tecla de teclado por gesto.** `GestureRule` pasa de UN `kc` a **`kcTap` / `kcLong` / `kcDbl`** y el formato de la regla es de **9 campos**: `key|tap|long|dbl|kcTap|kcLong|kcDbl|pkg|combo` (los respaldos de 4–7 campos se siguen leyendo: su tecla única va al gesto que era «Tecla del teclado»). En «Gestos» el selector de tecla sale **debajo del gesto** que sea «Tecla del teclado».

**3) Lista de acciones por categorías.** `GESTURE_CATS` (Nada, Sistema, Media, Pantalla, Apps, Launcher, Entradas) + `GestureAction.cat`; el desplegable de cada gesto va en **dos pasos**: categoría → acción, con «‹ volver». Media nueva: siguiente/anterior pista, adelantar/retroceder, detener, silenciar (además de reproducir/pausa y volumen).

**4) Bumpers y gatillos tocables.** Chips **LB / RB / LT / RT** en «Gestos», junto a los traseros: en el dibujo no se pueden tocar a propósito (quedan en el borde), así que ya no hay que pasar por «Aprender del mando».

**5) Cajón de apps desde cualquier app.** `openAppDrawer()` prueba tres caminos en orden: (1) el intent público `android.intent.action.ALL_APPS` lanzado **por Shizuku**, (2) el mismo intent abierto a mano, (3) Inicio + deslizamiento. Antes solo deslizaba → funcionaba únicamente en el launcher.

**6) Adelantar / retroceder video.** `seekVideo(±1)`: un deslizamiento horizontal **corto**, del tamaño elegido en «Tiempos» → **«Deslizamiento»** (2–30 %, 8 % por defecto), en lugar de «Página siguiente/anterior» (media pantalla: avanzaba muchísimo).

**7) «Pantalla de inicio» como ámbito.** El launcher se saca del sistema (`resolveActivity` de `CATEGORY_HOME`) y sale arriba en «Aplica a»: sirve para lo que solo pasa ahí (cajón de apps, páginas del launcher).

**8) Subir / Bajar brillo.** Usan `SystemLevels` (el mismo motor interno y el permiso que ya existían; solo faltaba la acción en la lista).

**9) Reconexión sola del mando.** `reconnectController()`: si el control reaparece como dispositivo de entrada y la conexión no está abierta, se reabre **sola** (aprovecha el permiso USB «Usar siempre» ya guardado) tras 800 ms — antes había que abrir la app. Las marcas de los traseros siguen guardadas en el mando y la app las re-verifica al abrirse.

**Verificación.** `:app:assembleRelease` **BUILD SUCCESSFUL** (1.3.20-diag, versionCode 26) → `app/build/outputs/apk/release/app-release.apk`, 2.777.095 bytes, sha256 `cb2ff43fbd5565d425c8121378e3bb20e85eba40abdd40fe8929bcfaae7a10e4`. La tablet se reinició y quedó desconectada: **instalación pendiente de que Roberto la conecte** (y ahí se rehace el ADB inalámbrico).

**Pendiente:** (1) instalar por adb la 1.3.20-diag + ADB inalámbrico; (2) prueba de Roberto de los 9 puntos; (3) sigue en la lista: mouse y sus botones (F6 del plan), fusión visual «Gestos»+«Traseros», gesto en Home con «Botón guía» encendido.

## 2026-10-12 (14ª parte — 1.4.0-diag: emulación de ratón con L3 + R3, al estilo Mantis)

**Cómo funciona (y qué se puede y qué no, según la documentación de Android).**
- Android **no tiene ninguna API de ratón**: la única inyección sin root es `AccessibilityService.dispatchGesture` (toques/deslizamientos; el XML del servicio ya traía `canPerformGestures="true"`). Así que el **puntero lo dibuja la app** (una ventana `TYPE_ACCESSIBILITY_OVERLAY`, la misma del panel, de pantalla completa y sin toques: mover el ratón es solo repintar) y **el clic es un toque inyectado justo ahí**. Por eso funciona en cualquier sitio: launcher, cajón de apps o dentro de una app.
- Los ejes del stick se leen por `setMotionEventSources` (API 34, docs oficiales: admite `SOURCE_JOYSTICK`), lo mismo que ya usaba el panel para navegarse. **Mientras el puntero está encendido, ese stick lo lee la app y el juego no lo ve**; al apagarlo vuelve tal cual.
- Un gesto inyectado se manda entero (no se puede ir moviendo mientras el dedo está apoyado): el **arrastre** se resuelve al soltar (`MOUSE_DRAG`), de donde estaba el puntero al apretar hasta donde esté ahora.

**Lo que se hizo.**
- `Mouse.kt` (nuevo): `MouseRules` (opciones, en las preferencias `g9`) + `MouseMode` (puntero, movimiento y clics). `MouseMode` lee el stick con **zona muerta radial**, **curva/aceleración** configurable y **suavidad** (filtro exponencial); el clic es un toque de 55 ms, el clic derecho un toque largo (menú contextual, lo más cerca del botón derecho sin root), el doble clic dos toques y la rueda un deslizamiento corto (o `input mouse roll` por Shizuku, con caída al deslizamiento).
- **L3 + R3 enciende y apaga el puntero** (`AutoMode.mouseChord` + `toggleMouse`), en cualquier app. La combinación **antes abría el panel / sacaba la captura**: eso queda **deprecado** y fuera de los textos de la UI (`PageTabletHome` deja de anunciarla y el interruptor de `PageDiagnostics` pasa a ser el del ratón). El panel sigue con el botón guía y la captura con guía + RB o M dos veces en DS5.
- Pestaña nueva **«Ratón»** (`PageMouse.kt`): encender/apagar ahora, stick (izquierdo/derecho/el que muevas), **Velocidad (DPI) 200–4000**, **Suavidad 0–90 %**, **Curva 0–100 %**, **Zona muerta 0–40 %**, modo de rueda y cuánto avanza cada paso.
- Categoría **«Ratón»** en «Gestos»: clic (toque), clic derecho (menú), doble clic, arrastrar, rueda arriba, rueda abajo y «Puntero: encender/apagar» — botón por botón y por app (`Aplica a`). Si una acción de ratón se dispara con el puntero apagado, se enciende sola (si no, no habría dónde hacer el clic).
- Mosaico **«Ratón»** en el panel de juego (ajustes rápidos) para encenderlo/apagarlo sin soltar el control.
- Versión **1.4.0-diag (versionCode 27)**; `assembleRelease` **BUILD SUCCESSFUL**.

**Pendiente al conectar la tableta:** instalar por adb (se saltó la 1.3.20: esta 1.4.0 la incluye) + ADB inalámbrico; y **comprobar el comando de la rueda real** (`adb shell input` imprime los comandos de ese Android): si `input mouse roll` no existe, ya cae sola al deslizamiento y la opción «rueda de verdad» se marca como no disponible.

**Ampliación (mismo día): papel por stick.** Preguntó por tres escenarios reales (Edge, Brave+YouTube, GameHub con point & click) y pidió que el scroll se pueda poner «en cualquier stick, ninguno o ambos» (no para él, para la comunidad). En «Ratón» **cada stick por separado** tiene un papel: **Nada / Puntero / Scroll**. «Scroll» manda pasos de rueda seguidos (≈200 ms al borde de la zona muerta → ≈70 ms a fondo) en la posición del puntero, con el ajuste de rueda de la pestaña. Con los dos en «Puntero» mueve el que muevas (no se suman) y con los dos en «Scroll» los dos desplazan; si ninguno mueve el puntero, la pestaña avisa. Commiteado dentro de la 1.4.0 (`2592a9d`, APK sha256 `134bd624…`, `BUILD SUCCESSFUL`).

## 2026-10-12 (15ª parte — 1.4.2-diag: los pendientes de la lista de Roberto, todos en una versión)

Roberto pidió **una sola versión con todas sus peticiones hechas**, y sobre esa probar. Hecho y compilado: **1.4.2-diag (versionCode 29)**, `assembleRelease` **BUILD SUCCESSFUL**, instalada encima en la Y700 (v29 confirmado con `dumpsys package`). Los registros de diagnóstico (`DIAG …`) que quedaban del depurado **se quitaron todos** (comprobado: no queda ningún `DIAG` temporal en los `.kt`).

**1) Puntero: tamaño, color y forma** (lo que había pedido y faltaba). Panel nuevo **«Aspecto del puntero»** en la pestaña «Ratón»: **Tamaño 10–48 dp** (por defecto 20), **Forma** (Flecha / Punto / Cruz) y **Color** con muestras (blanco, negro, amarillo, verde, cian, rosa, naranja). En los tres estilos **el punto del clic es el punto de la figura** (punta de la flecha, centro del punto, cruce de la cruz), así el clic siempre sale donde ves. El contorno se pone solo del color que **contrasta** con el relleno (calculado por luminancia), para que se vea sobre fondo claro y oscuro sin tener que elegir dos colores. El margen de repintado crece con el tamaño (un puntero grande no deja rastro). `MouseRules` gana `cursorSize`/`cursorColor`/`cursorStyle`; `CursorView` rehace la figura **solo cuando cambian** tamaño o estilo, no en cada cuadro. Verificado en la tableta: la página muestra `Forma → Flecha/Punto/Cruz` y `Color`.

**2) «Traseros» al lado de «Bumpers y gatillos»** (petición con imagen). Estaban **debajo**; ahora van **uno al lado del otro** en la misma fila (dos columnas), y «Botón con gestos» queda debajo de los dos. Verificado leyendo el árbol real de la pantalla en la tableta: `Bumpers y gatillos y=1205 → LB RB LT RT y=1315` y `Traseros y=1205 → L4 R4 L5 R5 y=1315` (misma línea).

**3) El ámbito «Pantalla de inicio» se heredaba en Cocoon — causa encontrada y arreglada.** Roberto lo describió exacto: asignó RT/LT a «Pantalla de inicio», dentro de Cocoon le registraba un doble clic, y al quitarlo se fue. **No era herencia de reglas: era que la app no veía a Cocoon.** El filtro que decide «esta ventana no es una app» (para ignorar superposiciones) preguntaba **primero si el paquete es un teclado**, y la tableta tiene tres teclados activos… y **uno es el propio Cocoon** (`rip.moth.cocoonshell/.ui.keyboard.CocoonInputMethodService`): Cocoon es teclado **y** pantalla de inicio. Por ese orden, **todos los avisos de ventana de Cocoon se descartaban**, la app seguía creyendo que estabas en `com.zui.launcher` y dentro de Cocoon te aplicaba **sus reglas**. Arreglo (`isTransient`): (a) si el aviso trae la ventana, se decide por el **tipo** (`TYPE_APPLICATION` = app; teclado/sistema/superposición = no cuenta); (b) sin ese dato, se pregunta **primero si es una app** (lanzable o pantalla de inicio) y solo después si además es teclado. Medido en la tableta con el registro puesto: antes `transitorio=true` (y ningún aviso de app al frente), ahora `transitorio=false` + `app al frente: rip.moth.cocoonshell (MainActivity), ventana 1376`. De paso: cualquier app que sea teclado y no actividad propia ya no puede “tragarse” el cambio de app.

**4) Cajón de apps: intent no hay, así que se espera de verdad al launcher.** Comprobado en la tableta que **nadie atiende** `android.intent.action.ALL_APPS` (ni ZUI ni Cocoon), así que el camino de MacroDroid **no existe** aquí: queda inicio + deslizamiento. Lo inestable era el **180 ms a ojo**: si el launcher tardaba, el deslizamiento caía en el vacío (te quedabas en el inicio) o se sentía lento. Ahora, al pedir el cajón se **pregunta a la ventana activa** si el launcher está al frente, **cada 60 ms hasta 1 s**, y se desliza **en el momento justo** (`esLauncherAlFrente` + `esperarLauncherYDeslizar`); si el launcher no llega, **no desliza a ciegas** sobre otra app y lo dice en el registro. También el «ya estoy en el launcher» dejó de estar clavado a `com.zui.launcher` y usa las pantallas de inicio reales del sistema.

**5) Rueda/deslizamiento «casi imperceptible».** El deslizamiento con asignación de stick usa el comando corregido (`input mouse scroll <x> <y> --axis VSCROLL`, medido en la tableta: **578 px por paso**; el viejo `input mouse roll` daba **0 px**) y el ritmo de 110 ms con rechazo controlado (no se apilan gestos: Android rechaza el que llega con otro en marcha). Se retiró el `wheelMode`/Shizuku del UI y quedó **«Reversar la dirección»**.

**6) «Arrastrar» ¿es redundante con mantener el clic?** No: el clic se inyecta como un toque de **55 ms** (no se puede dejar apretado mientras mueves). «Arrastrar» guarda dónde estaba el puntero al apretar y manda **un gesto entero** al soltar. Son cosas distintas.

**Pendiente:** solo la prueba de Roberto sobre esta 1.4.2-diag.

## 2026-10-13 (16ª parte — 1.4.3-diag: la ronda de los 5 puntos que Roberto reportó probando la 1.4.2)

Roberto probó la 1.4.2-diag con el mando y trajo 5 puntos (dos imágenes anotadas). Sobre su orden de **no**
hacer el commit («tiene mucho bug todavía»), todo se implementó en **UNA sola versión**: **1.4.3-diag
(versionCode 30)**.

1. **No se reconectaba sola tras dormirse (imagen).** `reconnectController()` se **rendía en silencio**
   cuando faltaba el permiso USB (`if (!G9Usb.hasPermission(this, d)) return`), que es justo lo que Android
   quita al desconectarse. Ahora el **servicio lo pide** (`G9Usb.askPermission`, petición sin esperar
   respuesta) y **reintenta a los 1,5 s**, con un tope de una petición cada 10 s para no repetir el
   diálogo. **A probar con el mando**; el `dumpsys usb` del despertar no se pudo medir (sin ADB).
2. **Chips de «Gestos» partidos (RT y R5 en dos renglones).** Las dos columnas a media pantalla con chips
   «naturales» no dejaban sitio. Ahora cada chip `Modifier.weight(1f)`, separación 4 dp, etiqueta a 12 sp
   **en una sola línea** (`maxLines = 1, softWrap = false`) y 8 dp entre las dos columnas. Opción elegida
   por él: «los 8 chips con el mismo ancho y más chicos, para que quepan en una sola fila».
3. **UP/DOWN «asignados» que él nunca asignó.** No era la pestaña Gestos: la pestaña **Ratón** guardaba sus
   botones como **reglas de gestos generales** (`Gestures.setRule`) y Gestos imprime esas reglas. Ahora los
   botones del ratón tienen **su propio almacén** (`MouseRules.buttons` → preferencia `mouse_buttons`), con
   **migración automática** de lo ya guardado (`migrateFromGestures`), y **no aparecen en «Gestos»**.
4. **Ratón.** (a) **clic derecho** = toque largo, ahora **1 s** (`tap(1000)`); sin root no existe el botón
   derecho real y un mantener abre menú **solo donde la app lo tenga** (dicho tal cual). (b) Con el puntero
   encendido **solo hacen algo los botones del ratón** y **el botón guía tampoco** (su decisión): los demás
   no llegan a la app ni al juego; siguen **L3 + R3** (apagarlo) y el panel ya abierto. (c) **Se quitó el
   auto-encendido**: apretar el botón del clic ya no saca el puntero; encender es L3 + R3 o la pestaña.
   (d) La acción del último renglón pasa a **«Puntero: solo apagar»** (`turnMouseOff`), asignable a
   cualquier botón y activa **solo con el puntero encendido**; L3 + R3 sigue encendiendo y apagando.
5. **Traseros activos en apps sin reglas.** El detector vigilaba «si algún trasero **tiene reglas**» → con
   reglas solo en Brave, en las demás apps se apartaba y los **Vista/Menú de la firma llegaban crudos**. La
   condición ahora es **si el mando trae de verdad la marca** en algún trasero: la app escribe en
   preferencias qué traseros están en su macro (`paddle_sig`, `MainActivity.syncFirmas`) y el servicio lo
   lee (`AutoMode.handleKey`). Fuera de la app con reglas, el trasero se consume y **no hace nada**.

**Verificación.** `JAVA_HOME=… ./gradlew :app:assembleRelease --offline` → **BUILD SUCCESSFUL in 33s**
(1 aviso de deprecación preexistente en `Mouse.kt:382`). APK:
`app/build/outputs/apk/release/app-release.apk`, **2.957.075 bytes**, sha256
`5ae1637b4a55eb375c4c91c0ee90b9d1c18220d2ab5e65de822bcf13cf7659f8`. `aapt2 dump badging` →
`versionCode='30' versionName='1.4.3-diag'`. Los cambios están **en el artefacto** (el dex contiene
`mouse_buttons`, `solo apagar` y `paddle_sig`).

**Pendiente:** instalar en la Y700 (**no se pudo: `adb devices` vacío**, la tablet se reinició y perdió el
ADB inalámbrico), probar los 5 puntos con el mando, y `dumpsys usb` al despertar el mando para cerrar el
punto 1 con dato. **Sin commit** (él lo pidió así).

### Correcciones de la misma ronda (13-10, tras la primera prueba con el mando)
- **1.4.4-diag (versionCode 31):** Menú/Select con asignaciones en «Todas las apps» — al encender la
  vigilancia de firmas en todas las apps (punto 5) esos dos botones pasan por el detector, y el camino de
  devolución (`lostSignatureKey`) mandaba al motor solo el «apretar»; el arreglo apresurado mandó
  «apretar + soltar» juntos, lo que cancelaba el «Mantener» (salía al soltar el dedo) y forzaba la
  reinyección de la tecla (`input keyevent 108` = abrir Files en esta ROM).
- **1.4.5-diag (versionCode 32):** arreglo definitivo — se devuelve **solo el «apretar»** al motor (el
  soltar real completa el toque) y, sin regla para ese botón, se devuelve con **origen de mando**
  (`input gamepad keyevent`), nunca con la tecla pelona.
- **1.4.6-diag (versionCode 33):** R3 (asignado a «Puntero: solo apagar») colaba al launcher al volver a
  apretarlo y activaba el icono enfocado (Files). Ahora **un botón asignado en «Ratón» nunca llega a la
  app**: encendido hace lo suyo, apagado no hace nada. Y con el puntero encendido la **cruceta** también se
  consume (`SOURCE_DPAD` faltaba en el filtro y movía la selección del inicio por debajo del puntero).
- Las tres versiones se instalaron por **ADB inalámbrico** (`192.168.68.107:5555`, `adb tcpip 5555` con el
  cable y luego el cable fuera para probar el mando). **Sin commit.**

- **1.4.7-diag (versionCode 34):** L3 + R3 encendía el ratón en la pantalla de inicio y **abría Files**: la
  combinación solo consumía su segunda tecla y la **primera** llegaba al launcher, donde un botón del mando
  activa el icono enfocado. Ahora la primera tecla **se consume en la pantalla de inicio** (y solo ahí); en
  apps y juegos sigue igual. Instalada por ADB inalámbrico. **Sin commit.**

- **1.4.8-diag (versionCode 35):** tras el 1.4.7 («el resto de los botones no funciona»): con el puntero
  encendido y en la pantalla de inicio, el botón de apagarlo (R3, asignado en «Ratón») se lo comía la lógica
  de L3 + R3 → el puntero no se apagaba nunca y, como con el puntero encendido los demás botones no hacen
  nada por diseño, parecía que se habían roto todos. Ahora la primera tecla de la combinación **sí pasa
  cuando está asignada en «Ratón»**. (La captura de `getevent` de esa ronda se colgó y quedó en 0 bytes.)

- **1.4.9-diag (versionCode 36):** «A no funciona, B no es Atrás, RT/LT al launcher no funcionan» — verificado
  en la tablet que **no había ventanas de la app** (el puntero estaba apagado), así que el culpable era la
  regla del 1.4.6 («un botón del ratón nunca llega a la app»): con el puntero apagado se comía A, B, LT, RT…
  que él también usa para otras cosas (esos botones quedaron en el almacén del ratón por la migración).
  Ahora con el puntero apagado un botón del ratón **se comporta como si no tuviera nada asignado**; solo se
  retiene la primera tecla de L3 + R3 en el inicio. **Sin commit.**

- **1.4.10-diag (versionCode 37):** el botón elegido para SALIR del ratón (él tenía L3) dejó de funcionar en la
  pantalla de inicio: en la condición de la primera tecla de L3 + R3 puse `&&` donde iba `||`. Ahora pasa si
  no estás en el inicio **o** si es un botón del ratón con el puntero encendido — sirve para **cualquier**
  botón que el usuario asigne, no solo R3. **Sin commit.**

## 2026-10-13 (17ª parte — 1.4.11-diag, versionCode 38: cajón de apps, rueda sin puntero y aviso de traseros al despertar)

2ª ronda del día, con el reporte de Roberto mientras probaba la 1.4.10-diag (mando **no** conectado;
la tablet estaba como segunda pantalla con SuperDisplay).

**Lo que reportó:** (1) el cajón de apps **solo funciona en la página principal del launcher**;
(2) el **aviso rojo** al despertar el mando («No pude preparar 4 trasero(s)»); (3) asignar
«Rueda arriba/abajo» a la cruceta **no hace nada en ningún lado**. Más 4 preguntas (Artemis en
X-Input con su «Un toque», X-Input vs HID vs DS5, LoL con Mantis Gamepad Pro, macros en GameHub).

**Arreglos (una sola versión):**
1. **Cajón desde cualquier página** (`AutoMode.kt`): si el launcher ya está al frente, antes se
   deslizaba **en el acto** (de ahí que solo valiera en la página principal). Ahora se manda
   **«Inicio» igualmente** (eso devuelve a la página de casa) y se esperan **260 ms** a que se
   asiente antes de deslizar (`esperarLauncherYDeslizar(intento, asentar)`).
2. **Rueda con el puntero apagado** (`AutoMode.kt` → `ruedaDeBoton`; `Mouse.kt` →
   `wheel(dir, mag, desdeBoton)`): todo el ratón dependía de `mouse.isOn`, así que una rueda
   asignada a un botón no hacía nada. Ahora la **rueda** se atiende también apagado (un scroll no
   necesita cursor); **los clics, el arrastre y el clic derecho siguen necesitando el puntero**
   (apuntan con el cursor). Si el puntero no se encendió nunca, desliza al centro de la pantalla.
3. **Aviso de traseros que daba la lata** (`MainActivity.preparePaddles`): cuando lo lanza la app
   sola (conectar, despertar, volver a la app) **reintenta 3 veces cada 1,2 s en silencio**; el
   botón manual sigue avisando en el acto.

**Verificado:** `assembleRelease --offline` → **BUILD SUCCESSFUL in 1m 52s**; APK 2.957.071 B,
sha256 `0dec49932fa586bfc6f9a39307c665d10af36eb4ee10a590aac935d2f84dea1f`;
`adb connect 192.168.68.107:5555` (adb de SuperDisplay) + `adb install -r` → **Success**;
`dumpsys package` → `versionCode=38 versionName=1.4.11-diag` `lastUpdateTime=2026-10-13 16:10:39`.
**Sin commit** (la rama `g9-remap-v2` sigue con el árbol sucio, como lo pidió).

**Errores de compilación que se anotaron:** `Log` sin prefijo en `MainActivity` (va
`android.util.Log`) y la **recursión** de `preparePaddles` sin tipo de retorno
(`Type checking has run into a recursive problem` → declarar `: Unit`).

**Pendiente de su prueba:** cajón desde otra página, rueda en la cruceta con el puntero apagado, y
que el aviso de traseros ya no salga al despertar el mando.

### Corrección de la misma ronda — la rueda en la cruceta SIEMPRE fue con el puntero encendido (1.4.12-diag, versionCode 39)

Roberto corrigió mi diagnóstico: **no funciona con el puntero encendido**; él nunca habló del puntero
apagado. El cambio que hice en la 1.4.11 (`wheel(desdeBoton)` para atender la rueda con el puntero
apagado) era **no pedido** y quedó **revertido**.

**Causa real:** la **cruceta de este mando llega como eje (HAT)**, no como teclas (la propia app la
dibuja así en la prueba de botones: `PageTest.kt:52`; `MainActivity.kt:260` lee `AXIS_HAT_X/Y`). El
camino de los botones del ratón (`AutoModeService.handleKey`) solo mira **eventos de tecla**, así que
cualquier acción del ratón asignada a la cruceta (la rueda incluida) **no se enteraba nunca**.

**Arreglo:** en `onMotionEvent`, con el puntero encendido se llama también a **`hatMouse(event)`**:
leen `AXIS_HAT_X/Y` y, **al cambiar de dirección** (una vez por pulsación; mantenerla no cambia el
valor y al soltar vuelve a 0), disparan la acción del ratón asignada a ese código
(`Arriba/Abajo/Izquierda/Derecha`). Si esa dirección no tiene nada asignado, no hace nada.

**Rastro para el diagnóstico en vivo:** dos renglones nuevos en el log —
`ratón: botón <código> -> <acción|nada>` (camino de teclas) y
`cruceta (HAT x/y) sobre el código <código> -> <acción>` (camino del eje).

**Verificado:** `assembleRelease --offline` → **BUILD SUCCESSFUL in 47s**; APK 2.957.071 B, sha256
`4d3dcadb8d3582cae8bfb0aa1b115b43a6c858445fce884c7240f03f5f0ef1b0`;
`adb install -r` → **Success**; `dumpsys package` → `versionCode=39 versionName=1.4.12-diag`
`lastUpdateTime=2026-10-13 16:19:55`; `logcat -c` limpio antes de la prueba. **Sin commit.**

### 1.4.13-diag (versionCode 40) — el cajón se comprueba solo (red de seguridad)

El deslizamiento del cajón **no se pudo reproducir como fallo** por ADB (abre desde otra página y desde
dentro de una app; medido con `uiautomator dump` + `input swipe`). En vez de dejar la suposición que puse
en la 1.4.11, la app ahora **se comprueba a sí misma**:

- Después de deslizar, `revisarCajon()` mira el árbol de la ventana al frente y decide si el cajón está
  abierto de verdad: marca medida = la caja **«Buscar apps»**; si el idioma no la trae, el otro dato
  medido (el cajón lista todas las apps: 31 etiquetas frente a 6-11 de una página).
- Si no abrió, **reintenta una vez desde Inicio** (`CAJON_INTENTOS = 2`) y deja rastro en el log
  (`cajón de apps: deslizo (intento N)` / `abierto con el intento N` / `no abrió tras N intento(s)`).
- **Se quitó el «Inicio por adelantado»** de la 1.4.11: la premisa «Inicio vuelve a la página principal»
  no se pudo confirmar con medición y solo agregaba retraso.

Verificado: `assembleRelease --offline` → **BUILD SUCCESSFUL in 47s**; `adb install -r` → **Success**;
`dumpsys package` → `versionName=1.4.13-diag` `lastUpdateTime=2026-10-13 16:54:34`; APK sha256
`b0ee31c147c574ba9e19fd62ae3f495209d401e736ed3a8fef4e7dccd3522d64`; app viva (`pidof` 16704), sin
errores en logcat y el servicio de accesibilidad activo. **Sin commit.**

## 2026-10-15 (18ª parte — 1.4.14-diag, versionCode 41: otros controles y doble toque para media)

Ronda pedida como **investigación y validación** («Es pregunta… No quiero que echemos a perder nada»)
y contestada con el mecanismo verificado antes de tocar nada; Roberto dio el OK a las tres en un
mismo formulario, así que se implementaron **las tres en UNA sola versión** (su regla de una ronda =
un build). La nota de registro está en el vault: `default/18-sesion-2026-10-15-registro-otros-controles-media.md`.

**1) Select/Start con un control ajeno (GameSir por Bluetooth).** No son botones de la app: llegan
como `KEYCODE_BUTTON_SELECT` (Vista) y `KEYCODE_BUTTON_START` (Menú) — `Gestures.kt:146-147` — que son
**exactamente los de la firma de los traseros del G9** (`Signatures.kt:19-22`). El detector de firmas
los **retenía hasta 90 ms** (`SignatureDetector.WINDOW_MS`) y los devolvía por inyección, y su
condición (`hayTrasero`) se encendía con la **marca guardada en prefs** (`paddle_sig`), **sin mirar
qué control estaba conectado**. Arreglo: el detector solo vigila Vista/Menú **cuando la entrada viene
del G9** (`esDelG9(event.device)`, `AutoMode.kt:857`).

**2) Con un control que no sea el G9, solo los Ajustes rápidos.** Camino nuevo: si el evento viene de
un pad ajeno (`!esDelG9(...)` + es del mando + no es la app al frente + no es la prueba de botones),
el servicio devuelve `legionKey(event)` y **nada más**: el botón guía sigue haciendo lo suyo (tocar =
Atrás, mantener = **Ajustes rápidos**) y **todo lo demás de ese pad pasa tal cual a la app**
(`AutoMode.kt:827-834`). Lo propio del G9 (ratón, traseros, reglas por app, teclas) no se le aplica.

**3) Doble toque adelante / atrás para media.** El swipe **no se deprecia**: `MEDIA_FF` / `MEDIA_REW`
se renombran a **«Swipe adelante» / «Swipe atrás»** (`Gestures.kt:42,44`) y se **añaden**
`MEDIA_FF_DBL` / `MEDIA_REW_DBL` («Doble toque adelante / atrás»), que inyectan **dos toques** en la
mitad derecha (0,75) o izquierda (0,25) de la pantalla (`AutoMode.seekVideoDouble` + `tapAt`): un
«toque» es una ruta de **longitud cero** (la referencia de Android de `GestureDescription` dice que
una ruta sin recorrido es un toque que no se mueve) y se mandan en **dos gestos separados 150 ms**,
que es lo que hace un dedo en un doble toque real.

**Helper nuevo:** `esDelG9(dev)` (`AutoMode.kt:790`) — nombre con «G9» / «Legion» / «X-Box 360» /
«DualSense» / «DualShock», o **VID del G9 0x3537**. Lo usa también `onKeyEvent` para decidir qué
control se apretó por última vez (`lastG9Key` / `lastOtherPad`), que antes repetía la comparación a mano.

**Verificado:** `assembleRelease --offline` → **BUILD SUCCESSFUL in 2m 46s**; APK 2.957.071 B, sha256
`1fa7a3af949dde9e6b50d8e1309f516e95db4538507ba56d6f89b93ad1d0f437`; `adb connect 192.168.68.106:5555`
(el ADB inalámbrico se movió del .107 al **.106**; el adb de SuperDisplay), `adb install -r` →
**Success**; `dumpsys package` → `versionCode=41 versionName=1.4.14-diag`
`lastUpdateTime=2026-10-15 11:21:13` con `firstInstallTime=2026-10-11 00:42:25` intacto (se actualizó
conservando los datos); el **servicio de accesibilidad siguió activo** tras instalar; app viva
(`ps -A` → `io.github.sudodiego.g9control`); el **texto nuevo está en el APK** (`unzip -p … classes.dex
| grep -a "Doble toque adelante"` → 2 coincidencias). **Sin commit** (el árbol sigue sucio, como él pide).

**Pendiente de medir en la tablet (con el pad conectado):** (a) con qué códigos llega el GameSir por
Bluetooth (¿manda `BUTTON_SELECT`/`BUTTON_START` y `BUTTON_MODE`, o usa otros?) — `getevent` +
`dumpsys input` + `logcat -T 1 'G9Auto:D' '*:S'` en una pulsación real; (b) que el doble toque de media
adelante/atrás se comporte como espera en sus reproductores (el punto de toque es 0,75 / 0,25 del ancho,
al centro de alto: si alguna app pide el borde, se ajusta).

### Corrección de la misma ronda — medido con el pad conectado (1.4.15 → 1.4.17-diag)

Roberto conectó el GameSir por Bluetooth y la medición (`getevent` + `logcat`) destapó dos cosas que no
se veían en el código: el pad es un **GameSir-Cyclone 2** y **comparte el fabricante del G9**
(`vendor=0x3537`, `product=0x1052`; el G9 es `0x1134`/`0x1136`). Mi primer filtro iba por fabricante, así
que al Cyclone 2 lo trataba **como G9** y Select/Start seguían retenidos. Además el **botón guía** de ese
pad manda `KEY_HOMEPAGE` → **`KEYCODE_HOME` (3)**, no el botón guía de mando.

- **1.4.15-diag (42)**: el guía de un control ajeno se atiende **por dispositivo** (fuentes GAMEPAD o
  JOYSTICK) con `guiaAjena()`: mantener 0,6 s = **Ajustes rápidos**, tocar = **Inicio**; Select/Start
  pasan tal cual; `esDelG9` pasa a distinguir por **producto** además del nombre.
- **1.4.16-diag (43)**: `DIAG ajeno` temporal (tecla + dispositivo + fuentes). Medido: Select `k=109`,
  Start `k=108`, guía `k=3`, todos desde el nodo `GameSir-Cyclone 2 Consumer Control`.
- **1.4.17-diag (44)**: se **quita el DIAG**. Roberto confirmó en pantalla que **mantener el botón guía
  abre los Ajustes rápidos**.

Verificado: `assembleRelease --offline` → **BUILD SUCCESSFUL in 30s**; APK sha256
`672ae8f17977b58d30c20b22146ec6a8c000d1697c40f66368fdcc07d32fd0d7`; `adb install -r` → **Success**;
`dumpsys package` → `versionCode=44 versionName=1.4.17-diag` `lastUpdateTime=2026-10-15 11:54:34`, con
`firstInstallTime=2026-10-11 00:42:25` intacto; servicio de accesibilidad activo; app viva; sin errores en
el buffer de crash. **Sin commit.**

## 2026-10-15 (19ª parte — identidad del fork y publicación: repo renombrado, READMEs, Release y metadatos de tienda)

Roberto revisó el repo en GitHub y el diagnóstico fue correcto: **el trabajo estaba, pero no se veía**.
Medido antes de tocar nada:

- `README.md` y `README.es.md` eran **byte a byte iguales** a los del proyecto original (`diff` contra
  `../G9Control-upstream`), y `CONTEXTO.md` también. O sea: la portada del repo mostraba el proyecto
  original (incluso su crédito a Claude Code), no el fork.
- La descripción del repo era la del original, y los topics estaban vacíos.
- El repo se llamaba `G9Control` aunque la app se llama **G9 Control Remapper** desde el commit
  `4929821` (el código ya estaba renombrado: `app_name` + los 16 textos).
- El árbol tenía 6 archivos **sin commitear** (la ronda 1.4.14→1.4.17).

**Identidad (commit `b1accae`):** repo renombrado a **`Rarstorm/G9-Control-Remapper`** (la API devolvió
`full_name` nuevo), descripción propia, 10 topics (`android`, `gamepad`, `controller-remapper`,
`legion-g9`, `lenovo-y700`, `kotlin`, `accessibility-service`, `shizuku`, `input-mapping`,
`jetpack-compose`) y `remote` local actualizado. Los dos README se reescribieron desde cero:
qué es el fork, qué viene del original (tabla), y **lo nuestro en tres bloques** —remapeo, traseros con
comando propio por firma, y ratón por overlay— más los extras. Autoría corregida: **DeepSeek vía Hermes
Agent** (antes decía Claude/Anthropic; el aviso legal dentro de la app ya estaba limpio: se verificó con
`grep` que no menciona a Anthropic en ningún archivo de `app/src`).

**Publication (Obtainium):** el flujo `android.yml` ahora tiene la entrada `publicar_tag`; con un tag
publica una **GitHub Release con el APK adjunto** (`G9-Control-Remapper-<tag>.apk`), que es lo que
Obtainium necesita (lee Releases, no artefactos del CI) y sigue siendo **solo manual**. Se añadió
`permissions: contents: write`.

**Publicación en tiendas — dos requisitos duros de F-Droid** (documentación oficial consultada:
*Inclusion Policy* y *Submitting to F-Droid*):

1. **ID nuevo obligatorio.** La política dice: *«Any application with a duplicate android application ID
   matching another domain name will receive automatic rejection»* y *«Forked applications need to obtain
   a fresh Android ID that differs from the original … and must receive corresponding name, icon and
   string changes»*. La app seguía con el `applicationId` del autor original. Ahora es
   **`io.github.rarstorm.g9controlremapper`** (el `namespace`/paquete Kotlin se queda como estaba: no es
   el ID de Android y moverlo no aporta). Consecuencia dicha a Roberto: al cambiar el ID, la app pasa a
   ser **otra app** en la tablet — se instala aparte y los datos se recuperan con el **Respaldo** que ya
   tiene la app.
2. **Ícono: SÍ es nuestro — comprobación mía equivocada y corregida.** Yo comparé los XML de
   `drawable/ic_launcher_bg.xml` y `ic_launcher_fg.xml` (idénticos al upstream) y concluí que el arte era
   del original. **Estaba mal**: el ícono que Android dibuja sale de `mipmap-anydpi-v26/ic_launcher.xml`,
   que apunta a **`@mipmap/ic_launcher_bg|fg|mono`**, y esos PNG **no existen en el upstream** (verificado
   archivo por archivo: `NO existe en upstream`). Además los XML de `drawable/` no los referencia nada
   (`grep`: las únicas menciones son las de `ic_launcher.xml`, y esas son `@mipmap/…`): son restos
   heredados. O sea que el arte (la «L» y el tridente en neón) **lo hizo Roberto con Nano Banana 2.1 en
   Google Flow**, y el crédito ya está en los dos README. No hace falta rehacer el ícono para F-Droid.

   **Lección para este repo:** para saber qué arte se usa hay que seguir la referencia que resuelve el
   sistema (`@mipmap/…`), no comparar los archivos que *parecen* ser el ícono. Y el `mipmap` gana al
   `drawable` del mismo nombre, así que un XML idéntico al upstream puede ser ruido.

**Metadatos y gráficos para las tiendas (en el repo, como pide la guía):**
`fastlane/metadata/android/{es-ES,en-US}/` con `title.txt`, `short_description.txt` (71 / 64 caracteres,
límite 80), `full_description.txt` (2327 / 2318, límite 4000), `changelogs/44.txt` (449 / 440, límite 500)
e `images/icon.png` (512×512) + `images/featureGraphic.png` (1024×500). Los gráficos se generaron con PIL
a partir de las capas del ícono adaptativo, recortando el **centro 66.7 %** (la zona visible de 72/108 dp)
para que el PNG de la tienda se vea como lo ve el usuario. El banner se verificó **viéndolo**: el primer
intento cortaba el subtítulo por el borde derecho, así que ahora el cuerpo de cada línea se ajusta solo
(`fit()` baja el tamaño hasta que el texto entra en el ancho disponible) y se volvió a comprobar a ojo.
El ícono de tienda y el banner son, por ahora, el arte del ícono actual (ver el punto 2: falta el ícono
propio del fork). Capturas de pantalla: pendientes (Roberto las dejó para otra sesión; las de
`docs/screenshots/` son del original).

**Verificado en local antes de publicar:** `JAVA_HOME=%LOCALAPPDATA%\Android\jdk-17`,
`./gradlew :app:assembleRelease --offline` → **BUILD SUCCESSFUL**, APK 2.957.087 B; `aapt2 dump badging` →
`package: name='io.github.rarstorm.g9controlremapper' versionCode='44' versionName='1.4.17'`,
`application-label:'G9 Control Remapper'`; `apksigner verify --print-certs` → SHA-256
`a9dea6410ff5899c9f319f7af231044c7fd248fee526e3fc52b5cccb0383e049` (la clave del proyecto, la de siempre).
