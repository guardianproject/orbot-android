package org.torproject.android.ui.v3onionservice.clientauth;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;

import org.torproject.android.R;
import org.torproject.android.service.db.V3ClientAuthColumns;
import org.torproject.android.ui.core.BaseActivity;
import org.torproject.android.ui.v3onionservice.V3BackupUtils;
import org.torproject.android.util.DiskUtils;

import java.util.Objects;

public class ClientAuthActivity extends BaseActivity {

    public static final String BUNDLE_KEY_ID = "_id",
            BUNDLE_KEY_DOMAIN = "domain",
            BUNDLE_KEY_HASH = "key_hash_value";


    private final ActivityResultLauncher<String[]> readBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::attemptToReadBackup
    );

    ContentResolver mResolver;
    ClientAuthListAdapter mAdapter;

    static final String CLIENT_AUTH_FILE_EXTENSION = ".auth_private",
            CLIENT_AUTH_SAF_MIME_TYPE = "*/*";

    @SuppressLint("Range")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_v3auth);
        // always prevent this screen from being screenshotted, regardless of the preference for screenshotting
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        mResolver = getContentResolver();
        mAdapter = new ClientAuthListAdapter(this, mResolver.query(ClientAuthContentProvider.CONTENT_URI, ClientAuthContentProvider.PROJECTION, null, null, null));
        mResolver.registerContentObserver(ClientAuthContentProvider.CONTENT_URI, true, new V3ClientAuthContentObserver(new Handler(Looper.getMainLooper())));

        findViewById(R.id.fab).setOnClickListener(_ ->
                new ClientAuthCreateDialogFragment().show(getSupportFragmentManager(), ClientAuthCreateDialogFragment.class.getSimpleName()));

        ListView auths = findViewById(R.id.auth_hash_list);
        auths.setAdapter(mAdapter);
        auths.setOnItemClickListener((parent, _, position, _) -> {
            Cursor item = (Cursor) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putInt(BUNDLE_KEY_ID, item.getInt(item.getColumnIndex(V3ClientAuthColumns._ID)));
            args.putString(BUNDLE_KEY_DOMAIN, item.getString(item.getColumnIndex(V3ClientAuthColumns.DOMAIN)));
            args.putString(BUNDLE_KEY_HASH, item.getString(item.getColumnIndex(V3ClientAuthColumns.HASH)));
            new ClientAuthActionsDialogFragment(args).show(getSupportFragmentManager(), ClientAuthActionsDialogFragment.class.getSimpleName());
        });
    }

    private void attemptToReadBackup(Uri uri) {
        if (uri == null) return;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        assert cursor != null;
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        cursor.moveToFirst();
        String filename = cursor.getString(nameIndex);
        cursor.close();
        if (!filename.endsWith(CLIENT_AUTH_FILE_EXTENSION)) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        String authText = DiskUtils.readFileFromInputStream(getContentResolver(), uri);
        new V3BackupUtils(this).restoreClientAuthBackup(authText);

    }

    private class V3ClientAuthContentObserver extends ContentObserver {
        V3ClientAuthContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mAdapter.changeCursor(mResolver.query(ClientAuthContentProvider.CONTENT_URI, ClientAuthContentProvider.PROJECTION, null, null, null));
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_import_auth_priv) {
            // unfortunately no good way to filter .auth_private files
            readBackupLauncher.launch(new String[]{CLIENT_AUTH_SAF_MIME_TYPE});
        } else if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.v3_client_auth_menu, menu);
        return true;
    }
}
