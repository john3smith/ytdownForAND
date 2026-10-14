package com.local.ytdown;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class IncompleteFileStore {
    static final class Entry {
        final String name, location;
        final long size, modified;
        final IncompleteFiles.Item file;
        final Uri uri;
        Entry(IncompleteFiles.Item f, String location) {
            file=f;uri=null;name=f.file.getName();size=f.size;modified=f.modified;this.location=location;
        }
        Entry(Uri uri,String name,long size,long modified) {
            file=null;this.uri=uri;this.name=name;this.size=size;this.modified=modified;location="다운로드 저장 미완료";
        }
    }
    static final class Listing {
        final List<Entry> entries=new ArrayList<>();
        int unavailable;
    }
    private static final String PATH="Download/YTDown/";
    static Listing scan(Context context) {
        Listing out=new Listing();
        File[] roots={new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"YTDown"),
                new File(context.getCacheDir(),"stream-downloads")};
        for(int i=0;i<roots.length;i++) try {
            for(IncompleteFiles.Item f:IncompleteFiles.scan(roots[i])) out.entries.add(new Entry(f,i==0?"Download/YTDown":"앱 임시 저장소"));
        } catch(Exception e) { out.unavailable++; }
        if(Build.VERSION.SDK_INT>=29) try {
            Uri collection=MediaStore.setIncludePending(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY));
            String[] columns={"_id","_display_name","_size","date_modified"};
            String where="is_pending=1 AND owner_package_name=? AND relative_path=?";
            try(Cursor c=context.getContentResolver().query(collection,columns,where,new String[]{context.getPackageName(),PATH},null)) {
                if(c==null) throw new IllegalStateException("Query unavailable");
                while(c.moveToNext()) out.entries.add(new Entry(ContentUris.withAppendedId(collection,c.getLong(0)),
                        c.getString(1),Math.max(0,c.getLong(2)),c.getLong(3)*1000));
            }
        } catch(Exception e) { out.unavailable++; }
        out.entries.sort(Comparator.comparingLong((Entry e)->e.modified).reversed().thenComparing(e->e.name));
        return out;
    }
    static boolean delete(Context context,Entry item) {
        try {
            if(item.file!=null) return IncompleteFiles.delete(item.file);
            if(Build.VERSION.SDK_INT<29) return false;
            // Recheck owner/path/pending state AND snapshot. Never delete a newly completed item.
            return context.getContentResolver().delete(item.uri,
                    "is_pending=1 AND owner_package_name=? AND relative_path=? AND _display_name=? AND _size=? AND date_modified=?",
                    new String[]{context.getPackageName(),PATH,item.name,Long.toString(item.size),Long.toString(item.modified/1000)})==1;
        } catch(Exception e) { return false; }
    }
}
