package com.rubenwardy.minetestmodmanager.manager;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Mod utility library
 */
class Utils {
    public interface UnzipFile_Progress
    {
        void Progress(long done, long total, String FileName);
    }

    @CheckResult
    public static boolean UnzipFile(@NonNull File zipFile, File targetDirectory, @Nullable UnzipFile_Progress progress)
            throws IOException {
        long total_len = zipFile.length();
        long total_installed_len = 0;

        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                if (progress != null) {
                    total_installed_len += ze.getCompressedSize();
                    String file_name = ze.getName();
                    progress.Progress(total_installed_len, total_len, file_name);
                }

                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                Log.w("Utils", "Extracting " + file.getAbsolutePath());
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            zis.close();
        }
        return true;
    }

    @Nullable
    public static String readTextFile(@Nullable File file) {
        if (file == null) {
            return null;
        }

        try {
            StringBuilder text = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
            return text.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void deleteRecursive(@NonNull File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            for (File child : fileOrDir.listFiles()) {
                deleteRecursive(child);
            }
        }
        if (fileOrDir.delete())
            Log.w("utils", "Deleted path: " + fileOrDir.getAbsolutePath());
        else
            Log.w("utils", "Failed to delete path: " + fileOrDir.getAbsolutePath());
    }

    public static boolean copyFolder(@NonNull File src, @NonNull File dest)
            throws IOException{

        if (src.isDirectory()) {
            if (!dest.exists()) {
                if (dest.mkdir()) {
                    System.out.println("Directory copied from "
                            + src + "  to " + dest);
                } else {
                    return false;
                }
            }

            for (String file : src.list()) {
                File srcFile = new File(src, file);
                File destFile = new File(dest, file);

                if (!copyFolder(srcFile,destFile))
                    return false;
            }
        } else {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];

            int length;
            while ((length = in.read(buffer)) > 0){
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
            System.out.println("File copied from " + src + " to " + dest);
        }

        return true;
    }

    @NonNull
    public static Mod.ModType detectModType(@NonNull File file) {
        if (new File(file.getAbsolutePath(), "init.lua").exists()) {
            Log.w("ModLib", "Found mod at " + file.getName());
            return Mod.ModType.EMT_MOD;
        } else if (new File(file.getAbsolutePath(), "modpack.txt").exists()) {
            Log.w("ModLib", "Found modpack at " + file.getName());
            return Mod.ModType.EMT_MODPACK;
        } else {
            Log.w("ModLib", "Found invalid directory at " + file.getName());
            return Mod.ModType.EMT_INVALID;
        }
    }

    @Nullable
    public static File findRootDir(@NonNull File dir) {
        if (!dir.isDirectory())
            return null;

        Log.w("Utils", "- Finding root dir");

        Mod.ModType type = detectModType(dir);
        if (type == Mod.ModType.EMT_INVALID) {
            Log.w("Utils", "- Invalid dir for mod location.");
            File subdir = null;
            for (File child : dir.listFiles()) {
                if (child.isDirectory()) {
                    Log.w("Utils", "- Found subdir.");
                    if (subdir == null) {
                        subdir = child;
                    } else {
                        Log.w("Utils", "- Two or more subdirs, cannot descend logically.");
                        return null;
                    }
                }
            }
            if (subdir == null) {
                Log.w("Utils", "- Find root failed: no valid subdir.");
                return null;
            } else {
                return findRootDir(subdir);
            }
        } else {
            Log.w("Utils", "- Found valid root: " + dir.getAbsoluteFile());
            return dir;
        }
    }
}
