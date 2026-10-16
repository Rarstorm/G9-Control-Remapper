# Publicar en F-Droid (borrador listo para mandar)

Aquí está el archivo con el que se le pide a F-Droid que construya la app desde el código:
`io.github.rarstorm.g9controlremapper.yml`. **No se ejecuta desde este repo**: se copia a
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) y se manda como *merge request*. El nombre del
archivo es el `applicationId`, así que no se puede renombrar.

Los textos e imágenes de la ficha ya viven en este repo
(`fastlane/metadata/android/{es-ES,en-US}/`), que es donde F-Droid los lee y donde quedan bajo nuestro
control.

## Antes de mandarlo

- [ ] **El autor original respondió** al issue
      [sudo-diego/G9Control#1](https://github.com/sudo-diego/G9Control/issues/1). La política de
      inclusión exige que «the author of the app has been notified (and does not oppose the
      inclusion)». Sin eso, los revisores lo devuelven.
- [ ] El tag de la versión a construir existe y apunta a un commit ya publicado
      (`v1.4.17` → `70f2419f0009a9d2ef1b2cce3b3a4caf015d09dd`). Al publicar una versión nueva hay que
      **agregar un bloque `Builds`** con su `versionName`, `versionCode` y `commit` (hash completo).

## Cómo se manda (cuenta de GitLab: `rstrempler`)

1. Entrar en <https://gitlab.com/fdroid/fdroiddata> y **Fork**.
2. En el fork, crear una rama: `io.github.rarstorm.g9controlremapper`.
3. Copiar `io.github.rarstorm.g9controlremapper.yml` a `metadata/` (en la raíz del fork de fdroiddata).
4. Abrir el *merge request* contra `master`, con el título
   `New app: G9 Control Remapper` y, en la descripción, el enlace al issue de permiso del autor.
5. El CI de F-Droid revisa el formato y **prueba a construirla** en un entorno aislado; después lo
   revisa una persona. Si todo va bien, la app tarda entre 24 y 48 h en aparecer en el repositorio.

## Lo que hay que tener claro (ya está avisado en los README)

- **F-Droid firma con SU clave.** Quien tenga el APK de las Releases de GitHub **no puede actualizar**
  al de F-Droid (ni al revés): hay que desinstalar e instalar, y los ajustes se pasan con
  Respaldo → Exportar / Importar.
- Si F-Droid rechaza la inclusión, la app sigue disponible igual: Releases + Obtainium, o un repositorio
  F-Droid propio.
