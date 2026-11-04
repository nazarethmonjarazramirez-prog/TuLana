package mx.cochinito.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contenedor nativo de la app Cochinito.
 * La interfaz vive en assets/index.html y se sirve desde un origen https local
 * (appassets.androidplatform.net) para que localStorage y las llamadas a la API funcionen.
 */
public class MainActivity extends ComponentActivity {

    private static final String HOST = "appassets.androidplatform.net";

    private WebView web;
    private ValueCallback<Uri[]> pendingFiles;
    private Uri cameraUri;
    private ActivityResultLauncher<Intent> picker;
    private TextRecognizer recognizer;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost())) return false;
                // Enlaces externos se abren en el navegador del teléfono
                try { startActivity(new Intent(Intent.ACTION_VIEW, url)); } catch (Exception ignored) { }
                return true;
            }
        });

        // Puente para que la app pinte la barra de estado con el color de la temporada
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setBarColor(String hex, boolean dark) {
                runOnUiThread(() -> {
                    try {
                        int c = Color.parseColor(hex);
                        getWindow().setStatusBarColor(c);
                        getWindow().setNavigationBarColor(c);
                        web.setBackgroundColor(c);
                        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), web);
                        bars.setAppearanceLightStatusBars(!dark);
                        bars.setAppearanceLightNavigationBars(!dark);
                    } catch (Exception ignored) { }
                });
            }

            // Lee el texto de la foto del ticket en el teléfono (ML Kit, sin internet)
            @JavascriptInterface
            public void ocr(String b64, String id) {
                runOnUiThread(() -> readTicket(b64, id));
            }
        }, "AndroidBridge");

        // Resultado de la cámara o la galería
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (pendingFiles == null) return;
            Uri[] files = null;
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data != null && data.getData() != null) {
                    files = new Uri[]{ data.getData() };
                } else if (cameraUri != null) {
                    files = new Uri[]{ cameraUri };
                }
            }
            pendingFiles.onReceiveValue(files);
            pendingFiles = null;
        });

        // <input type="file"> abre un selector con Cámara + Galería
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;

                Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                gallery.setType("image/*");
                Intent chooser = Intent.createChooser(gallery, "Foto del ticket");

                try {
                    File dir = new File(getCacheDir(), "camera");
                    if (!dir.exists()) dir.mkdirs();
                    File photo = new File(dir, "ticket_" + System.currentTimeMillis() + ".jpg");
                    cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", photo);
                    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{ camera });
                } catch (Exception e) {
                    cameraUri = null;
                }

                try {
                    picker.launch(chooser);
                } catch (Exception e) {
                    pendingFiles.onReceiveValue(null);
                    pendingFiles = null;
                    return false;
                }
                return true;
            }
        });

        // Botón "atrás": primero navega dentro de la app, luego cierra
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("window.onAndroidBack ? window.onAndroidBack() : false", value -> {
                    if (!"true".equals(value)) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                });
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("https://" + HOST + "/assets/index.html");
        }
    }

    private void readTicket(String b64, String id) {
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) { ocrDone(id, "", "decode"); return; }
            if (recognizer == null) recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener(result -> ocrDone(id, toRows(result), null))
                    .addOnFailureListener(e -> ocrDone(id, "", "ocr"));
        } catch (Exception e) {
            ocrDone(id, "", "ocr");
        }
    }

    /** Une las líneas que están a la misma altura para que "PRODUCTO ... $PRECIO" quede en un solo renglón. */
    private static String toRows(Text result) {
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock b : result.getTextBlocks())
            for (Text.Line l : b.getLines())
                if (l.getBoundingBox() != null) lines.add(l);
        if (lines.isEmpty()) return result.getText();
        Collections.sort(lines, (a, b) -> Integer.compare(a.getBoundingBox().centerY(), b.getBoundingBox().centerY()));
        StringBuilder out = new StringBuilder();
        List<Text.Line> row = new ArrayList<>();
        float rowY = 0, rowH = 0;
        for (Text.Line l : lines) {
            Rect r = l.getBoundingBox();
            if (!row.isEmpty() && Math.abs(r.centerY() - rowY) > Math.max(6f, rowH * 0.55f)) {
                appendRow(out, row); row.clear();
            }
            if (row.isEmpty()) { rowY = r.centerY(); rowH = r.height(); }
            else { rowY = (rowY * row.size() + r.centerY()) / (row.size() + 1); rowH = Math.max(rowH, r.height()); }
            row.add(l);
        }
        appendRow(out, row);
        return out.toString().trim();
    }

    private static void appendRow(StringBuilder out, List<Text.Line> row) {
        if (row.isEmpty()) return;
        Collections.sort(row, (a, b) -> Integer.compare(a.getBoundingBox().left, b.getBoundingBox().left));
        for (int i = 0; i < row.size(); i++) { if (i > 0) out.append("  "); out.append(row.get(i).getText()); }
        out.append('\n');
    }

    private void ocrDone(String id, String text, String err) {
        String js = "window.__ocrDone && window.__ocrDone(" + JSONObject.quote(id) + "," + JSONObject.quote(text == null ? "" : text) + "," + (err == null ? "null" : JSONObject.quote(err)) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
