package org.torproject.android.ui.v3onionservice;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.torproject.android.R;
import org.torproject.android.core.DiskUtils;
import org.torproject.android.core.ui.BaseActivity;
import org.torproject.android.service.db.OnionServiceColumns;

public class OnionServiceActivity extends BaseActivity {

    static final String BUNDLE_KEY_ID = "id", BUNDLE_KEY_PORT = "port", BUNDLE_KEY_DOMAIN = "domain", BUNDLE_KEY_PATH = "path";
    private static final String BASE_WHERE_SELECTION_CLAUSE = OnionServiceColumns.CREATED_BY_USER + "=";
    private static final String BUNDLE_KEY_SHOW_USER_SERVICES = "show_user_key";
    private static final int REQUEST_CODE_READ_ZIP_BACKUP = 347;
    private MaterialButton btnShowUserServices;
    private MaterialButton btnShowAppServices;
    private FloatingActionButton fab;
    private ContentResolver mContentResolver;
    private OnionV3ListAdapter mAdapter;
    private CoordinatorLayout mLayoutRoot;

    @SuppressLint("Range")
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_hosted_services);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        mLayoutRoot = findViewById(R.id.hostedServiceCoordinatorLayout);
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> new OnionServiceCreateDialogFragment().show(getSupportFragmentManager(), OnionServiceCreateDialogFragment.class.getSimpleName()));

        mContentResolver = getContentResolver();
        mAdapter = new OnionV3ListAdapter(this, mContentResolver.query(OnionServiceContentProvider.CONTENT_URI, OnionServiceColumns.getV3_ONION_SERVICE_PROJECTION(), BASE_WHERE_SELECTION_CLAUSE + '1', null, null));
        mContentResolver.registerContentObserver(OnionServiceContentProvider.CONTENT_URI, true, new OnionServiceObserver(new Handler()));

        ListView onionList = findViewById(R.id.onion_list);

        btnShowUserServices = findViewById(R.id.radioUserServices);
        btnShowAppServices = findViewById(R.id.radioAppServices);

        btnShowUserServices.setOnClickListener(v -> {
            btnShowUserServices.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_enabled_purple));
            btnShowAppServices.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_disable_grey));
            filterServices(true);
        });

        btnShowAppServices.setOnClickListener(v -> {
            btnShowUserServices.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_disable_grey));
            btnShowAppServices.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_enabled_purple));
            filterServices(false);
        });

        boolean showUserServices = btnShowAppServices.isChecked() || bundle == null || bundle.getBoolean(BUNDLE_KEY_SHOW_USER_SERVICES, false);
        if (showUserServices) {
            btnShowUserServices.setChecked(true);
        } else {
            btnShowAppServices.setChecked(true);
        }
        filterServices(showUserServices);
        onionList.setAdapter(mAdapter);
        onionList.setOnItemClickListener((parent, view, position, id) -> {
            Cursor item = (Cursor) parent.getItemAtPosition(position);
            Bundle arguments = new Bundle();
            arguments.putInt(BUNDLE_KEY_ID, item.getInt(item.getColumnIndex(OnionServiceColumns._ID)));
            arguments.putString(BUNDLE_KEY_PORT, item.getString(item.getColumnIndex(OnionServiceColumns.PORT)));
            arguments.putString(BUNDLE_KEY_DOMAIN, item.getString(item.getColumnIndex(OnionServiceColumns.DOMAIN)));
            arguments.putString(BUNDLE_KEY_PATH, item.getString(item.getColumnIndex(OnionServiceColumns.PATH)));
            OnionServiceActionsDialogFragment dialog = new OnionServiceActionsDialogFragment(arguments);
            dialog.show(getSupportFragmentManager(), OnionServiceActionsDialogFragment.class.getSimpleName());
        });
    }

    private void filterServices(boolean showUserServices) {
        String predicate;
        if (showUserServices) {
            predicate = "1";
            fab.show();
        } else {
            predicate = "0";
            fab.hide();
        }
        mAdapter.changeCursor(mContentResolver.query(OnionServiceContentProvider.CONTENT_URI, OnionServiceColumns.getV3_ONION_SERVICE_PROJECTION(),
                BASE_WHERE_SELECTION_CLAUSE + predicate, null, null));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.hs_menu, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean(BUNDLE_KEY_SHOW_USER_SERVICES, btnShowUserServices.isChecked());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_restore_backup) {
            Intent readFileIntent = DiskUtils.createReadFileIntent(ZipUtilities.ZIP_MIME_TYPE);
            startActivityForResult(readFileIntent, REQUEST_CODE_READ_ZIP_BACKUP);
        } else if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int result, Intent data) {
        super.onActivityResult(requestCode, result, data);
        if (requestCode == REQUEST_CODE_READ_ZIP_BACKUP && result == RESULT_OK) {
            new V3BackupUtils(this).restoreZipBackupV3(data.getData());
        }
    }

    private class OnionServiceObserver extends ContentObserver {

        OnionServiceObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            filterServices(btnShowUserServices.isChecked());
            showBatteryOptimizationsMessageIfAppropriate();
        }
    }

    void showBatteryOptimizationsMessageIfAppropriate() {
        Cursor activeServices = getContentResolver().query(OnionServiceContentProvider.CONTENT_URI, OnionServiceColumns.getV3_ONION_SERVICE_PROJECTION(),
                OnionServiceColumns.ENABLED + "=1", null, null);
        if (activeServices == null) return;
        if (activeServices.getCount() > 0)
            PermissionManager.requestBatteryPermissions(this, mLayoutRoot);
        else
            PermissionManager.requestDropBatteryPermissions(this, mLayoutRoot);
        activeServices.close();
    }
}
