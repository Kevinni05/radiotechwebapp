package com.radiotech.radiotech_backend.service;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.async.*;
import org.springframework.web.context.request.NativeWebRequest;
import java.util.concurrent.Callable;
/** Release staged bytes and permits even when async handling is cancelled before streaming begins. */
public final class AsyncDownloadLease implements AutoCloseable {
    private final AutoCloseable[] resources;
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    private AsyncDownloadLease(AutoCloseable[] resources){this.resources=resources;}
    public static AsyncDownloadLease bind(HttpServletRequest request,AutoCloseable... resources){
        var lease=new AsyncDownloadLease(resources);
        if(request!=null)WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(lease,new CallableProcessingInterceptor(){
            @Override public <T> void afterCompletion(NativeWebRequest request,Callable<T> task)throws Exception{lease.close();}
        });return lease;
    }
    @Override public void close()throws Exception{
        if(!closed.compareAndSet(false,true))return;Exception failure=null;
        for(var resource:resources)try{resource.close();}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure!=null)throw failure;
    }
}
