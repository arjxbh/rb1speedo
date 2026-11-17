package club.midlife.rb1speed;

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.MediaCodec;
import android.os.*;
import android.util.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Single-lens discovery and visible preview probes. Never records or changes lens preferences. */
final class CameraDiagnostics {
    interface Listener {
        void line(String text);
        void idle();
        void lenses(List<CameraCatalog.Option> lenses);
        void frame(Bitmap bitmap);
    }
    private static int engines;
    private static synchronized void acquire() { engines++; DashcamService.diagnosticsActive=true; }
    private static synchronized void release() { engines--; DashcamService.diagnosticsActive=engines>0; }
    private static synchronized boolean previousEngineClosing() { return engines>1; }
    private final Context context;
    private final Listener listener;
    private final CameraManager manager;
    private final HandlerThread thread=new HandlerThread("lens-discovery");
    private final Handler work;
    private final LinkedHashMap<String,CameraCatalog.Option> routes=new LinkedHashMap<String,CameraCatalog.Option>();
    private volatile boolean closed;
    private volatile int generation;
    private boolean active, leaseReleased;
    private int openings;
    private long began;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CameraPreviewFrames preview;
    private final AtomicInteger frames=new AtomicInteger();
    CameraDiagnostics(Context context,Listener listener) {
        acquire(); this.context=context; this.listener=listener;
        manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        thread.start(); work=new Handler(thread.getLooper());
    }
    private void log(String line) { if(!closed) listener.line(line); }
    private boolean valid(int token) { return !closed && active && token==generation; }
    void run(final String method) {
        work.post(new Runnable(){public void run(){
            if(closed) return;
            cancelInternal(false); active=true; final int token=++generation; began=SystemClock.elapsedRealtime();
            log("\n=== "+method+" · "+new Date()+" ===");
            ready(token,new Runnable(){public void run(){
                try {
                    if(method.equals("Scan rear lenses")) inventory(false);
                    else if(method.equals("Probe camera IDs 0–9")) inventory(true);
                    else if(method.equals("Package access info")) packageInfo();
                    finish("DONE: "+method,token);
                } catch(Exception e) { finish("FAIL: "+error(e),token); }
            }});
        }});
    }
    private void ready(final int token,final Runnable task) {
        if(!valid(token)) return;
        if(DashcamService.diagnosticsBusy() || previousEngineClosing() || openings>0) {
            if(SystemClock.elapsedRealtime()-began>=10000) { finish("FAIL: previous camera has not closed; stop recording and retry.",token); return; }
            work.postDelayed(new Runnable(){public void run(){ready(token,task);}},100); return;
        }
        task.run();
    }
    private void inventory(boolean probe) throws Exception {
        log("Package: "+context.getPackageName());
        log("Device: "+Build.MANUFACTURER+" "+Build.MODEL+" / "+Build.DEVICE);
        log("Android: "+Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT+" security "+Build.VERSION.SECURITY_PATCH);
        log("Build: "+Build.DISPLAY+"\nFingerprint: "+Build.FINGERPRINT);
        String[] exposed=manager.getCameraIdList(); Arrays.sort(exposed);
        log("Exposed camera IDs: "+Arrays.toString(exposed));
        if(!probe) routes.clear();
        LinkedHashSet<String> ids=new LinkedHashSet<String>(Arrays.asList(exposed));
        if(probe) for(int i=0;i<=9;i++) ids.add(Integer.toString(i));
        for(String id:ids) {
            try {
                CameraCharacteristics c=manager.getCameraCharacteristics(id);
                boolean listed=Arrays.asList(exposed).contains(id);
                describe(id,c,listed ? "exposed" : "unlisted metadata; opening not yet verified");
                addRoutes(id,null,c);
                if(Build.VERSION.SDK_INT>=29) for(String physical:c.getPhysicalCameraIds()) {
                    try {
                        CameraCharacteristics p=manager.getCameraCharacteristics(physical);
                        describe(physical,p,"physical via logical camera "+id);
                        addRoutes(id,physical,p);
                    } catch(Exception e) { log("  Physical "+physical+": "+error(e)); }
                }
            } catch(Exception e) { log("Camera "+id+": unavailable · "+error(e)); }
        }
        ArrayList<CameraCatalog.Option> ordered=new ArrayList<CameraCatalog.Option>(routes.values());
        Collections.sort(ordered,new Comparator<CameraCatalog.Option>(){public int compare(CameraCatalog.Option a,CameraCatalog.Option b){int field=CameraLens.compare(a.field,b.field);return field!=0 ? field : a.key.compareTo(b.key);}});
        listener.lenses(ordered);
        log("Rear preview candidates: "+ordered.size()+" (widest estimated field first)");
        for(CameraCatalog.Option option:ordered) log("  "+option.key+" · "+option.label());
        log("An unlisted ID with metadata is not proof it can open. Preview it to test access; identify the physical lens by its view or by covering lenses one at a time. No saved camera choice is changed.");
    }
    private void addRoutes(String id,String physical,CameraCharacteristics c) {
        Integer facing=c.get(CameraCharacteristics.LENS_FACING);
        StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(facing==null || facing!=CameraCharacteristics.LENS_FACING_BACK || map==null || CameraPreviewFrames.choose(map.getOutputSizes(ImageFormat.YUV_420_888))==null) return;
        CameraCatalog.Option option=new CameraCatalog.Option(id,physical,c,1f); routes.put(option.key,option);
        if(physical==null && Build.VERSION.SDK_INT>=30) {
            Range<Float> zoom=c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if(zoom!=null && zoom.getLower()>0 && zoom.getLower()<1f) {
                option=new CameraCatalog.Option(id,null,c,zoom.getLower()); routes.put(option.key,option);
            }
        }
    }
    private void describe(String id,CameraCharacteristics c,String source) {
        CameraCatalog.Option lens=new CameraCatalog.Option(id,null,c,1f);
        log("Camera "+id+" ("+source+") facing="+c.get(CameraCharacteristics.LENS_FACING)+" [0=front,1=rear,2=external] level="+c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
        log("  focal="+Arrays.toString(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))+" sensor="+c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)+" estimated horizontal field="+lens.field+"°");
        log("  physical="+(Build.VERSION.SDK_INT>=28 ? c.getPhysicalCameraIds() : "unavailable")+" zoom="+(Build.VERSION.SDK_INT>=30 ? c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) : "API unavailable")+" max digital zoom="+c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
        StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(map!=null) log("  encoder="+Arrays.toString(map.getOutputSizes(MediaCodec.class))+"\n  YUV="+Arrays.toString(map.getOutputSizes(ImageFormat.YUV_420_888)));
        log("  fps="+Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));
    }
    private void packageInfo() {
        log("Installed package: "+context.getPackageName());
        log("Some OnePlus firmware filters auxiliary cameras by app package. An app cannot change its installed package at runtime.");
        log("RB1 Speed uses org.codeaurora.snapcam by default, the identity that exposed the ultrawide lens on HD1925_11_F.05. RB1-Speed.apk updates the previous Wide app in place. The legacy club.midlife.rb1speed app has separate settings and footage. No privileged/system access is granted.");
        for(String key:new String[]{"vendor.camera.aux.packagelist","persist.vendor.camera.privapp.list","persist.camera.privapp.list"}) {
            try {
                java.lang.Process process=new ProcessBuilder("/system/bin/getprop",key).redirectErrorStream(true).start();
                StringBuilder result=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line; while((line=reader.readLine())!=null && result.length()<4096) result.append(line).append(' ');
                }
                process.waitFor(); log(key+" = "+(result.toString().trim().isEmpty() ? "empty or inaccessible; not evidence of no restriction" : result.toString().trim()));
            } catch(Exception e) { log(key+": unreadable · "+error(e)); }
        }
        log("No root, system properties, firmware, or camera allowlists are changed. Package probing is experimental; exposing a lens does not establish recording support.");
    }
    void preview(final CameraCatalog.Option option) {
        work.post(new Runnable(){public void run(){
            if(closed) return;
            cancelInternal(false); active=true; final int token=++generation; began=SystemClock.elapsedRealtime(); frames.set(0);
            log("\n=== Preview lens · "+option.key+" · "+option.label()+" ===");
            ready(token,new Runnable(){public void run(){open(option,token);}});
        }});
    }
    private void open(final CameraCatalog.Option option,final int token) {
        work.postDelayed(new Runnable(){public void run(){if(valid(token) && frames.get()==0) finish("FAIL: camera open/configuration/frame timeout (12 seconds)",token);}},12000);
        openings++;
        try {
            manager.openCamera(option.openId,new CameraDevice.StateCallback(){
                private boolean pending=true;
                private void settled() { if(pending){pending=false;openings--;} if(closed && openings==0) endLease(); }
                public void onOpened(CameraDevice device) {
                    if(!valid(token)) { device.close(); settled(); return; }
                    settled(); camera=device; log("OPENED "+option.openId);
                    try { configure(option,token); } catch(Exception e) { finish("FAIL configure: "+error(e),token); }
                }
                public void onDisconnected(CameraDevice device) { device.close(); settled(); if(valid(token)) finish("FAIL: camera disconnected",token); }
                public void onError(CameraDevice device,int code) { device.close(); settled(); if(valid(token)) finish("FAIL open: camera error="+code,token); }
            },work);
        } catch(Exception e) { openings--; finish("FAIL open: "+error(e),token); }
    }
    private void configure(final CameraCatalog.Option option,final int token) throws Exception {
        StreamConfigurationMap map=option.characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size size=CameraPreviewFrames.choose(map.getOutputSizes(ImageFormat.YUV_420_888));
        int rotation=((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        int orientation=(option.characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)-rotation*90+360)%360;
        preview=new CameraPreviewFrames(size,orientation,work);
        preview.listen(new CameraPreviewFrames.Listener(){public void frame(Bitmap bitmap){
            if(!valid(token)) { bitmap.recycle(); return; }
            frames.incrementAndGet(); listener.frame(bitmap);
        }});
        OutputConfiguration output=new OutputConfiguration(preview.surface());
        if(Build.VERSION.SDK_INT>=28 && option.physicalId!=null) output.setPhysicalCameraId(option.physicalId);
        camera.createCaptureSessionByOutputConfigurations(Collections.singletonList(output),new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession capture) {
                if(!valid(token)){capture.close();return;} session=capture;
                try {
                    CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); request.addTarget(preview.surface());
                    request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                    if(option.physicalId==null) {
                        if(Build.VERSION.SDK_INT>=30 && option.characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)!=null) request.set(CaptureRequest.CONTROL_ZOOM_RATIO,option.zoom);
                        else if(Float.isFinite(option.focal) && option.characteristics.getAvailableCaptureRequestKeys().contains(CaptureRequest.LENS_FOCAL_LENGTH)) request.set(CaptureRequest.LENS_FOCAL_LENGTH,option.focal);
                    }
                    session.setRepeatingRequest(request.build(),null,work);
                    log("CONFIGURED · zoom="+option.zoom+" · preview "+size+" · showing 8 seconds");
                    work.postDelayed(new Runnable(){public void run(){if(valid(token)) finish((frames.get()>=2 ? "PASS" : "FAIL: no usable preview")+" · frames="+frames.get()+" · "+option.label(),token);}},8000);
                } catch(Exception e) { finish("FAIL capture: "+error(e),token); }
            }
            public void onConfigureFailed(CameraCaptureSession capture){capture.close();if(valid(token))finish("FAIL: preview configuration rejected",token);}
        },work);
    }
    private void cleanup() {
        if(session!=null) { try{session.close();}catch(Exception ignored){} session=null; }
        if(camera!=null) { try{camera.close();}catch(Exception ignored){} camera=null; }
        if(preview!=null) { preview.listen(null); preview.close(); preview=null; }
    }
    private void finish(String result,int token) { if(!valid(token))return; active=false; generation++; cleanup(); log(result); listener.idle(); }
    private void cancelInternal(boolean report) { active=false; generation++; cleanup(); if(report){log("STOPPED: lens probe cancelled; camera released.");listener.idle();} }
    void cancel(){work.post(new Runnable(){public void run(){cancelInternal(true);}});}
    private void endLease(){if(!leaseReleased){leaseReleased=true;release();}thread.quitSafely();}
    void close() {
        closed=true;final CountDownLatch done=new CountDownLatch(1);
        work.post(new Runnable(){public void run(){try{cancelInternal(false);}finally{done.countDown();if(openings==0)endLease();else work.postDelayed(new Runnable(){public void run(){endLease();}},15000);}}});
        try{done.await(500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private static String error(Exception e){return e.getClass().getSimpleName()+": "+e.getMessage();}
}
