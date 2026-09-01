package org.torproject.android.ui.v3onionservice.clientauth;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.torproject.android.BuildConfig;
import org.torproject.android.service.db.V3ClientAuthColumns;

import java.util.Objects;

public class ClientAuthContentProvider extends ContentProvider {
    public static final String[] PROJECTION = {
            V3ClientAuthColumns._ID,
            V3ClientAuthColumns.DOMAIN,
            V3ClientAuthColumns.HASH,
            V3ClientAuthColumns.ENABLED,
    };
    private static final String AUTH = BuildConfig.APPLICATION_ID + ".ui.v3onionservice.clientauth";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTH + "/v3auth");
    private static final int V3AUTHS = 1, V3AUTH_ID = 2;

    private static final UriMatcher uriMatcher;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTH, "v3auth", V3AUTHS);
        uriMatcher.addURI(AUTH, "v3auth/#", V3AUTH_ID);
    }

    private ClientAuthDatabase mDatabase;

    @Override
    public boolean onCreate() {
        mDatabase = new ClientAuthDatabase(getContext());
        return true;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        int match = uriMatcher.match(uri);
        return switch (match) {
            case V3AUTHS -> "vnd.android.cursor.dir/vnd.torproject.v3auths";
            case V3AUTH_ID -> "vnd.android.cursor.item/vnd.torproject.v3auth";
            default -> null;
        };
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (uriMatcher.match(uri) == V3AUTH_ID)
            selection = "_id=" + uri.getLastPathSegment();
        SQLiteDatabase db = mDatabase.getReadableDatabase();
        return db.query(ClientAuthDatabase.DATABASE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase db = mDatabase.getWritableDatabase();
        long regId = db.insert(ClientAuthDatabase.DATABASE_NAME, null, values);
        Objects.requireNonNull(getContext()).getContentResolver().notifyChange(CONTENT_URI, null);
        return ContentUris.withAppendedId(CONTENT_URI, regId);
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (uriMatcher.match(uri) == V3AUTH_ID)
            selection = "_id=" + uri.getLastPathSegment();
        SQLiteDatabase db = mDatabase.getWritableDatabase();
        int rows = db.delete(ClientAuthDatabase.DATABASE_NAME, selection, selectionArgs);
        Objects.requireNonNull(getContext()).getContentResolver().notifyChange(CONTENT_URI, null);
        return rows;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mDatabase.getWritableDatabase();
        if (uriMatcher.match(uri) == V3AUTH_ID)
            selection = "_id=" + uri.getLastPathSegment();
        int rows = db.update(ClientAuthDatabase.DATABASE_NAME, values, selection, null);
        Objects.requireNonNull(getContext()).getContentResolver().notifyChange(CONTENT_URI, null);
        return rows;
    }


}
