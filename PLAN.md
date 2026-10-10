# PLAN — G9 Remapper (g9-remap-v2)

> Proyecto: extender G9Control (GPL-3, fork de sudo-diego) para remapeo total del Legion G9 2026.
> Estado vivo en PROGRESS.md. Detalle completo: vault `10-g9-remap-plan.md`.

## Objetivo
Cualquier botón del mando reasignable, por app, con gestos (tap/largo/doble), destinos de teclado/mouse/acción Android, y stick→mouse con curva.

## Verificado (10-10-2026)
- Canal GCM funcional en **Legion G9 2026 + Y700 Gen 5 (ZUI 18)**: comandos 1F/53/08 responden (log G9Control).
- Mando en DS5: VID 054C PID 0CE6, nombre "Legion Gaming Controller G9 2026". En Android: VID 3537 (GameSir).
- Limitaciones firmware: M no llega a Android; traseros solo copian botones/macros; teclas de teclado no envían nada; Home (BUTTON_MODE) con retraso (mal portador → usar B); en X-Input/DS5 no hay canal GCM.

## Arquitectura
- **Capa firmware (existe):** G9Usb.kt / Gcm.kt / BackKeys.kt / Macros.kt — asignación traseros + macros (máx 12 eventos, 11/paquete).
- **Capa software (construir):** AccessibilityService (AutoMode.kt) intercepta botones portadores → gestos (tap/largo/doble con duraciones configurables) → acciones: botón, tecla de teclado, botón de mouse, acción Android (Atrás/Inicio/Recientes/Captura/Volumen/Brillo/abrir app), macro.
- **Mouse:** stick (derecho/izquierdo) → cursor con curva/sensibilidad/zona muerta; botones = clics; rueda arriba/abajo asignable con velocidad.
- **Inyección** de teclado/mouse/scroll: vía Shizuku (shell) — instalado y confirmado en el equipo (Key Mapper lo usa).
- **Perfiles por app + global:** tabla app → mapa de botones (3 gestos c/u). Modo Sistema = default fuera de apps configuradas.

## Fases / checkpoints
- F1 ✅ fork Rarstorm/G9Control + clon local + rama g9-remap-v2
- F2 build CI verde (workflow GitHub Actions, sin keystore → debug key)
- F3 APK instalado en tablet (mantener G9Control 1.0.0 para comparar; no sobreescribir)
- F4 gestos tap/largo/doble en traseros
- F5 stick→mouse con curva
- F6 perfiles por app + acciones Android
- F7 teclado/mouse/scroll vía Shizuku

## Retomar tras corte
`git log` + PROGRESS.md + PLAN.md + vault `10-g9-remap-plan.md`. Cada hito = commit + push.

## Notas
- NO construir APK sin orden explícita (instrucción de Roberto).
- Preferir ediciones quirúrgicas (patch) sobre reescrituras.
- Latencias de inyección Shizuku: a medir en el equipo.
