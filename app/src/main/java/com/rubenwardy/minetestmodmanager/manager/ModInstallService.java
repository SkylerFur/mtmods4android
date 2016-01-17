package com.rubenwardy.minetestmodmanager.manager;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Mod manager service.
 */
public class ModInstallService extends IntentService {
    public static final int UPDATE_PROGRESS = 8344;
    private static final String ACTION_URL_INSTALL = "com.rubenwardy.minetestmodmanager.action.URL_INSTALL";
    private static final String ACTION_INSTALL = "com.rubenwardy.minetestmodmanager.action.INSTALL";

    private static final String EXTRA_MOD_NAME = "com.rubenwardy.minetestmodmanager.extra.MOD_NAME";
    private static final String EXTRA_URL = "com.rubenwardy.minetestmodmanager.extra.URL";
    private static final String EXTRA_DEST = "com.rubenwardy.minetestmodmanager.extra.DEST";
    public static final String RET_NAME = "name";
    public static final String RET_DEST = "dest";
    public static final String RET_ERROR = "error";
    public static final String RET_PROGRESS = "progress";

    public ModInstallService() {
        super("ModInstallService");
    }

    public static void startActionInstall(Context context, ServiceResultReceiver srr,
            String modname, File zip, String dest) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_INSTALL);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_URL, zip.getAbsolutePath());
        intent.putExtra(EXTRA_DEST, dest);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }

    public static void startActionUrlInstall(Context context, ServiceResultReceiver srr,
                                          String modname, String url, String dest) {
        Intent intent = new Intent(context, ModInstallService.class);
        intent.setAction(ACTION_URL_INSTALL);
        intent.putExtra(EXTRA_MOD_NAME, modname);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_DEST, dest);
        intent.putExtra("receiverTag", srr);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            ResultReceiver rec = intent.getParcelableExtra("receiverTag");

            if (ACTION_INSTALL.equals(action)) {
                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String zippath = intent.getStringExtra(EXTRA_URL);
                final String dest = intent.getStringExtra(EXTRA_DEST);
                handleActionInstall(rec, modname, new File(zippath), new File(dest));
            } else if (ACTION_URL_INSTALL.equals(action)) {
                final String modname = intent.getStringExtra(EXTRA_MOD_NAME);
                final String url = intent.getStringExtra(EXTRA_URL);
                final String dest = intent.getStringExtra(EXTRA_DEST);
                handleActionUrlInstall(rec, modname, url, new File(dest));
            } else {
                Log.w("ModService", "Invalid action request.");
            }
        }
    }

    private void handleActionUrlInstall(ResultReceiver rec, String modname, String url_str, File dest) {
        Log.w("ModService", "Downloading file..");
        try {
            URL url = new URL(url_str);
            URLConnection connection = url.openConnection();
            connection.connect();
            // this will be useful so that you can show a typical 0-100% progress bar
            int fileLength = connection.getContentLength();

            File file = null;
            int i = 0;
            do {
                Log.w("ModService", "Checking file tmp" + Integer.toString(i) + ".zip");
                file = new File(getCacheDir(), "tmp" + Integer.toString(i) + ".zip");
                i++;
            } while (file.exists());

            // download the file
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(file);

            byte data[] = new byte[1024];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                // publishing the progress....
                Bundle b = new Bundle();
                b.putString(RET_NAME, modname);
                b.putString(RET_DEST, dest.getAbsolutePath());
                b.putInt(RET_PROGRESS, (int) (total * 100 / fileLength));
                rec.send(UPDATE_PROGRESS, b);
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();

            handleActionInstall(rec, modname, file.getAbsoluteFile(), dest);
        } catch (IOException e) {
            Bundle b = new Bundle();
            b.putString(RET_NAME, modname);
            b.putString(RET_DEST, dest.getAbsolutePath());
            b.putString(RET_ERROR, e.toString());
            rec.send(0, b);
            e.printStackTrace();
        }
    }

    /**
     * Handle action Install in the provided background thread with the provided
     * parameters.
     */
    private void handleActionInstall(ResultReceiver rec, String modname, File zipfile, File dest) {
        Log.w("ModService", "Installing mod...");

        File dir = null;
        int i = 0;
        do {
            Log.w("ModService", "Checking tmp" + Integer.toString(i));
            dir = new File(getCacheDir(), "tmp" + Integer.toString(i));
            i++;
        } while (dir.exists());

        try {
            Utils.UnzipFile(zipfile, dir, null);

            Log.w("ModService", "Finding root dir:");
            File root = Utils.findRootDir(dir);
            if (root == null) {
                Log.w("ModService", "Unable to find root dir.");
            } else {
                Log.w("ModService", "Copying to " + dest.getAbsolutePath());
                Utils.copyFolder(root, new File(dest, modname));

                Bundle b = new Bundle();
                b.putString(RET_NAME, modname);
                b.putString(RET_DEST, dest.getAbsolutePath());
                rec.send(0, b);
            }
        } catch (IOException e) {
            Bundle b = new Bundle();
            b.putString(RET_NAME, modname);
            b.putString(RET_DEST, dest.getAbsolutePath());
            b.putString(RET_ERROR, e.toString());
            rec.send(0, b);
            e.printStackTrace();
        }
        Log.w("ModService", "Finished installing mod.");
    }
}
