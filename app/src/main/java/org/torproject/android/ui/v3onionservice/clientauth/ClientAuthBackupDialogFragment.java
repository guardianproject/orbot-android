package org.torproject.android.ui.v3onionservice.clientauth;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import org.torproject.android.R;
import org.torproject.android.util.DiskUtils;
import org.torproject.android.ui.components.NoPersonalizedLearningEditText;
import org.torproject.android.ui.v3onionservice.V3BackupUtils;

import java.util.Objects;

public class ClientAuthBackupDialogFragment extends DialogFragment {

    private NoPersonalizedLearningEditText etFilename;
    private TextWatcher fileNameTextWatcher;

    private static final String BUNDLE_KEY_FILENAME = "filename";

    public ClientAuthBackupDialogFragment() {
    }

    public ClientAuthBackupDialogFragment(Bundle args) {
        super();
        setArguments(args);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog ad = new AlertDialog.Builder(getContext())
                .setTitle(R.string.v3_backup_key)
                .setMessage(R.string.v3_backup_key_warning)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .create();
        ad.setOnShowListener(dialog -> ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> doBackup()));
        FrameLayout container = new FrameLayout(ad.getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = getResources().getDimensionPixelOffset(R.dimen.alert_dialog_margin);
        params.leftMargin = margin;
        params.rightMargin = margin;
        etFilename = new NoPersonalizedLearningEditText(ad.getContext(), null);
        etFilename.setSingleLine(true);
        etFilename.setHint(R.string.v3_backup_name_hint);
        if (savedInstanceState != null)
            etFilename.setText(savedInstanceState.getString(BUNDLE_KEY_FILENAME, ""));
        fileNameTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                ad.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(s.toString().trim()));
            }
        };
        etFilename.addTextChangedListener(fileNameTextWatcher);
        etFilename.setLayoutParams(params);
        container.addView(etFilename);
        ad.setView(container);
        return ad;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BUNDLE_KEY_FILENAME, Objects.requireNonNull(etFilename.getText()).toString());
    }


    @Override
    public void onStart() {
        super.onStart();
        fileNameTextWatcher.afterTextChanged(etFilename.getEditableText());
    }



    private void doBackup() {
        String filename = Objects.requireNonNull(etFilename.getText()).toString().trim();
        if (!filename.endsWith(ClientAuthActivity.CLIENT_AUTH_FILE_EXTENSION))
            filename += ClientAuthActivity.CLIENT_AUTH_FILE_EXTENSION;
        Intent createFileIntent = DiskUtils.createWriteFileIntent(filename, ClientAuthActivity.CLIENT_AUTH_SAF_MIME_TYPE);
        requireActivity().startActivityForResult(createFileIntent, REQUEST_CODE_WRITE_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_WRITE_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                attemptToWriteBackup(data.getData());
            }
        }
    }

    private void attemptToWriteBackup(Uri outputFile) {
        assert getArguments() != null;
        var v3BackupUtils = new V3BackupUtils(getContext());
        var domain = getArguments().getString(ClientAuthActivity.BUNDLE_KEY_DOMAIN);
        var hash = getArguments().getString(ClientAuthActivity.BUNDLE_KEY_HASH);
        var backup = v3BackupUtils.createV3AuthBackup(domain, hash, outputFile);
        Toast.makeText(getContext(), backup != null ? R.string.backup_saved_at_external_storage : R.string.error, Toast.LENGTH_LONG).show();
        dismiss();
    }

    private static final int REQUEST_CODE_WRITE_FILE = 432;
}
