package org.torproject.android.ui.v3onionservice.clientauth;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import org.torproject.android.R;
import org.torproject.android.service.db.V3ClientAuthColumns;

public class ClientAuthListAdapter extends CursorAdapter {

    private static final int TYPE_IMPORT = 0;
    private static final int TYPE_ITEM = 1;

    private final LayoutInflater mLayoutInflater;
    private final Context mContext;
    private final OnImportClickListener mImportClickListener;

    interface OnImportClickListener {
        void onImportClicked();
    }

    ClientAuthListAdapter(Context context, Cursor cursor, OnImportClickListener listener) {
        super(context, cursor, 0);
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
        mImportClickListener = listener;
    }

    @Override
    public int getCount() {
        int cursorCount = super.getCount();
        return cursorCount + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) return null;
        return super.getItem(position - 1);
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return -1;
        return super.getItemId(position - 1);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_IMPORT : TYPE_ITEM;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mLayoutInflater.inflate(R.layout.layout_client_cookie_list_item, null);
    }

    @SuppressLint("Range")
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndex(V3ClientAuthColumns._ID));
        final String where = V3ClientAuthColumns._ID + "=" + id;
        TextView domain = view.findViewById(R.id.cookie_onion);
        String url = cursor.getString(cursor.getColumnIndex(V3ClientAuthColumns.DOMAIN));
        if (url.length() > 10)
            url = url.substring(0, 10) + "…" + "  .onion";
        domain.setText(url);
        SwitchCompat enabled = view.findViewById(R.id.cookie_switch);
        enabled.setChecked(cursor.getInt(cursor.getColumnIndex(V3ClientAuthColumns.ENABLED)) == 1);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ContentResolver resolver = context.getContentResolver();
            ContentValues fields = new ContentValues();
            fields.put(V3ClientAuthColumns.ENABLED, isChecked);
            resolver.update(ClientAuthContentProvider.CONTENT_URI, fields, where, null);
            Toast.makeText(context, R.string.please_restart_Orbot_to_enable_the_changes, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (getItemViewType(position) == TYPE_IMPORT) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(
                        R.layout.item_import_auth,
                        parent,
                        false
                );
            }

            convertView.setOnClickListener(_ -> {
                if (mImportClickListener != null) {
                    mImportClickListener.onImportClicked();
                }
            });

            return convertView;
        }

        Cursor cursor = (Cursor) super.getItem(position - 1);

        if (convertView == null) {
            convertView = newView(mContext, cursor, parent);
        }

        bindView(convertView, mContext, cursor);
        return convertView;
    }
}

