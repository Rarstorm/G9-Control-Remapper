# PROGRESS — G9 Remapper

## 2026-10-10
- F1 ✅ Fork `Rarstorm/G9Control` creado (HTTP 202 verificado) y clonado en `C:\Users\Roberto\G9Control`.
- ✅ Rama `g9-remap-v2` creada (base: main = upstream v1.0.0 + commits posteriores).
- ✅ Validación GCM en hardware (mando Legion G9 2026 + Y700 Gen 5): comandos 1F/53/08 responden.
- ✅ Vault: `10-g9-remap-plan.md` (plan + estado) y `00-estado-default.md` actualizados.
- [x] Leer código clave: AutoMode.kt (onKeyEvent/handleKey/onAccessibilityEvent), Shell.kt, Gcm.kt, BackKeys.kt, Profiles.kt, PageAuto.kt, MainActivity.kt, auto_mode_service.xml.
- [x] Diseño anclado a código verificado (ver PLAN.md → "Diseño anclado al código").
- [x] Workflow `android.yml` creado — SOLO `workflow_dispatch` (nunca build automático).
- [ ] F2a: ejecutar build CI — pendiente de ORDEN explícita de Roberto; **solo con modelo pagado DeepSeek V4.1**.
- [ ] F3 APK instalado en tablet.
