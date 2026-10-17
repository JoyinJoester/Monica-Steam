package com.monica.handofftest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

/** Calls the real exported activity from another UID. No Steam login or real credentials. */
public final class HostActivity extends Activity {
    private String nonce;
    private TextView status;
    private static final String TARGET = "takagi.ru.monica.steam.recoverytest";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        status.setTextSize(20);
        status.setText("WAITING_FOR_MONICA");
        setContentView(status);
        if (state != null) { status.setText("REQUEST_CANCELLED"); return; }
        nonce = UUID.randomUUID().toString();
        Intent request = new Intent("takagi.ru.monica.steamapp.action.REQUEST_MAFILE")
            .setComponent(new ComponentName(TARGET, "takagi.ru.monica.steam.backup.share.SteamMaFileExportActivity"))
            .putExtra("protocol_version", getIntent().getBooleanExtra("invalidVersion", false) ? 2 : 1)
            .putExtra("request_id", nonce);
        startActivityForResult(request, 31);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != 31) return;
        if (result != RESULT_OK) { status.setText("REQUEST_CANCELLED"); return; }
        byte[] bytes = new byte[1024 * 1024 + 1];
        try {
            Uri uri = data.getData();
            if (data.getIntExtra("protocol_version", -1) != 1 || !nonce.equals(data.getStringExtra("request_id")) ||
                uri == null || !uri.toString().matches("content://takagi\\.ru\\.monica\\.steam\\.recoverytest\\.mafile-handoff/v1/[a-f0-9]{64}")) {
                throw new IllegalStateException();
            }
            int size = 0;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                int read;
                while ((read = input.read(bytes, size, bytes.length - size)) != -1) {
                    size += read;
                    if (size >= bytes.length) throw new IllegalStateException();
                }
            }
            JSONObject account = new JSONObject(new String(bytes, 0, size, java.nio.charset.StandardCharsets.UTF_8));
            if (!"76561198000000001".equals(account.getString("steamid")) ||
                !"handoff-fixture".equals(account.getString("account_name"))) throw new IllegalStateException();
            boolean replayDenied = false;
            try (InputStream ignored = getContentResolver().openInputStream(uri)) { }
            catch (Exception expected) { replayDenied = true; }
            status.setText(replayDenied ? "RECEIVED_ONCE_REPLAY_DENIED" : "REPLAY_NOT_DENIED");
        } catch (Exception failure) {
            status.setText("INVALID_RESPONSE");
        } finally { Arrays.fill(bytes, (byte) 0); }
    }
}
