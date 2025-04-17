package org.torproject.android.ui.v3onionservice.clientauth;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.torproject.android.R;
import org.torproject.android.core.DiskUtils;
import org.torproject.android.core.LocaleHelper;
import org.torproject.android.ui.v3onionservice.V3BackupUtils;

public class ClientAuthFragment extends Fragment {

    public static final String BUNDLE_KEY_ID = "_id",
            BUNDLE_KEY_DOMAIN = "domain",
            BUNDLE_KEY_HASH = "key_hash_value";

    private ContentResolver mResolver;
    private ClientAuthListAdapter mAdapter;
    private static final int REQUEST_CODE_READ_ZIP_BACKUP = 12;

    static final String CLIENT_AUTH_FILE_EXTENSION = ".auth_private",
            CLIENT_AUTH_SAF_MIME_TYPE = "*/*";

    public ClientAuthFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(LocaleHelper.onAttach(context));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_v3auth, container, false);

        requireActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        mResolver = requireContext().getContentResolver();
        mAdapter = new ClientAuthListAdapter(requireContext(),
                mResolver.query(ClientAuthContentProvider.CONTENT_URI,
                        ClientAuthContentProvider.PROJECTION, null, null, null));
        mResolver.registerContentObserver(ClientAuthContentProvider.CONTENT_URI, true,
                new V3ClientAuthContentObserver(new Handler()));

        view.findViewById(R.id.fab).setOnClickListener(v ->
                new ClientAuthCreateDialogFragment()
                        .show(getParentFragmentManager(), ClientAuthCreateDialogFragment.class.getSimpleName()));

        ListView auths = view.findViewById(R.id.auth_hash_list);
        auths.setAdapter(mAdapter);
        auths.setOnItemClickListener((parent, itemView, position, id) -> {
            Cursor item = (Cursor) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putInt(BUNDLE_KEY_ID, item.getInt(item.getColumnIndex(ClientAuthContentProvider.V3ClientAuth._ID)));
            args.putString(BUNDLE_KEY_DOMAIN, item.getString(item.getColumnIndex(ClientAuthContentProvider.V3ClientAuth.DOMAIN)));
            args.putString(BUNDLE_KEY_HASH, item.getString(item.getColumnIndex(ClientAuthContentProvider.V3ClientAuth.HASH)));
            new ClientAuthActionsDialogFragment(args)
                    .show(getParentFragmentManager(), ClientAuthActionsDialogFragment.class.getSimpleName());
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.v3_client_auth_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_import_auth_priv) {
            Intent readFileIntent = DiskUtils.createReadFileIntent(CLIENT_AUTH_SAF_MIME_TYPE);
            startActivityForResult(readFileIntent, REQUEST_CODE_READ_ZIP_BACKUP);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_READ_ZIP_BACKUP && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Cursor cursor = mResolver.query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    cursor.moveToFirst();
                    String filename = cursor.getString(nameIndex);
                    cursor.close();
                    if (!filename.endsWith(CLIENT_AUTH_FILE_EXTENSION)) {
                        Toast.makeText(getContext(), R.string.error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String authText = DiskUtils.readFileFromInputStream(mResolver, uri);
                    new V3BackupUtils(getContext()).restoreClientAuthBackup(authText);
                }
            }
        }
    }

    private class V3ClientAuthContentObserver extends ContentObserver {
        V3ClientAuthContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            mAdapter.changeCursor(mResolver.query(ClientAuthContentProvider.CONTENT_URI,
                    ClientAuthContentProvider.PROJECTION, null, null, null));
        }
    }
}
