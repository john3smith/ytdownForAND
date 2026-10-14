package com.local.ytdown;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public class IncompleteFilesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private File file(File root,String name,int size) throws Exception {
        File f=new File(root,name);Files.write(f.toPath(),new byte[size]);return f;
    }
    @Test public void recognizesOnlyExplicitPartials() {
        for(String n:new String[]{"영상.mp4.part","x.ytdl","x.mp4.part-Frag12","x.part-Frag3.part","x.PART"}) assertTrue(n,IncompleteFiles.partial(n));
        for(String n:new String[]{"x.mp4","x.f137.mp4","x.temp.mp4","x.jpg","x.m4a","x.tmp","x.part.mp4","x.part-Fragx","x.json"}) assertFalse(n,IncompleteFiles.partial(n));
    }
    @Test public void scanAndDeletePreserveCompleteAndNestedFiles() throws Exception {
        File root=temp.newFolder("root");File done=file(root,"finished.mp4",0);File photo=file(root,"image.jpg",1);
        File nested=new File(root,"nested");assertTrue(nested.mkdir());File child=file(nested,"nested.part",2);
        File partial=file(root,"partial.mp4.part",8);List<IncompleteFiles.Item> found=IncompleteFiles.scan(root);
        assertEquals(1,found.size());assertTrue(IncompleteFiles.delete(found.get(0)));assertFalse(partial.exists());
        assertTrue(done.exists());assertTrue(photo.exists());assertTrue(child.exists());assertTrue(IncompleteFiles.scan(root).isEmpty());
    }
    @Test public void changedFileIsNotDeleted() throws Exception {
        File root=temp.newFolder("root");File f=file(root,"x.part",1);
        IncompleteFiles.Item item=IncompleteFiles.scan(root).get(0);Files.write(f.toPath(),new byte[2]);
        assertFalse(IncompleteFiles.delete(item));assertTrue(f.exists());
    }
    @Test public void missingFileIsSafeAndCompletedRenameSurvives() throws Exception {
        File root=temp.newFolder("root");File f=file(root,"x.part",1);
        IncompleteFiles.Item item=IncompleteFiles.scan(root).get(0);File done=new File(root,"x.mp4");assertTrue(f.renameTo(done));
        assertFalse(IncompleteFiles.delete(item));assertTrue(done.exists());
    }
    @Test public void rejectsOutsideRootAndCompletedFileEvenWithForgedSnapshot() throws Exception {
        File root=temp.newFolder("root");File outside=file(temp.getRoot(),"outside.part",5);
        assertFalse(IncompleteFiles.delete(new IncompleteFiles.Item(root,outside)));assertTrue(outside.exists());
        File done=file(root,"done.mp4",2);assertFalse(IncompleteFiles.delete(new IncompleteFiles.Item(root,done)));
    }
    @Test public void zeroBytePartialIsIncludedButZeroByteMediaIsNot() throws Exception {
        File root=temp.newFolder("root");file(root,"zero.part",0);file(root,"zero.mp4",0);
        assertEquals(1,IncompleteFiles.scan(root).size());
    }
    @Test public void emptyAndMissingDirectoriesAreEmpty() throws Exception {
        assertTrue(IncompleteFiles.scan(temp.newFolder("empty")).isEmpty());
        assertTrue(IncompleteFiles.scan(new File(temp.getRoot(),"missing")).isEmpty());
    }
}
