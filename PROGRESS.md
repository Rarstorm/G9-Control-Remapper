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
- [ ] F3 APK instalado en tablet.
