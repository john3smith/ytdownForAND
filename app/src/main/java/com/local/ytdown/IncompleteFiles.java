package com.local.ytdown;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Only explicit downloader partials; never infer failure from size, age or a media suffix. */
final class IncompleteFiles {
    static final Object IO_LOCK = new Object();
    static final class Item {
        final File root, file;
        final long size, modified;
        Item(File root, File file) { this.root=root; this.file=file; size=file.length(); modified=file.lastModified(); }
    }
    static boolean partial(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        return n.endsWith(".part") || n.endsWith(".ytdl") || n.matches(".+\\.part-frag[0-9]+(?:\\.part)?");
    }
    static boolean contained(File root, File file) throws IOException {
        // Android's trusted app root may use /data/user/0 -> /data/data aliases.
        // Normalize that root, but reject redirected children and nested paths.
        return root.getAbsoluteFile().equals(file.getAbsoluteFile().getParentFile())
                && new File(root.getCanonicalFile(),file.getName()).equals(file.getCanonicalFile())
                && root.getCanonicalFile().equals(file.getCanonicalFile().getParentFile());
    }
    static List<Item> scan(File root) throws IOException {
        List<Item> result=new ArrayList<>();
        if (!root.exists()) return result;
        File[] files=root.listFiles();
        if (files==null) throw new IOException("Directory unavailable");
        for(File file:files) if(partial(file.getName()) && contained(root,file) && file.isFile())
            result.add(new Item(root,file));
        return result;
    }
    static boolean delete(Item item) throws IOException {
        File f=item.file;
        if(!partial(f.getName()) || !contained(item.root,f) || !f.isFile()
                || f.length()!=item.size || f.lastModified()!=item.modified) return false;
        return f.delete();
    }
}
