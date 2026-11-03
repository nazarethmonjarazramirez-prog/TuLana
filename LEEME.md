# Cochinito — cómo obtener el APK

Este proyecto es la app Cochinito empaquetada como app de Android. GitHub la compila gratis en la nube y te entrega el archivo `Cochinito.apk` para instalarlo en cualquier teléfono Android 8 o más reciente.

No necesitas instalar Android Studio ni nada en tu computadora.

## 1. Sube el proyecto a GitHub (una sola vez)

1. Crea una cuenta en https://github.com (gratis) e inicia sesión.
2. Arriba a la derecha toca **+ → New repository**. Nómbralo `cochinito`, déjalo **Public** y toca **Create repository**.
3. En la página del repositorio vacío, toca el enlace **uploading an existing file**.
4. Descomprime el zip en tu computadora. Abre la carpeta `cochinito-android` y **arrastra todo lo que hay dentro** (la carpeta `app` y los archivos `build.gradle`, `settings.gradle`, `gradle.properties`, `LEEME.md`) a la página de GitHub.
5. Abajo toca **Commit changes**.

## 2. Activa la compilación automática

GitHub a veces ignora las carpetas que empiezan con punto al arrastrarlas, así que este archivo se crea a mano:

1. En tu repositorio toca **Add file → Create new file**.
2. En el nombre escribe exactamente: `.github/workflows/build-apk.yml`
3. Abre el archivo `.github/workflows/build-apk.yml` del zip con el Bloc de notas, copia todo su contenido y pégalo en GitHub.
4. Toca **Commit changes**.

Eso arranca la compilación.

## 3. Espera el APK (unos 5 minutos)

1. Entra a la pestaña **Actions** del repositorio. Verás "Compilar APK" en amarillo (trabajando).
2. Cuando se ponga en verde, ya está.
3. Si sale en rojo, abre la ejecución, copia el error y compártelo para corregirlo.

## 4. Instálalo en el teléfono

1. Desde el teléfono abre `https://github.com/TU-USUARIO/cochinito/releases`.
2. Toca **Cochinito.apk** para descargarlo.
3. Ábrelo. Android te pedirá permiso para **instalar apps de fuentes desconocidas**: acéptalo para tu navegador.
4. Si Play Protect avisa que la app no es conocida, toca **Instalar de todas formas**. Pasa con cualquier app que no viene de la Play Store.

Cada vez que cambies algo en el repositorio, GitHub genera un APK nuevo en Releases.

## 5. Activa la lectura de tickets con IA

El escáner usa la API de Claude con tu propia clave:

1. Entra a https://console.anthropic.com, crea una cuenta y agrega saldo (cada ticket cuesta centavos).
2. En **API Keys** crea una clave. Empieza con `sk-ant-`.
3. En la app ve a **Perfil → Lectura de tickets con IA**, pega la clave y toca **Guardar**.

Sin clave la app funciona igual para registrar gastos a mano, pegar el texto de un ticket, ver el dashboard y usar el cochinito.

## Importante

- **Tus datos viven solo en tu teléfono.** Si desinstalas la app se borran.
- **No compartas un APK con tu clave guardada.** La clave se guarda en cada teléfono, no dentro del APK, así que cada persona que instale la app debe poner la suya. Para darle la app a muchas personas sin que cada quien tenga clave, el siguiente paso es un pequeño servidor (por ejemplo una Edge Function de Supabase) que guarde la clave y haga la llamada a la IA.
- El APK está firmado con una llave de prueba (debug). Sirve para instalarlo directamente; para publicarlo en Play Store hay que generar una versión firmada.

## Qué hay en el proyecto

| Archivo | Para qué sirve |
|---|---|
| `app/src/main/assets/index.html` | Toda la app (pantallas, escáner, dashboard, cochinito) |
| `app/src/main/java/.../MainActivity.java` | Contenedor Android: muestra la app, abre cámara y galería, maneja el botón atrás |
| `app/src/main/res/drawable/ic_pig.xml` | Ícono del cochinito |
| `.github/workflows/build-apk.yml` | Instrucciones para que GitHub compile el APK |
