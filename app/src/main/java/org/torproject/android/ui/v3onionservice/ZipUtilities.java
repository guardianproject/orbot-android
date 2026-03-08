package org.torproject.android.ui.v3onionservice;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtilities {
    private static final int BUFFER = 2048;
    public static final String ZIP_MIME_TYPE = "application/zip";

    private final String[] files;
    private final Uri zipFile;
    private final ContentResolver contentResolver;

    public ZipUtilities(@Nullable String[] files, @NonNull Uri zipFile, @NonNull ContentResolver contentResolver) {
        this.files = files;
        this.zipFile = zipFile;
        this.contentResolver = contentResolver;
    }

    public boolean zip() {
        try {
            BufferedInputStream origin;
            ParcelFileDescriptor pdf = contentResolver.openFileDescriptor(zipFile, "w");
            assert pdf != null;
            FileOutputStream dest = new FileOutputStream(pdf.getFileDescriptor());
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
            byte[] data = new byte[BUFFER];
            assert files != null;
            for (String file : files) {
                FileInputStream fi = new FileInputStream(file);
                origin = new BufferedInputStream(fi, BUFFER);
                ZipEntry entry = new ZipEntry(file.substring(file.lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
            out.close();
            dest.close();
            pdf.close();
        } catch (IOException e) {
            Log.wtf(TAG, e.toString());
            return false;
        }
        return true;
    }

    public boolean unzip(String outputPath) {
        InputStream is;
        try {
            is = contentResolver.openInputStream(zipFile);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
            boolean returnVal = extractFromZipInputStream(outputPath, zis);
            assert is != null;
            is.close();
            return returnVal;
        } catch (IOException e) {
            Log.wtf(TAG, e.toString());
            return false;
        }
    }

    private static final String TAG = "ZipUtilities";

    private static final List<String> ONION_SERVICE_CONFIG_FILES = Arrays.asList("config.json",
            "hostname",
            "hs_ed25519_public_key",
            "hs_ed25519_secret_key");

    private boolean extractFromZipInputStream(String outputPath, ZipInputStream zis) {
        File outputDir = new File(outputPath);
        try {
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            outputDir.mkdirs();

            while ((ze = zis.getNextEntry()) != null) {
                String filename = ze.getName();

                if (!ONION_SERVICE_CONFIG_FILES.contains(filename)) { // *any* kind of foreign file
                    File[] writtenFiles = outputDir.listFiles();
                    if (writtenFiles != null) {
                        for (File writtenFile: writtenFiles) {
                            writtenFile.delete();
                        }
                    }
                    outputDir.delete();
                    return false;
                }

                // Need to create directories if not exists, or it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(outputPath + "/" + filename);
                    fmd.mkdirs();
                    continue;
                }

                FileOutputStream fout = new FileOutputStream(outputPath + "/" + filename);

                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        } catch (IOException e) {
            Log.wtf(TAG, e.toString());
            return false;
        }
        return true;
    }


}