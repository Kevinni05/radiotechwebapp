package com.radiotech.radiotech_backend.service;

import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Tenant-authorized, immutable APK packages. Firestore is the no-billing test adapter. */
@Service
public class PrivateDistributionService {
    public static final long MAX_BYTES=150L*1024*1024;
    public Map<String,Object> latest(String tenant)throws Exception {
        var document=FirestoreClient.getFirestore().collection("privateAppReleases").document(tenant).get().get();
        if(!document.exists()||!Boolean.TRUE.equals(document.getBoolean("available")))return Map.of("available",false,"channel","PRIVATE_SITE");
        if(!tenant.equals(document.getString("tenantId")))throw new SecurityException("Canale non autorizzato.");
        var data=new LinkedHashMap<String,Object>();
        for(String field:List.of("applicationId","versionName","versionCode","signingSha256","sha256","filename","size","publishedAt"))data.put(field,document.get(field));
        validate(data);data.put("available",true);data.put("channel","PRIVATE_SITE");return data;
    }
    static void validate(Map<String,Object> data){
        if(!(data.get("applicationId") instanceof String app)||!app.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||!(data.get("versionName") instanceof String versionName)||versionName.isBlank()||versionName.length()>80)throw new IllegalStateException("Identità release non valida.");
        for(String field:List.of("sha256","signingSha256"))if(!(data.get(field) instanceof String value)||!value.matches("[a-f0-9]{64}"))throw new IllegalStateException("Metadati della release non validi.");
        if(!(data.get("filename") instanceof String name)||!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,150}\\.apk"))throw new IllegalStateException("Nome release non valido.");
        if(!(data.get("size") instanceof Number size)||size.longValue()<1||size.longValue()>MAX_BYTES||size.doubleValue()!=size.longValue()||!(data.get("versionCode") instanceof Number version)||version.longValue()<1||version.longValue()>2100000000||version.doubleValue()!=version.longValue())throw new IllegalStateException("Release non valida.");
    }
    public record Download(Path file,String filename,long size,String digest) implements AutoCloseable {
        public void close()throws java.io.IOException{Files.deleteIfExists(file);}
    }
    public Download prepare(String tenant,String filename)throws Exception {
        var release=latest(tenant);
        if(!Boolean.TRUE.equals(release.get("available"))||!filename.equals(release.get("filename")))throw new IllegalArgumentException("Release non disponibile.");
        String sha=release.get("sha256").toString();long expected=((Number)release.get("size")).longValue();
        var db=FirestoreClient.getFirestore();var packageRef=db.collection("privateAppPackages").document(sha);var metadata=packageRef.get().get();
        Long count=metadata.getLong("chunkCount");
        if(!metadata.exists()||!"READY".equals(metadata.getString("state"))||count==null||count<1||count>300||!sha.equals(metadata.getString("sha256"))||!Long.valueOf(expected).equals(metadata.getLong("size")))throw new IllegalStateException("Pacchetto non disponibile.");
        Path stage=Files.createTempFile("radiotech-private-apk-",".partial");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(var output=Files.newOutputStream(stage)){
                for(int index=0;index<count;index++){
                    var chunk=packageRef.collection("chunks").document(String.format(Locale.ROOT,"%06d",index)).get().get();var blob=chunk.getBlob("data");
                    if(!chunk.exists()||blob==null)throw new IllegalStateException("Pacchetto incompleto.");byte[] bytes=blob.toBytes();
                    if(bytes.length<1||bytes.length>512*1024||size+bytes.length>expected)throw new IllegalStateException("Dimensione pacchetto non valida.");
                    output.write(bytes);digest.update(bytes);size+=bytes.length;
                }
            }
            if(size!=expected||!MessageDigest.isEqual(HexFormat.of().formatHex(digest.digest()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),sha.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IllegalStateException("Integrità pacchetto non valida.");
            return new Download(stage,filename,size,sha);
        }catch(Exception error){Files.deleteIfExists(stage);throw error;}
    }
}
