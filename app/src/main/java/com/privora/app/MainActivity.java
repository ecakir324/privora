package com.privora.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaController;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA_REQUEST = 7101;
    private static final String PREFS = "privora_prefs_v3";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_ITEMS = "items_json";

    private static final int BG = Color.rgb(13, 15, 20);
    private static final int SURFACE = Color.rgb(24, 27, 36);
    private static final int SURFACE_2 = Color.rgb(34, 38, 50);
    private static final int TEXT = Color.rgb(240, 242, 248);
    private static final int MUTED = Color.rgb(166, 173, 190);
    private static final int ACCENT = Color.rgb(124, 92, 255);
    private static final int DANGER = Color.rgb(255, 85, 110);

    private SharedPreferences prefs;
    private final ArrayList<VaultItem> items = new ArrayList<>();
    private File vaultDir;
    private File coverDir;
    private File cacheDir;
    private boolean pendingEncryptedImport = false;
    private String activeFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        vaultDir = new File(getFilesDir(), "vault_media");
        coverDir = new File(getFilesDir(), "covers");
        cacheDir = new File(getCacheDir(), "privora_view");
        ensureDir(vaultDir);
        ensureDir(coverDir);
        ensureDir(cacheDir);
        createNoMedia(vaultDir);
        createNoMedia(coverDir);
        createNoMedia(cacheDir);
        cleanCache();

        if (!prefs.contains(KEY_PIN_HASH)) showPinSetup();
        else showLogin();
    }

    private void showPinSetup() {
        LinearLayout root = authRoot("Privora", "Önce 4–8 haneli PIN belirle.");
        EditText pin1 = pinInput("Yeni PIN");
        EditText pin2 = pinInput("PIN tekrar");
        Button save = filledButton("PIN Kaydet", ACCENT);
        root.addView(pin1);
        root.addView(pin2);
        root.addView(save);
        setContentView(root);

        save.setOnClickListener(v -> {
            String p1 = pin1.getText().toString().trim();
            String p2 = pin2.getText().toString().trim();
            if (p1.length() < 4 || p1.length() > 8) {
                toast("PIN 4–8 hane olmalı.");
                return;
            }
            if (!p1.equals(p2)) {
                toast("PIN tekrar eşleşmiyor.");
                return;
            }
            prefs.edit().putString(KEY_PIN_HASH, CryptoUtils.sha256(p1)).apply();
            showMain();
        });
    }

    private void showLogin() {
        LinearLayout root = authRoot("Privora", "Vault'u açmak için PIN gir.");
        EditText pin = pinInput("PIN");
        Button login = filledButton("Aç", ACCENT);
        root.addView(pin);
        root.addView(login);
        setContentView(root);

        login.setOnClickListener(v -> {
            String expected = prefs.getString(KEY_PIN_HASH, "");
            if (expected.equals(CryptoUtils.sha256(pin.getText().toString().trim()))) showMain();
            else {
                pin.setText("");
                toast("PIN hatalı.");
            }
        });
    }

    private LinearLayout authRoot(String titleText, String subtitleText) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(90), dp(24), dp(24));
        root.setBackgroundColor(BG);

        TextView logo = label("●", 44, ACCENT, Gravity.CENTER, true);
        TextView title = label(titleText, 34, TEXT, Gravity.CENTER, true);
        TextView subtitle = label(subtitleText, 16, MUTED, Gravity.CENTER, false);
        subtitle.setPadding(0, dp(8), 0, dp(28));
        root.addView(logo);
        root.addView(title);
        root.addView(subtitle);
        return root;
    }

    private void showMain() {
        loadItems();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(18), dp(16), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Privora", 28, TEXT, Gravity.LEFT, true);
        TextView subtitle = label(items.size() + " medya • hızlı gizleme aktif", 13, MUTED, Gravity.LEFT, false);
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button add = pillButton("+ Medya", ACCENT, Color.WHITE);
        header.addView(add);
        root.addView(header);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), dp(12), dp(14), dp(12));
        info.setBackground(round(SURFACE, dp(18), 0));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(0, dp(14), 0, dp(10));
        root.addView(info, infoLp);
        TextView i1 = label("Videoya uzun bas → kapak saniyesi seç", 14, TEXT, Gravity.LEFT, true);
        TextView i2 = label("Import bozulmasın diye v3'te kapak hatası dosya eklemeyi durdurmaz.", 12, MUTED, Gravity.LEFT, false);
        info.addView(i1);
        info.addView(i2);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.addView(filterButton("Tümü", "all"));
        filters.addView(filterButton("Foto", "image"));
        filters.addView(filterButton("Video", "video"));
        root.addView(filters);

        List<VaultItem> visible = filteredItems();
        if (visible.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            TextView icon = label("□", 64, SURFACE_2, Gravity.CENTER, true);
            TextView t = label("Henüz medya yok", 22, TEXT, Gravity.CENTER, true);
            TextView s = label("+ Medya butonuyla fotoğraf veya video seç. Dosya uygulama içine alınır.", 15, MUTED, Gravity.CENTER, false);
            Button emptyAdd = filledButton("Medya Ekle", ACCENT);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.setMargins(0, dp(18), 0, 0);
            empty.addView(icon);
            empty.addView(t);
            empty.addView(s);
            empty.addView(emptyAdd, btnLp);
            root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            emptyAdd.setOnClickListener(v -> showImportModeDialog());
        } else {
            ScrollView scroll = new ScrollView(this);
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(3);
            grid.setPadding(0, dp(12), 0, dp(24));
            for (VaultItem item : visible) grid.addView(mediaCard(item));
            scroll.addView(grid);
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        setContentView(root);
        add.setOnClickListener(v -> showImportModeDialog());
    }

    private Button filterButton(String label, String filter) {
        boolean active = activeFilter.equals(filter);
        Button b = pillButton(label, active ? ACCENT : SURFACE, active ? Color.WHITE : MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            activeFilter = filter;
            showMain();
        });
        return b;
    }

    private List<VaultItem> filteredItems() {
        ArrayList<VaultItem> out = new ArrayList<>();
        for (VaultItem item : items) {
            if (activeFilter.equals("all") || activeFilter.equals(item.type)) out.add(item);
        }
        return out;
    }

    private View mediaCard(VaultItem item) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int cardW = (screen - dp(44)) / 3;
        int imageSize = cardW - dp(10);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(5), dp(5), dp(5), dp(7));
        card.setBackground(round(SURFACE, dp(16), 0));
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = cardW;
        glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        glp.setMargins(dp(3), dp(4), dp(3), dp(8));
        card.setLayoutParams(glp);

        FrameLayout media = new FrameLayout(this);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap coverBmp = loadBitmap(new File(coverDir, item.coverName).getAbsolutePath(), imageSize);
        if (coverBmp != null) img.setImageBitmap(coverBmp);
        else img.setImageDrawable(round(SURFACE_2, dp(12), 0));
        media.addView(img, new FrameLayout.LayoutParams(imageSize, imageSize));

        if (item.type.equals("video")) {
            TextView play = label("▶", 28, Color.WHITE, Gravity.CENTER, true);
            play.setBackground(round(Color.argb(120, 0, 0, 0), dp(24), 0));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER);
            media.addView(play, pp);
        }

        if (item.encrypted) {
            TextView lock = label("LOCK", 9, Color.WHITE, Gravity.CENTER, true);
            lock.setBackground(round(Color.argb(180, 0, 0, 0), dp(8), 0));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(42), dp(22), Gravity.RIGHT | Gravity.TOP);
            lp.setMargins(0, dp(5), dp(5), 0);
            media.addView(lock, lp);
        }

        TextView name = label(shortName(item.name), 11, MUTED, Gravity.CENTER, false);
        name.setMaxLines(2);
        card.addView(media);
        card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(v -> openItem(item));
        card.setOnLongClickListener(v -> {
            showItemMenu(item);
            return true;
        });
        return card;
    }

    private void showImportModeDialog() {
        String[] opts = {"Hızlı Gizle - önerilen", "Güvenli Şifrele - yavaş"};
        new AlertDialog.Builder(this)
                .setTitle("Import modu")
                .setMessage("Hızlı Gizle büyük videolarda daha seri çalışır. Şifreleme istersen dosyaya uzun basıp sonradan da yapabilirsin.")
                .setItems(opts, (d, which) -> {
                    pendingEncryptedImport = which == 1;
                    pickMedia();
                })
                .show();
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, PICK_MEDIA_REQUEST);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            fallback.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(fallback, "Medya seç"), PICK_MEDIA_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MEDIA_REQUEST || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) {
            toast("Dosya seçilmedi.");
            return;
        }

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Import");
        progress.setMessage("0 / " + uris.size() + " dosya alınıyor...");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            int ok = 0;
            int fail = 0;
            String lastError = null;
            for (int i = 0; i < uris.size(); i++) {
                final int index = i + 1;
                runOnUiThread(() -> progress.setMessage(index + " / " + uris.size() + " dosya alınıyor..."));
                ImportResult result = importUri(uris.get(i), pendingEncryptedImport);
                if (result.success) ok++;
                else {
                    fail++;
                    lastError = result.error;
                }
            }
            saveItems();
            final int finalOk = ok;
            final int finalFail = fail;
            final String finalLastError = lastError;
            runOnUiThread(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                showMain();
                String msg = finalOk + " dosya eklendi";
                if (finalFail > 0) msg += ", " + finalFail + " hata" + (finalLastError == null ? "" : ": " + finalLastError);
                toast(msg);
                if (finalOk > 0) showPostImportNote();
            });
        }).start();
    }

    private ImportResult importUri(Uri uri, boolean encrypt) {
        try {
            String mime = getContentResolver().getType(uri);
            String name = getDisplayName(uri);
            String lower = name.toLowerCase(Locale.ROOT);
            boolean isVideo = isVideo(mime, lower);
            boolean isImage = isImage(mime, lower);
            if (!isVideo && !isImage) return ImportResult.fail("Desteklenmeyen dosya: " + name);

            String id = UUID.randomUUID().toString().replace("-", "");
            String ext = extensionFromNameOrMime(lower, mime, isVideo);
            String type = isVideo ? "video" : "image";
            String coverName = id + ".jpg";
            File coverFile = new File(coverDir, coverName);
            String vaultName;

            if (encrypt) {
                File temp = new File(cacheDir, id + ext);
                copyUriToFile(uri, temp);
                tryGenerateCover(type, temp, uri, coverFile, 1);
                vaultName = id + ".vault";
                CryptoUtils.encryptFile(temp, new File(vaultDir, vaultName));
                safeDelete(temp);
            } else {
                vaultName = id + ext;
                File target = new File(vaultDir, vaultName);
                copyUriToFile(uri, target);
                tryGenerateCover(type, target, uri, coverFile, 1);
            }

            VaultItem item = new VaultItem();
            item.id = id;
            item.name = name;
            item.type = type;
            item.mime = mime == null ? (isVideo ? "video/*" : "image/*") : mime;
            item.ext = ext;
            item.vaultName = vaultName;
            item.coverName = coverName;
            item.coverSecond = isVideo ? 1 : 0;
            item.encrypted = encrypt;
            items.add(0, item);
            return ImportResult.ok();
        } catch (Exception e) {
            return ImportResult.fail(e.getMessage() == null ? "bilinmeyen hata" : e.getMessage());
        }
    }

    private void tryGenerateCover(String type, File mediaFile, Uri originalUri, File coverFile, int second) {
        try {
            if ("video".equals(type)) saveVideoFrameAsCover(mediaFile, originalUri, coverFile, second);
            else saveImageAsCover(mediaFile, coverFile);
        } catch (Exception ignored) {
            try {
                Bitmap fallback = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
                saveJpeg(fallback, coverFile, 84);
                fallback.recycle();
            } catch (Exception ignored2) {}
        }
    }

    private void showPostImportNote() {
        new AlertDialog.Builder(this)
                .setTitle("Import tamam")
                .setMessage("Dosya Privora içine alındı. Android izinlerinden dolayı orijinal galerideki kopyayı otomatik silemeyebiliriz; gizlemek istiyorsan orijinali galeriden manuel sil.")
                .setPositiveButton("Tamam", null)
                .show();
    }

    private void showItemMenu(VaultItem item) {
        ArrayList<String> opts = new ArrayList<>();
        if (item.type.equals("video")) opts.add("Kapak saniyesi seç");
        if (!item.encrypted) opts.add("Güvenli şifrele");
        opts.add("Sil");
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String c = opts.get(which);
                    if (c.equals("Kapak saniyesi seç")) showCoverPicker(item);
                    else if (c.equals("Güvenli şifrele")) confirmEncrypt(item);
                    else confirmDelete(item);
                })
                .show();
    }

    private void confirmEncrypt(VaultItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Dosya şifrelensin mi?")
                .setMessage("Büyük videolarda sürebilir. İşlem bitince dosya AES-GCM ile saklanır.")
                .setPositiveButton("Şifrele", (d, w) -> encryptExisting(item))
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void encryptExisting(VaultItem item) {
        ProgressDialog p = new ProgressDialog(this);
        p.setMessage("Şifreleniyor...");
        p.setCancelable(false);
        p.show();
        new Thread(() -> {
            String error = null;
            try {
                File plain = new File(vaultDir, item.vaultName);
                if (!plain.exists()) throw new Exception("Dosya bulunamadı.");
                File encrypted = new File(vaultDir, item.id + ".vault");
                CryptoUtils.encryptFile(plain, encrypted);
                safeDelete(plain);
                item.vaultName = encrypted.getName();
                item.encrypted = true;
                saveItems();
            } catch (Exception e) {
                error = e.getMessage();
            }
            String finalError = error;
            runOnUiThread(() -> {
                try { p.dismiss(); } catch (Exception ignored) {}
                if (finalError == null) {
                    toast("Şifrelendi.");
                    showMain();
                } else toast("Şifreleme hatası: " + finalError);
            });
        }).start();
    }

    private void confirmDelete(VaultItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Vault'tan silinsin mi?")
                .setMessage("Bu işlem Privora içindeki dosyayı siler.")
                .setPositiveButton("Sil", (d, w) -> {
                    safeDelete(new File(vaultDir, item.vaultName));
                    safeDelete(new File(coverDir, item.coverName));
                    items.remove(item);
                    saveItems();
                    showMain();
                    toast("Silindi.");
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void openItem(VaultItem item) {
        try {
            File readable = getReadableFile(item, "open");
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.BLACK);

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.setPadding(dp(8), dp(8), dp(8), dp(8));
            top.setBackgroundColor(BG);

            ImageButton back = new ImageButton(this);
            back.setImageResource(android.R.drawable.ic_media_previous);
            back.setColorFilter(Color.WHITE);
            back.setBackgroundColor(Color.TRANSPARENT);
            top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView name = label(item.name + (item.encrypted ? "  LOCK" : ""), 15, TEXT, Gravity.LEFT, true);
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (item.type.equals("video")) {
                Button cover = pillButton("Kapak", SURFACE_2, TEXT);
                top.addView(cover);
                cover.setOnClickListener(v -> showCoverPicker(item));
            }
            root.addView(top);

            if (item.type.equals("video")) {
                VideoView video = new VideoView(this);
                MediaController controller = new MediaController(this);
                controller.setAnchorView(video);
                video.setMediaController(controller);
                video.setVideoPath(readable.getAbsolutePath());
                video.setOnPreparedListener(mp -> video.start());
                video.setOnErrorListener((mp, what, extra) -> {
                    toast("Video açılamadı.");
                    return true;
                });
                root.addView(video, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            } else {
                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Bitmap bmp = loadBitmap(readable.getAbsolutePath(), 1600);
                if (bmp != null) img.setImageBitmap(bmp);
                else toast("Görsel açılamadı.");
                root.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            }
            setContentView(root);
            back.setOnClickListener(v -> {
                if (item.encrypted) cleanCache();
                showMain();
            });
        } catch (Exception e) {
            toast("Açılamadı: " + e.getMessage());
        }
    }

    private void showCoverPicker(VaultItem item) {
        if (!item.type.equals("video")) return;
        File videoFile = null;
        MediaMetadataRetriever retriever = null;
        try {
            videoFile = getReadableFile(item, "cover");
            final boolean tempFile = item.encrypted;
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoFile.getAbsolutePath());
            String durText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            int duration = Math.max(1, Integer.parseInt(durText == null ? "1" : durText) / 1000);
            final MediaMetadataRetriever finalRetriever = retriever;
            final File finalVideoFile = videoFile;

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(18), dp(10), dp(18), dp(4));
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
            TextView secText = label("Saniye: " + item.coverSecond, 16, Color.BLACK, Gravity.CENTER, true);
            box.addView(secText);
            SeekBar seek = new SeekBar(this);
            seek.setMax(duration);
            seek.setProgress(Math.min(item.coverSecond, duration));
            box.addView(seek);
            Button previewBtn = new Button(this);
            previewBtn.setText("Önizleme Al");
            previewBtn.setAllCaps(false);
            box.addView(previewBtn);

            final Runnable update = () -> {
                int sec = seek.getProgress();
                secText.setText("Saniye: " + sec);
                try {
                    Bitmap frame = finalRetriever.getFrameAtTime(sec * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame != null) preview.setImageBitmap(frame);
                } catch (Exception ignored) {}
            };
            update.run();
            previewBtn.setOnClickListener(v -> update.run());
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    secText.setText("Saniye: " + progress);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) { update.run(); }
            });

            final MediaMetadataRetriever releaseTarget = retriever;
            new AlertDialog.Builder(this)
                    .setTitle("Kapak saniyesi seç")
                    .setView(box)
                    .setPositiveButton("Kaydet", (d, w) -> {
                        try {
                            int sec = seek.getProgress();
                            saveVideoFrameAsCover(finalVideoFile, null, new File(coverDir, item.coverName), sec);
                            item.coverSecond = sec;
                            saveItems();
                            toast("Kapak güncellendi.");
                            showMain();
                        } catch (Exception e) {
                            toast("Kapak kaydedilemedi: " + e.getMessage());
                        } finally {
                            releaseRetriever(releaseTarget);
                            if (tempFile) safeDelete(finalVideoFile);
                        }
                    })
                    .setNegativeButton("Vazgeç", (d, w) -> {
                        releaseRetriever(releaseTarget);
                        if (tempFile) safeDelete(finalVideoFile);
                    })
                    .show();
        } catch (Exception e) {
            if (retriever != null) releaseRetriever(retriever);
            if (item.encrypted && videoFile != null) safeDelete(videoFile);
            toast("Kapak seçici açılamadı: " + e.getMessage());
        }
    }

    private File getReadableFile(VaultItem item, String tag) throws Exception {
        File source = new File(vaultDir, item.vaultName);
        if (!source.exists()) throw new Exception("Vault dosyası bulunamadı.");
        if (!item.encrypted) return source;
        File out = new File(cacheDir, item.id + "_" + tag + item.ext);
        CryptoUtils.decryptFile(source, out);
        return out;
    }

    private void saveVideoFrameAsCover(File file, Uri fallbackUri, File cover, int second) throws Exception {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            try {
                r.setDataSource(file.getAbsolutePath());
            } catch (Exception fileFail) {
                if (fallbackUri == null) throw fileFail;
                r.setDataSource(this, fallbackUri);
            }
            Bitmap frame = r.getFrameAtTime(Math.max(0, second) * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) throw new Exception("Frame alınamadı.");
            saveScaledJpeg(frame, cover, 720);
        } finally {
            releaseRetriever(r);
        }
    }

    private void saveImageAsCover(File image, File cover) throws Exception {
        Bitmap bmp = loadBitmap(image.getAbsolutePath(), 900);
        if (bmp == null) throw new Exception("Görsel kapak okunamadı.");
        saveScaledJpeg(bmp, cover, 720);
    }

    private Bitmap loadBitmap(String path, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > maxSize) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            return BitmapFactory.decodeFile(path, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveScaledJpeg(Bitmap src, File out, int maxSize) throws Exception {
        int w = src.getWidth();
        int h = src.getHeight();
        float ratio = Math.min(1f, maxSize / (float) Math.max(w, h));
        Bitmap scaled = ratio < 1f ? Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * ratio)), Math.max(1, Math.round(h * ratio)), true) : src;
        saveJpeg(scaled, out, 88);
        if (scaled != src) scaled.recycle();
    }

    private void saveJpeg(Bitmap bmp, File out, int quality) throws Exception {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        }
    }

    private void copyUriToFile(Uri uri, File out) throws Exception {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception("Dosya okunamadı.");
            byte[] buf = new byte[512 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }

    private String getDisplayName(Uri uri) {
        String out = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) out = c.getString(idx);
            }
        } catch (Exception ignored) {}
        if (out == null || out.trim().isEmpty()) out = "media_" + System.currentTimeMillis();
        return out;
    }

    private boolean isVideo(String mime, String lowerName) {
        return (mime != null && mime.startsWith("video/")) || lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".mkv") || lowerName.endsWith(".3gp") || lowerName.endsWith(".webm");
    }

    private boolean isImage(String mime, String lowerName) {
        return (mime != null && mime.startsWith("image/")) || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif");
    }

    private String extensionFromNameOrMime(String lowerName, String mime, boolean video) {
        int dot = lowerName.lastIndexOf('.');
        if (dot >= 0 && dot < lowerName.length() - 1) {
            String ext = lowerName.substring(dot);
            if (ext.length() <= 6) return ext;
        }
        if (mime != null) {
            if (mime.contains("png")) return ".png";
            if (mime.contains("webp")) return ".webp";
            if (mime.contains("gif")) return ".gif";
            if (mime.contains("quicktime")) return ".mov";
            if (mime.contains("matroska")) return ".mkv";
            if (mime.contains("3gpp")) return ".3gp";
            if (mime.contains("webm")) return ".webm";
        }
        return video ? ".mp4" : ".jpg";
    }

    private void loadItems() {
        items.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < arr.length(); i++) items.add(VaultItem.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
    }

    private void saveItems() {
        try {
            JSONArray arr = new JSONArray();
            for (VaultItem item : items) arr.put(item.toJson());
            prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private EditText pinInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(20);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setBackground(round(SURFACE, dp(16), dp(1), SURFACE_2));
        e.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.setMargins(0, dp(8), 0, dp(8));
        e.setLayoutParams(lp);
        return e;
    }

    private TextView label(String s, int sp, int color, int gravity, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(gravity);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(2), dp(3), dp(2), dp(3));
        return t;
    }

    private Button filledButton(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(color, dp(18), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.setMargins(0, dp(14), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button pillButton(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, dp(18), 0));
        return b;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        return round(color, radius, stroke, color);
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(stroke, strokeColor);
        return d;
    }

    private String shortName(String name) {
        if (name == null || name.isEmpty()) return "media";
        return name.length() > 18 ? name.substring(0, 15) + "..." : name;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void ensureDir(File f) {
        if (!f.exists()) f.mkdirs();
    }

    private void createNoMedia(File dir) {
        try {
            File n = new File(dir, ".nomedia");
            if (!n.exists()) n.createNewFile();
        } catch (Exception ignored) {}
    }

    private void safeDelete(File file) {
        try {
            if (file != null && file.exists()) {
                if (!file.delete()) file.deleteOnExit();
            }
        } catch (Exception ignored) {}
    }

    private void cleanCache() {
        try {
            File[] files = cacheDir.listFiles();
            if (files != null) for (File f : files) safeDelete(f);
        } catch (Exception ignored) {}
    }

    private void releaseRetriever(MediaMetadataRetriever r) {
        try { r.release(); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        cleanCache();
        super.onDestroy();
    }

    static class ImportResult {
        boolean success;
        String error;
        static ImportResult ok() {
            ImportResult r = new ImportResult();
            r.success = true;
            return r;
        }
        static ImportResult fail(String e) {
            ImportResult r = new ImportResult();
            r.success = false;
            r.error = e;
            return r;
        }
    }

    static class VaultItem {
        String id;
        String name;
        String type;
        String mime;
        String ext;
        String vaultName;
        String coverName;
        int coverSecond;
        boolean encrypted;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("type", type);
            o.put("mime", mime);
            o.put("ext", ext);
            o.put("vaultName", vaultName);
            o.put("coverName", coverName);
            o.put("coverSecond", coverSecond);
            o.put("encrypted", encrypted);
            return o;
        }

        static VaultItem fromJson(JSONObject o) {
            VaultItem item = new VaultItem();
            item.id = o.optString("id");
            item.name = o.optString("name", "media");
            item.type = o.optString("type", "image");
            item.mime = o.optString("mime", "");
            item.ext = o.optString("ext", item.type.equals("video") ? ".mp4" : ".jpg");
            item.vaultName = o.optString("vaultName");
            item.coverName = o.optString("coverName");
            item.coverSecond = o.optInt("coverSecond", item.type.equals("video") ? 1 : 0);
            item.encrypted = o.optBoolean("encrypted", false);
            return item;
        }
    }
}
