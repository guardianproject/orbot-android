/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */

package org.torproject.android.tv.ui;

import static org.torproject.android.service.OrbotConstants.*;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.torproject.android.tv.R;
import org.torproject.android.service.util.Prefs;
import org.torproject.android.service.vpn.TorifiedApp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

public class AppManagerActivity extends AppCompatActivity {

    private GridView listApps;
    private ListAdapter adapterApps;
    private ProgressBar progressBar;
    PackageManager pMgr = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pMgr = getPackageManager();

        this.setContentView(R.layout.layout_apps);
        setTitle("");
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        listApps = findViewById(R.id.applistview);
        progressBar = findViewById(R.id.progressBar);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mPrefs = Prefs.getSharedPrefs(getApplicationContext());
        reloadApps();
    }


    /*
     * Create the UI Options Menu (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.menu_refresh_apps)
        {
            mApps = null;
            reloadApps();
        }
        else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void reloadApps () {
        new AsyncTask<Void, Void, Void>() {
            protected void onPreExecute() {
                // Pre Code
                progressBar.setVisibility(View.VISIBLE);
            }
            protected Void doInBackground(Void... unused) {
                loadApps(mPrefs);
                return null;
            }
            protected void onPostExecute(Void unused) {
                listApps.setAdapter(adapterApps);
                progressBar.setVisibility(View.GONE);
            }
        }.execute();


    }

    SharedPreferences mPrefs = null;
    static ArrayList<TorifiedApp> mApps = null;

    private void loadApps (SharedPreferences prefs) {
        if (mApps == null)
            mApps = getApps(prefs);


        final LayoutInflater inflater = getLayoutInflater();

        adapterApps = new ArrayAdapter<TorifiedApp>(this, R.layout.layout_apps_item, R.id.itemtext,mApps) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                ListEntry entry = null;

                if (convertView == null)
                    convertView = inflater.inflate(R.layout.layout_apps_item, parent, false);
                else
                    entry = (ListEntry) convertView.getTag();

                if (entry == null) {
                    // Inflate a new view
                    entry = new ListEntry();
                    entry.icon = convertView.findViewById(R.id.itemicon);
                    entry.text = convertView.findViewById(R.id.itemtext);
                    convertView.setTag(entry);
                }

                final TorifiedApp app = mApps.get(position);

                if (entry.icon != null) {

                    try {
                        entry.icon.setImageDrawable(pMgr.getApplicationIcon(app.getPackageName()));
                        entry.icon.setOnClickListener(v -> {
                            final TorifiedApp tApp = (TorifiedApp) v.getTag();
                            if (tApp != null) {
                                tApp.setTorified(!app.isTorified());


                                Intent data = new Intent();
                                data.putExtra(Intent.EXTRA_PACKAGE_NAME,tApp.getPackageName());
                                setResult(RESULT_OK,data);
                                saveAppSettings();
                                finish();
                            }
                        });
                        entry.icon.setTag(app);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                if (entry.text != null) {
                    entry.text.setText(app.getName());
                }


                return convertView;
            }
        };


    }

    private static class ListEntry {
        private TextView text;
        private ImageView icon;
    }

    public ArrayList<TorifiedApp> getApps(SharedPreferences prefs) {

        String tordAppString = prefs.getString(PREFS_KEY_TORIFIED, "");

        String[] tordApps;

        StringTokenizer st = new StringTokenizer(tordAppString,"|");
        tordApps = new String[st.countTokens()];
        int tordIdx = 0;
        while (st.hasMoreTokens())
        {
            tordApps[tordIdx++] = st.nextToken();
        }
        Arrays.sort(tordApps);

        List<ApplicationInfo> lAppInfo = pMgr.getInstalledApplications(0);

        Iterator<ApplicationInfo> itAppInfo = lAppInfo.iterator();

        ArrayList<TorifiedApp> apps = new ArrayList<>();

        ApplicationInfo aInfo;

        TorifiedApp app;

        while (itAppInfo.hasNext())
        {
            aInfo = itAppInfo.next();
            // don't include apps user has disabled, often these ship with the device
            if (!aInfo.enabled) continue;
            app = new TorifiedApp();


            try {
                PackageInfo pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS);

                if (pInfo != null && pInfo.requestedPermissions != null)
                {
                    for (String permInfo:pInfo.requestedPermissions)
                    {
                        if (permInfo.equals(Manifest.permission.INTERNET))
                        {
                            app.setUsesInternet(true);
                        }
                    }

                }


            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            try
            {
                app.setName(pMgr.getApplicationLabel(aInfo).toString());
            }
            catch (Exception e)
            {
                // no name
                continue; //we only show apps with names
            }


            if (!app.getUsesInternet())
                continue;
            else
            {
                apps.add(app);
            }

            app.setEnabled(aInfo.enabled);
            app.setUid(aInfo.uid);
            app.setUsername(pMgr.getNameForUid(app.getUid()));
            app.setProcname(aInfo.processName);
            app.setPackageName(aInfo.packageName);


            // check if this application is allowed
            app.setTorified(Arrays.binarySearch(tordApps, app.getUsername()) >= 0);

        }

        Collections.sort(apps);

        return apps;
    }


    public void saveAppSettings() {

        StringBuilder tordApps = new StringBuilder();
        Intent response = new Intent();

        for (TorifiedApp tApp:mApps)
        {
            if (tApp.isTorified())
            {
                tordApps.append(tApp.getUsername());
                tordApps.append("|");
                response.putExtra(tApp.getUsername(),true);
            }
        }

        Editor edit = mPrefs.edit();
        edit.putString(PREFS_KEY_TORIFIED, tordApps.toString());
        edit.commit();

        setResult(RESULT_OK,response);
    }
}
