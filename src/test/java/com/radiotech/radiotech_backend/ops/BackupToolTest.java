package com.radiotech.radiotech_backend.ops;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class BackupToolTest{
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
 @Test void localAttachmentsCanBeRecoveredWithoutPathTraversal()throws Exception{
  var source=directory.resolve("source");java.nio.file.Files.createDirectories(source);String name="a".repeat(64)+".bin";byte[] data="photo backup".getBytes();java.nio.file.Files.write(source.resolve(name),data);
  var files=BackupTool.snapshotLocalFiles(source);var restored=directory.resolve("restored");BackupTool.restoreLocalFiles(files,restored);assertArrayEquals(data,java.nio.file.Files.readAllBytes(restored.resolve(name)));
  assertThrows(IllegalArgumentException.class,()->BackupTool.restoreLocalFiles(List.of(Map.of("name","../secret","data","")),restored));
 }
 @Test void encryptedBackupsRejectTamperingAndWrongKey()throws Exception{byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);byte[] plain="private backup".getBytes();byte[] encrypted=BackupTool.encrypt(plain,key);assertArrayEquals(plain,BackupTool.decrypt(encrypted,key));encrypted[16]^=1;assertThrows(Exception.class,()->BackupTool.decrypt(encrypted,key));assertThrows(Exception.class,()->BackupTool.decrypt(BackupTool.encrypt(plain,key),new byte[32]));}
 @Test void typedCodecPreservesNestedDataAndLargeInteger(){var data=new LinkedHashMap<String,Object>();data.put("null",null);data.put("large",Long.MAX_VALUE);data.put("nested",List.of(Map.of("_rt_type","user data"),true,2.5));assertEquals(data,BackupTool.decode(BackupTool.encode(data),null));}
}
