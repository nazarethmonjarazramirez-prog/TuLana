package mx.cochinito.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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

import java.io.File;

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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
