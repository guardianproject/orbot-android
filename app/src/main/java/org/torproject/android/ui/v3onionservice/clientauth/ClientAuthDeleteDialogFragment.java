package org.torproject.android.ui.v3onionservice.clientauth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.torproject.android.R;
import org.torproject.android.service.db.V3ClientAuthColumns;

public class ClientAuthDeleteDialogFragment extends DialogFragment {

    public ClientAuthDeleteDialogFragment() {}
    public ClientAuthDeleteDialogFragment(Bundle args) {
        super();
        setArguments(args);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.v3_delete_client_authorization)
                .setPositiveButton(R.string.v3_delete_client_authorization_confirm, (dialog, which) -> doDelete())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .create();
    }

    private void doDelete() {
        assert getArguments() != null;
        int id = getArguments().getInt(ClientAuthFragment.BUNDLE_KEY_ID);
        requireContext().getContentResolver().delete(ClientAuthContentProvider.CONTENT_URI, V3ClientAuthColumns._ID + "=" + id, null);
    }

}
