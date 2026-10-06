package com.radiotech.radiotech_backend.service;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private local attachment adapter for the free test; production uses cloud storage. */
@Service
public class LocalAttachmentService {
 public static final int MAX_BYTES=10_000_000;
 private final boolean enabled;
 private final Path root;
 private final JsonMapper json=JsonMapper.builder().build();
 public LocalAttachmentService(Environment env){enabled=!env.acceptsProfiles(Profiles.of("production"));root=Path.of(env.getProperty("radiotech.local-files.directory",".dist/private-files")).toAbsolutePath().normalize();}
 public boolean enabled(){return enabled;}
 private void requireEnabled(){if(!enabled)throw new IllegalArgumentException("Storage locale disponibile solo nel test.");}
 private Path path(String id,String suffix){if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Allegato non valido.");return root.resolve(id+suffix);}
 public synchronized Map<String,Object> save(String tenant,String uid,String operation,String name,byte[] data)throws Exception{
  requireEnabled();if(tenant==null||uid==null)throw new SecurityException("Identità richiesta.");
  if(operation==null||!operation.matches("[A-Za-z0-9_-]{1,160}")||name==null||name.isBlank()||name.length()>160||data.length==0||data.length>MAX_BYTES)throw new IllegalArgumentException("Allegato non valido: limite 10 MB.");
  String safeName=name.replaceAll("[\\\\/\\p{Cntrl}]","_");String id=hash((tenant+":"+uid+":"+operation).getBytes(StandardCharsets.UTF_8)),digest=hash(data);
  Files.createDirectories(root);Path metadata=path(id,".json"),binary=path(id,".bin");
  if(Files.exists(metadata)){var previous=json.readValue(Files.readAllBytes(metadata),Map.class);if(!digest.equals(previous.get("digest")))throw new IllegalArgumentException("Operazione allegato già usata per un altro file.");return Map.of("reference","radiotech-file:"+id,"name",previous.get("name"));}
  var record=Map.of("tenantId",tenant,"uid",uid,"name",safeName,"digest",digest,"size",data.length);
  Path temp=Files.createTempFile(root,"upload-",".partial");try{Files.write(temp,data);Files.move(temp,binary,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}
  Path metaTemp=Files.createTempFile(root,"metadata-",".partial");try{Files.write(metaTemp,json.writeValueAsBytes(record));Files.move(metaTemp,metadata,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(metaTemp);}
  return Map.of("reference","radiotech-file:"+id,"name",safeName);
 }
 public Map<String,Object> read(String id,String tenant,String uid,boolean manager)throws Exception{
  requireEnabled();var record=metadata(id);if(!tenant.equals(record.get("tenantId"))||(!manager&&!uid.equals(record.get("uid"))))throw new SecurityException("Allegato non autorizzato.");
  byte[] data=Files.readAllBytes(path(id,".bin"));if(data.length>MAX_BYTES||!hash(data).equals(record.get("digest")))throw new IllegalStateException("Integrità allegato non valida.");
  return Map.of("name",record.get("name"),"base64",Base64.getEncoder().encodeToString(data));
 }
 public void validateOwned(String reference,String tenant,String uid){try{requireEnabled();var record=metadata(reference.substring("radiotech-file:".length()));if(!tenant.equals(record.get("tenantId"))||!uid.equals(record.get("uid")))throw new SecurityException("Allegato non autorizzato.");}catch(Exception e){throw new IllegalArgumentException("Allegato locale non valido per tenant e operatore.");}}
 private Map<String,Object> metadata(String id)throws Exception{Path file=path(id,".json");if(!Files.exists(file))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Allegato non trovato.");return json.readValue(Files.readAllBytes(file),Map.class);}
 private static String hash(byte[] data)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));}
}
