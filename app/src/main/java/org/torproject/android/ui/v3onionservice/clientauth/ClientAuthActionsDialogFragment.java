package org.torproject.android.ui.v3onionservice.clientauth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import org.torproject.android.R;

public class ClientAuthActionsDialogFragment extends DialogFragment {

    public ClientAuthActionsDialogFragment() {}

    public ClientAuthActionsDialogFragment(Bundle args) {
        super();
        setArguments(args);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SpannableString backupKeyText = new SpannableString(getString(R.string.v3_backup_key));
        backupKeyText.setSpan(new StyleSpan(Typeface.BOLD), 0, backupKeyText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        AlertDialog ad = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.v3_client_auth_activity_title)
                .setItems(new CharSequence[]{
                        backupKeyText,
                        getString(R.string.v3_delete_client_authorization)
                }, null)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .create();
        ad.getListView().setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0)
                new ClientAuthBackupDialogFragment(getArguments()).show(requireActivity().getSupportFragmentManager(), ClientAuthBackupDialogFragment.class.getSimpleName());
            else
                new ClientAuthDeleteDialogFragment(getArguments()).show(requireActivity().getSupportFragmentManager(), ClientAuthDeleteDialogFragment.class.getSimpleName());
            ad.dismiss();
        });
        return ad;
    }
}
