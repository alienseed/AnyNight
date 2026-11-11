package com.local.anynight;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 最小的 FileProvider。
 *
 * 只做一件事：把 cache/apk 下的安装包以 content:// 交给系统安装器。
 * 不引入 androidx（本应用只要 Shizuku 一个依赖），所以自己写这三十行。
 * exported=false + grantUriPermissions，只有拿到临时授权的安装器能读。
 */
public class ApkProvider extends ContentProvider {

    static final String AUTHORITY_SUFFIX = ".apk";
    static final String DIR = "apk";

    static Uri uriFor(Context c, File f) {
        return new Uri.Builder().scheme("content")
                .authority(c.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(f.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = null;
        try {
            f = resolve(uri);
        } catch (FileNotFoundException ignored) {
        }
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = f == null ? uri.getLastPathSegment() : f.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = f == null ? 0 : f.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || getContext() == null) throw new FileNotFoundException("bad uri");
        File f = new File(new File(getContext().getCacheDir(), DIR), name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return f;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
