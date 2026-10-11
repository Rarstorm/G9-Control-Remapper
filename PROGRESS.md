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
- [ ] F5: stick→mouse, perfiles por app, teclado/mouse vía Shizuku.
