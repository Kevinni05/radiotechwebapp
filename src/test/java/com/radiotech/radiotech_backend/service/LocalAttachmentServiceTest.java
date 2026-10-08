package com.radiotech.radiotech_backend.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalAttachmentServiceTest {
 @TempDir Path folder;
 @Test void localFilesAreIdempotentTenantAndActorBoundAndDetectTampering()throws Exception{
  var files=new LocalAttachmentService(new MockEnvironment().withProperty("radiotech.local-files.directory",folder.toString()));
  byte[] bytes="private attachment".getBytes();var saved=files.save("tenant","tech","op-1","photo.txt",bytes);
  assertEquals(saved,files.save("tenant","tech","op-1","photo.txt",bytes));String reference=saved.get("reference").toString(),id=reference.substring("radiotech-file:".length());
  files.validateOwned(reference,"tenant","tech");assertThrows(IllegalArgumentException.class,()->files.validateOwned(reference,"other","tech"));
  assertThrows(SecurityException.class,()->files.read(id,"tenant","other-tech",false));assertThrows(SecurityException.class,()->files.read(id,"other","admin",true));
  assertEquals("photo.txt",files.read(id,"tenant","admin",true).get("name"));
  assertThrows(IllegalArgumentException.class,()->files.save("tenant","tech","op-1","photo.txt","different".getBytes()));
  Files.write(folder.resolve(id+".bin"),"tampered".getBytes());assertThrows(IllegalStateException.class,()->files.read(id,"tenant","tech",false));
 }
 @Test void productionSelectsPersistentFirestoreAndRejectsTraversal()throws Exception{
  var env=new MockEnvironment().withProperty("radiotech.local-files.directory",folder.toString());env.setActiveProfiles("production");var files=new LocalAttachmentService(env);
  assertFalse(files.enabled());assertEquals("FIRESTORE",files.storageBackend());
  var testFiles=new LocalAttachmentService(new MockEnvironment().withProperty("radiotech.local-files.directory",folder.toString()));
  assertThrows(IllegalArgumentException.class,()->testFiles.read("../secrets","t","u",true));
 }
}
