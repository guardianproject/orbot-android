package org.torproject.android.ui.v3onionservice;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.torproject.android.R;
import org.torproject.android.service.OrbotConstants;
import org.torproject.android.service.db.OnionServiceColumns;
import org.torproject.android.service.db.V3ClientAuthColumns;
import org.torproject.android.ui.v3onionservice.clientauth.ClientAuthContentProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Objects;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class V3BackupUtils {
    private static final String configFileName = "config.json";
    private final Context mContext;
    private final ContentResolver mResolver;

    public V3BackupUtils(Context context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
    }

    public String createV3ZipBackup(String relativePath, Uri zipFile) {
        String[] files = createFilesForZippingV3(relativePath);
        ZipUtilities zip = new ZipUtilities(files, zipFile, mResolver);
        if (!zip.zip()) return null;
        return zipFile.getPath();
    }

    public String createV3AuthBackup(String domain, String keyHash, Uri backupFile) {
        String fileText = V3ClientAuthColumns.buildV3ClientAuthFile(domain, keyHash);
        try {
            ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(backupFile, "w");
            assert pfd != null;
            FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
            fos.write(fileText.getBytes());
            fos.close();
            pfd.close();
        } catch (IOException ioe) {
            return null;
        }
        return backupFile.getPath();
    }

    /**
     * @noinspection CallToPrintStackTrace
     */ // todo this doesn't export data for onions that orbot hosts which have authentication (not supported yet...)
    @SuppressLint("Range")
    private String[] createFilesForZippingV3(String relativePath) {
        final String v3BasePath = getV3BasePath() + "/" + relativePath + "/";
        final String hostnamePath = v3BasePath + "hostname",
                configFilePath = v3BasePath + configFileName,
                privKeyPath = v3BasePath + "hs_ed25519_secret_key",
                pubKeyPath = v3BasePath + "hs_ed25519_public_key";

        Cursor portData = mResolver.query(OnionServiceContentProvider.CONTENT_URI, OnionServiceColumns.getV3_ONION_SERVICE_PROJECTION(),
                OnionServiceColumns.PATH + "=\"" + relativePath + "\"", null, null);

        JSONObject config = new JSONObject();
        try {
            if (portData == null || portData.getCount() != 1)
                return null;
            portData.moveToNext();


            config.put(OnionServiceColumns.NAME, portData.getString(portData.getColumnIndex(OnionServiceColumns.NAME)));
            config.put(OnionServiceColumns.PORT, portData.getString(portData.getColumnIndex(OnionServiceColumns.PORT)));
            config.put(OnionServiceColumns.ONION_PORT, portData.getString(portData.getColumnIndex(OnionServiceColumns.ONION_PORT)));
            config.put(OnionServiceColumns.DOMAIN, portData.getString(portData.getColumnIndex(OnionServiceColumns.DOMAIN)));
            config.put(OnionServiceColumns.CREATED_BY_USER, portData.getString(portData.getColumnIndex(OnionServiceColumns.CREATED_BY_USER)));
            config.put(OnionServiceColumns.ENABLED, portData.getString(portData.getColumnIndex(OnionServiceColumns.ENABLED)));

            portData.close();

            FileWriter fileWriter = new FileWriter(configFilePath);
            fileWriter.write(config.toString());
            fileWriter.close();
        } catch (JSONException | IOException ioe) {
            ioe.printStackTrace();
            return null;
        }

        return new String[]{hostnamePath, configFilePath, privKeyPath, pubKeyPath};
    }

    private void extractConfigFromUnzippedBackupV3(String backupName) {
        File v3BasePath = getV3BasePath();
        String v3Dir = backupName.substring(0, backupName.lastIndexOf('.'));
        String configFilePath = v3BasePath + "/" + v3Dir + "/" + configFileName;
        File v3Path = new File(v3BasePath.getAbsolutePath(), v3Dir);
        if (!v3Path.isDirectory()) v3Path.mkdirs();

        File configFile = new File(configFilePath);
        try {
            FileInputStream fis = new FileInputStream(configFile);
            FileChannel fc = fis.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            String jsonString = Charset.defaultCharset().decode(bb).toString();
            JSONObject savedValues = new JSONObject(jsonString);
            ContentValues fields = new ContentValues();

            int port = savedValues.getInt(OnionServiceColumns.PORT);
            fields.put(OnionServiceColumns.PORT, port);
            fields.put(OnionServiceColumns.NAME, savedValues.getString(OnionServiceColumns.NAME));
            fields.put(OnionServiceColumns.ONION_PORT, savedValues.getInt(OnionServiceColumns.ONION_PORT));
            fields.put(OnionServiceColumns.DOMAIN, savedValues.getString(OnionServiceColumns.DOMAIN));
            fields.put(OnionServiceColumns.CREATED_BY_USER, savedValues.getInt(OnionServiceColumns.CREATED_BY_USER));
            fields.put(OnionServiceColumns.ENABLED, savedValues.getInt(OnionServiceColumns.ENABLED));

            Cursor dbService = mResolver.query(OnionServiceContentProvider.CONTENT_URI, OnionServiceColumns.getV3_ONION_SERVICE_PROJECTION(),
                    OnionServiceColumns.PORT + "=" + port, null, null);
            if (dbService == null || dbService.getCount() == 0)
                mResolver.insert(OnionServiceContentProvider.CONTENT_URI, fields);
            else
                mResolver.update(OnionServiceContentProvider.CONTENT_URI, fields, OnionServiceColumns.PORT + "=" + port, null);
            dbService.close();

            configFile.delete();
            if (v3Path.renameTo(new File(v3BasePath, "/v3" + port))) {
                Toast.makeText(mContext, R.string.backup_restored, Toast.LENGTH_LONG).show();
            } else {
                // collision, clean up files
                for (File file : Objects.requireNonNull(v3Path.listFiles()))
                    file.delete();
                v3Path.delete();
                Toast.makeText(mContext, mContext.getString(R.string.backup_port_exist, ("" + port)), Toast.LENGTH_LONG).show();
            }
        } catch (IOException | JSONException | NullPointerException e) {
            Log.e("V3BackupUtils", e.toString());
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show();
        }
    }

    private File getV3BasePath() {
        return new File(mContext.getFilesDir().getAbsolutePath(), OrbotConstants.ONION_SERVICES_DIR);
    }

    public void restoreZipBackupV3(Uri zipUri) {
        if (zipUri == null) return;
        Cursor returnCursor = mResolver.query(zipUri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String backupName = returnCursor.getString(nameIndex);
        returnCursor.close();

        String v3Dir = backupName.substring(0, backupName.lastIndexOf('.'));
        File v3Path = new File(getV3BasePath().getAbsolutePath(), v3Dir);
        if (new ZipUtilities(null, zipUri, mResolver).unzip(v3Path.getAbsolutePath()))
            extractConfigFromUnzippedBackupV3(backupName);
        else
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show();
    }

    public void restoreClientAuthBackup(String authFileContents) {
        ContentValues fields = new ContentValues();
        String[] split = authFileContents.split(":");
        if (split.length != 4) {
            Toast.makeText(mContext, R.string.error, Toast.LENGTH_LONG).show();
            return;
        }
        fields.put(V3ClientAuthColumns.DOMAIN, split[0]);
        fields.put(V3ClientAuthColumns.HASH, split[3]);
        mResolver.insert(ClientAuthContentProvider.CONTENT_URI, fields);
        Toast.makeText(mContext, R.string.backup_restored, Toast.LENGTH_LONG).show();
    }

}
