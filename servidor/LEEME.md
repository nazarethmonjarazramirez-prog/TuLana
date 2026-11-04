# Servidor de IA de TuLana

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
