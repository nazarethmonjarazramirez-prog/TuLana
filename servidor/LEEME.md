# Servidor de TuLana (IA y cuentas)

Este Worker de Cloudflare guarda la clave de la IA. La app lo usa para leer tickets y para el chat de Merino,
sin que cada persona tenga que poner su propia clave. Si el servidor no responde, la app sigue funcionando
sin IA (lee el ticket en el teléfono y Merino contesta en modo rápido).

1. Clave gratuita de Gemini: https://aistudio.google.com → "Get API key" → "Create API key".
2. Cloudflare (gratis): https://dash.cloudflare.com → Workers & Pages → Create → Start with Hello World →
   nombre `tulana-ia` → Deploy → Edit code → borra todo, pega `worker.js` → Deploy.
3. En el Worker: Settings → Variables and Secrets → Add:
   - `GEMINI_API_KEY` (tipo Secret) = tu clave de Gemini
   - `APP_TOKEN` (tipo Secret) = una contraseña inventada, por ejemplo `tulana-2026-xyz`
4. Abre la dirección del Worker (https://tulana-ia.TU-USUARIO.workers.dev): debe decir `"ok":true`.
5. En GitHub: repositorio → Settings → Secrets and variables → Actions → pestaña Variables → New repository variable:
   - `TULANA_SERVER` = la dirección del Worker
   - `TULANA_TOKEN` = la misma contraseña de APP_TOKEN
6. Actions → Compilar APK → Run workflow. El APK nuevo ya trae la IA.

Opcional: `GEMINI_MODEL` cambia el modelo (por defecto `gemini-flash-lite-latest`; `gemini-flash-latest` es más preciso
pero tiene menos lecturas gratis al día). Si en lugar de Gemini pones `ANTHROPIC_API_KEY`, usa Claude (de pago).

Nota de privacidad: en el plan gratuito de Gemini, Google puede usar lo que se envía para mejorar sus productos.
No envíes datos sensibles; los tickets normalmente solo traen productos y precios.

## Cuentas con Google y respaldo en la nube

El mismo Worker guarda las cuentas y los respaldos en una base de datos D1 de Cloudflare.

1. Cloudflare → Storage & databases → D1 SQL database → Create database → nombre `tulana-db`.
2. Worker `tulana-ia` → Settings → Bindings → Add binding → D1 database → nombre de variable `DB` → `tulana-db`.
   Las tablas se crean solas la primera vez.
3. Google Cloud (console.cloud.google.com) → proyecto TuLana → Google Auth Platform:
   - Cliente **Android**: paquete `mx.cochinito.app`, SHA-1 de la firma de TuLana
     `16:64:BB:BA:0E:E2:EE:96:F9:BC:2A:C7:C5:9E:88:0F:DC:78:EB:D3`.
   - Cliente **Aplicación web**: su ID de cliente va en `worker.js` (GOOGLE_CLIENT_ID) y en la app.
   - Público → Publicar app.
4. Pega el `worker.js` nuevo en el Worker → Deploy. La dirección del Worker debe decir `"cuentas":true`.

Qué guarda: nombre, correo y foto de la cuenta de Google; una copia comprimida de los datos de la app; sesiones
(solo su huella, no el token); y un conteo diario de uso de la IA por persona (se borra a los 3 días).
"Eliminar mi cuenta" en la app borra todo de la base de datos. Variable opcional: `LIMITE_IA_DIA` (por defecto 150).
