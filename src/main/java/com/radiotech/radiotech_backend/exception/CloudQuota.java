package com.radiotech.radiotech_backend.exception;
/** Detect provider quota failures without exposing provider or credential details. */
public final class CloudQuota {
 private CloudQuota() {}
 public static final String MESSAGE="Quota del database cloud temporaneamente esaurita. Riprova dopo il ripristino della quota.";
 public static boolean exhausted(Throwable error) {
  for(int depth=0;error!=null&&depth<16;depth++,error=error.getCause()) {
   if(error instanceof com.google.api.gax.rpc.ResourceExhaustedException)return true;
   if(error instanceof io.grpc.StatusRuntimeException status&&status.getStatus().getCode()==io.grpc.Status.Code.RESOURCE_EXHAUSTED)return true;
  }
  return false;
 }
}
