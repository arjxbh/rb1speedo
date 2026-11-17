package club.midlife.rb1speed;

import android.content.*;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.*;
import android.media.*;
import android.os.*;
import android.opengl.*;
import android.util.*;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Bounded, foreground-only probes. Test footage stays in cache and is deleted after each probe. */
final class CameraDiagnostics {
    interface Listener { void line(String text); void idle(); }
    private static int engines;
    private static synchronized void acquire() { engines++; DashcamService.diagnosticsActive=true; }
    private static synchronized void release() { engines--; DashcamService.diagnosticsActive=engines>0; }
    private static synchronized boolean previousEngineClosing() { return engines>1; }
    private final Context context;
    private final Listener listener;
    private final CameraManager manager;
    private final HandlerThread thread = new HandlerThread("camera-diagnostics");
    private final Handler work;
    private final ArrayDeque<Probe> queue = new ArrayDeque<Probe>();
    private final ArrayList<Endpoint> endpoints = new ArrayList<Endpoint>();
    private Probe current;
    private int generation;
    private volatile boolean closed;
    private boolean legacy;
    private int openings;
    private boolean leaseReleased;
    private EGLDisplay glDisplay=EGL14.EGL_NO_DISPLAY;
    private EGLContext glContext=EGL14.EGL_NO_CONTEXT;
    private EGLSurface glSurface=EGL14.EGL_NO_SURFACE;
    private String stage;
    private long began;
    private static final class Probe {
        final String rear, front, method; final int width, height; final boolean frontFirst;
        Probe(String rear, String front, String method, int width, int height, boolean frontFirst) {
            this.rear=rear; this.front=front; this.method=method; this.width=width; this.height=height; this.frontFirst=frontFirst;
        }
        public String toString() { return method + " rear=" + rear + " front=" + front + " " + width + "x" + height + " " + (frontFirst ? "front-first" : "rear-first"); }
    }
    private final class Endpoint {
        String id; CameraDevice device; CameraCaptureSession session; ImageReader reader;
        Surface surface; MediaCodec codec; MediaMuxer mux; File file;
        android.hardware.Camera old; SurfaceTexture texture;
        int frames, samples, track; boolean muxStarted, codecStarted, planar, openPending;
        byte[] latest;
    }
    CameraDiagnostics(Context context, Listener listener) {
        acquire(); this.context=context; this.listener=listener; manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        thread.start(); work=new Handler(thread.getLooper());
    }
    private void log(String text) { if (!closed) listener.line(text); }
    void run(final String method) {
        work.post(new Runnable() { public void run() {
            if (closed) return;
            cancelInternal(false);
            log("\n=== " + method + " · " + new java.util.Date() + " ===");
            began=SystemClock.elapsedRealtime();
            awaitService(method,generation);
        }});
    }
    private void awaitService(final String method,final int epoch) {
        if (closed || generation!=epoch) return;
        if (DashcamService.diagnosticsBusy() || previousEngineClosing()) {
            if (SystemClock.elapsedRealtime()-began > 10000) { log("FAIL: dashcam has not released its cameras. Stop recording and retry."); listener.idle(); return; }
            work.postDelayed(new Runnable() { public void run() { awaitService(method,epoch); }}, 100); return;
        }
        try {
            if (method.equals("Scan capabilities")) { inventory(); listener.idle(); return; }
            List<String> backs=new ArrayList<String>(), fronts=new ArrayList<String>();
            legacy=method.equals("Camera1 preview");
            if (legacy) {
                for (int i=0;i<android.hardware.Camera.getNumberOfCameras();i++) {
                    android.hardware.Camera.CameraInfo info=new android.hardware.Camera.CameraInfo(); android.hardware.Camera.getCameraInfo(i,info);
                    if (info.facing==android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK) backs.add(""+i);
                    if (info.facing==android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) fronts.add(""+i);
                }
            } else for (String id:manager.getCameraIdList()) {
                Integer facing=manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing==CameraCharacteristics.LENS_FACING_BACK) backs.add(id);
                if (facing != null && facing==CameraCharacteristics.LENS_FACING_FRONT) fronts.add(id);
            }
            Collections.sort(backs); Collections.sort(fronts);
            boolean baseline=method.equals("Single-camera baseline");
            for (String rear:backs) {
                if (baseline) { queue.add(new Probe(rear,null,"Two encoders",640,480,false)); continue; }
                for (String front:fronts) for (boolean reverse:new boolean[]{false,true}) {
                    int[][] sizes=method.equals("Open cameras only") ? new int[][]{{0,0}} :
                        method.equals("One split-screen encoder") || legacy ? new int[][]{{640,480}} : new int[][]{{1920,1080},{1280,720},{640,480}};
                    for (int[] size:sizes) queue.add(new Probe(rear,front,method,size[0],size[1],reverse));
                }
            }
            if (baseline) for (String front:fronts) queue.add(new Probe(front,null,"Two encoders",640,480,false));
            if (queue.isEmpty()) { log("SKIP: no matching exposed cameras.\n=== Tests finished ==="); listener.idle(); return; }
            log("Queued " + queue.size() + " probes. Each streams for 3 seconds; setup timeout 12 seconds. Stop cancels remaining probes.");
            next();
        } catch (Exception e) { log("FAIL scan: " + error(e)); listener.idle(); }
    }
    private void inventory() throws Exception {
        log("Device: " + Build.MANUFACTURER + " " + Build.MODEL + " / " + Build.DEVICE);
        log("Android: " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT + " security " + Build.VERSION.SECURITY_PATCH);
        log("Build: " + Build.DISPLAY + "\nFingerprint: " + Build.FINGERPRINT);
        log("Concurrent pairs: " + (Build.VERSION.SDK_INT>=30 ? manager.getConcurrentCameraIds() : "API unavailable"));
        for (String id:manager.getCameraIdList()) {
            CameraCharacteristics c=manager.getCameraCharacteristics(id);
            Integer facing=c.get(CameraCharacteristics.LENS_FACING);
            log("Camera " + id + " facing=" + facing + " (0=front,1=back,2=external) level=" + c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
            log("  physical=" + (Build.VERSION.SDK_INT>=28 ? c.getPhysicalCameraIds() : "unavailable") + " focal=" + Arrays.toString(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)));
            StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map!=null) log("  encoder="+Arrays.toString(map.getOutputSizes(MediaCodec.class))+"\n  YUV="+Arrays.toString(map.getOutputSizes(ImageFormat.YUV_420_888)));
            log("  fps="+Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));
        }
        UsbManager usb=(UsbManager)context.getSystemService(Context.USB_SERVICE);
        log("USB devices: " + usb.getDeviceList().size());
        for (UsbDevice d:usb.getDeviceList().values()) {
            log("  USB vendor="+d.getVendorId()+" product="+d.getProductId()+" permission="+usb.hasPermission(d));
            for(int i=0;i<d.getInterfaceCount();i++) log("    interface class="+d.getInterface(i).getInterfaceClass()+" subclass="+d.getInterface(i).getInterfaceSubclass());
        }
        log("USB is an inventory only: class 14 indicates a video interface. No USB recording or USB permission request is performed.");
        log("CameraX/NDK use the same camera service; not separate bypass tests. Vendor/root/ROM changes are not performed. Logical physical-camera IDs are listed, not opened independently.");
        log("PASS: capability scan completed.");
    }
    private void next() {
        if (closed) return;
        if (queue.isEmpty()) { log("=== Tests finished ==="); listener.idle(); return; }
        current=queue.remove(); final int token=++generation; began=SystemClock.elapsedRealtime(); stage="setup";
        log("\nTRY " + current);
        try {
            if (legacy) { openLegacy(token); return; }
            String first=current.frontFirst ? current.front : current.rear, second=current.frontFirst ? current.rear : current.front;
            if (!current.method.equals("Open cameras only")) {
                if (!supported(first) || (second!=null && !supported(second))) { finish("SKIP: requested size not exposed",token); return; }
            }
            Endpoint a=new Endpoint(); a.id=first; endpoints.add(a);
            if(second!=null) { Endpoint b=new Endpoint(); b.id=second; endpoints.add(b); }
            work.postDelayed(new Runnable() { public void run() { if(valid(token)) finish("FAIL at " + stage + ": 12-second timeout",token); }},12000);
            open(0,token);
        } catch(Exception e) { finish("FAIL at " + stage + ": " + error(e),token); }
    }
    private boolean supported(String id) throws Exception {
        StreamConfigurationMap map=manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes=map==null ? null : current.method.equals("Two encoders") ? map.getOutputSizes(MediaCodec.class) : map.getOutputSizes(ImageFormat.YUV_420_888);
        if(sizes!=null) for(Size size:sizes) if(size.getWidth()==current.width && size.getHeight()==current.height) return true;
        return false;
    }
    private boolean valid(int token) { return !closed && current!=null && token==generation; }
    private void open(final int index,final int token) throws Exception {
        final Endpoint ep=endpoints.get(index); stage="open camera " + ep.id; log("  " + stage);
        openings++; ep.openPending=true;
        try { manager.openCamera(ep.id,new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice camera) {
                if(!valid(token)) { camera.close(); settled(ep); return; }
                settled(ep);
                ep.device=camera; log("  OPENED " + ep.id);
                try {
                    if(index+1<endpoints.size()) open(index+1,token);
                    else if(current.method.equals("Open cameras only")) {
                        stage="hold open";
                        work.postDelayed(new Runnable(){public void run(){if(valid(token)) finish("PASS: all cameras stayed open for 1 second; no streams configured",token);}},1000);
                    } else configure(token);
                } catch(Exception e) { finish("FAIL at "+stage+": "+error(e),token); }
            }
            public void onDisconnected(CameraDevice camera) { camera.close(); settled(ep); if(valid(token)) finish("FAIL at "+stage+": camera "+ep.id+" disconnected",token); }
            public void onError(CameraDevice camera,int code) { camera.close(); settled(ep); if(valid(token)) finish("FAIL at "+stage+": camera "+ep.id+" error="+code+" "+cameraError(code),token); }
        },work); } catch(Exception e) { settled(ep); throw e; }
    }
    private void settled(Endpoint ep) {
        if(ep.openPending) { ep.openPending=false; openings--; }
        if(closed && openings==0) endLease();
    }
    private void endLease() { if(!leaseReleased) { leaseReleased=true; release(); } thread.quitSafely(); }
    private static String cameraError(int code) {
        switch(code) { case 1:return "CAMERA_IN_USE"; case 2:return "MAX_CAMERAS_IN_USE"; case 3:return "CAMERA_DISABLED"; case 4:return "CAMERA_DEVICE"; case 5:return "CAMERA_SERVICE"; default:return "UNKNOWN"; }
    }
    private void configure(final int token) throws Exception {
        stage="configure streams";
        final boolean composite=current.method.equals("One split-screen encoder");
        if(composite) encoder(endpoints.get(0),current.width*2,current.height,false);
        for(final Endpoint ep:endpoints) {
            if(current.method.equals("Two encoders")) encoder(ep,current.width,current.height,true);
            else {
                ep.reader=ImageReader.newInstance(current.width,current.height,ImageFormat.YUV_420_888,3);
                ep.reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener(){public void onImageAvailable(ImageReader reader){
                    if(!valid(token)) return;
                    try(Image image=reader.acquireLatestImage()) {
                        if(image==null) return; ep.frames++;
                        if(composite) {
                            Image.Plane[] p=image.getPlanes();
                            ep.latest=PreviewPixels.nv21(image.getWidth(),image.getHeight(),new ByteBuffer[]{p[0].getBuffer(),p[1].getBuffer(),p[2].getBuffer()},
                                new int[]{p[0].getRowStride(),p[1].getRowStride(),p[2].getRowStride()},new int[]{p[0].getPixelStride(),p[1].getPixelStride(),p[2].getPixelStride()});
                        }
                    } catch(Exception e) { finish("FAIL image: "+error(e),token); }
                }},work);
                ep.surface=ep.reader.getSurface();
            }
        }
        for(final Endpoint ep:endpoints) ep.device.createCaptureSession(Collections.singletonList(ep.surface),new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession session) {
                if(!valid(token)) { session.close(); return; } ep.session=session;
                try {
                    CaptureRequest.Builder request=ep.device.createCaptureRequest(current.method.equals("Two encoders") ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
                    request.addTarget(ep.surface); request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                    request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                    Range<Integer>[] ranges=manager.getCameraCharacteristics(ep.id).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    Range<Integer> chosen=null;
                    if(ranges!=null) for(Range<Integer> range:ranges) if(range.getUpper()<=30 && (chosen==null || range.getLower()<chosen.getLower())) chosen=range;
                    if(chosen!=null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,chosen);
                    session.setRepeatingRequest(request.build(),null,work); log("  CONFIGURED "+ep.id+" fps="+chosen);
                    for(Endpoint other:endpoints) if(other.session==null) return;
                    stage="capture"; drainLoop(token,0);
                    work.postDelayed(new Runnable(){public void run(){ if(valid(token)) {
                        String counts=counts(); boolean pass=true;
                        for(Endpoint other:endpoints) if(current.method.equals("Two encoders") ? other.samples<5 : other.frames<5) pass=false;
                        if(composite && endpoints.get(0).samples<5) pass=false;
                        finish((pass ? "PASS" : "FAIL: insufficient frames")+" "+counts,token);
                    }}},3000);
                } catch(Exception e) { finish("FAIL at configure "+ep.id+": "+error(e),token); }
            }
            public void onConfigureFailed(CameraCaptureSession session) { session.close(); if(valid(token)) finish("FAIL stream configuration camera="+ep.id,token); }
        },work);
    }
    private void encoder(Endpoint ep,int width,int height,boolean surface) throws Exception {
        MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
        ep.codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        int color=MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        if(!surface) {
            color=0;
            for(int candidate:ep.codec.getCodecInfo().getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats)
                if(candidate==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar || candidate==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) { color=candidate; break; }
            if(color==0) throw new IllegalStateException("Encoder has no explicit planar/semiplanar CPU input format");
            ep.planar=color==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        }
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,color); format.setInteger(MediaFormat.KEY_FRAME_RATE,15);
        format.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(500000,width*height*2)); format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        ep.codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        if(surface) ep.surface=ep.codec.createInputSurface();
        ep.codec.start(); ep.codecStarted=true;
        ep.file=File.createTempFile("camera-diag-",".mp4",context.getCacheDir()); ep.mux=new MediaMuxer(ep.file.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        log("  ENCODER "+ep.id+" "+ep.codec.getName()+" "+width+"x"+height+" input="+color);
    }
    private void drainLoop(final int token,final long previousInput) {
        if(!valid(token)) return;
        long nextInput=previousInput;
        try {
            if(current.method.equals("One split-screen encoder")) {
                Endpoint a=endpoints.get(0),b=endpoints.get(1); long now=SystemClock.elapsedRealtime();
                if(a.latest!=null && b.latest!=null && now-previousInput>=66) {
                    int index=a.codec.dequeueInputBuffer(0);
                    if(index>=0) {
                        byte[] frame=DiagnosticPixels.join(a.latest,b.latest,current.width,current.height,a.planar);
                        ByteBuffer input=a.codec.getInputBuffer(index); input.clear(); input.put(frame);
                        a.codec.queueInputBuffer(index,0,frame.length,SystemClock.elapsedRealtimeNanos()/1000,0); nextInput=now;
                    }
                }
            }
            for(Endpoint ep:endpoints) if(ep.codec!=null) {
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                for(int i=0;i<8;i++) {
                    int index=ep.codec.dequeueOutputBuffer(info,0);
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { ep.track=ep.mux.addTrack(ep.codec.getOutputFormat()); ep.mux.start(); ep.muxStarted=true; continue; }
                    if(index<0) break;
                    try { if(info.size>0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) { ep.mux.writeSampleData(ep.track,ep.codec.getOutputBuffer(index),info); ep.samples++; } }
                    finally { ep.codec.releaseOutputBuffer(index,false); }
                }
            }
        } catch(Exception e) { finish("FAIL encoder: "+error(e),token); return; }
        final long lastInput=nextInput;
        work.postDelayed(new Runnable(){public void run(){drainLoop(token,lastInput);}},20);
    }
    private void openLegacy(final int token) throws Exception {
        stage="Camera1 open";
        String[] ids=current.frontFirst ? new String[]{current.front,current.rear} : new String[]{current.rear,current.front};
        for(String id:ids) { Endpoint ep=new Endpoint(); ep.id=id; endpoints.add(ep); ep.old=android.hardware.Camera.open(Integer.parseInt(id)); log("  Camera1 OPENED "+id); }
        stage="Camera1 preview";
        legacyGL();
        for(final Endpoint ep:endpoints) {
            android.hardware.Camera.Parameters parameters=ep.old.getParameters(); boolean found=false;
            for(android.hardware.Camera.Size size:parameters.getSupportedPreviewSizes()) if(size.width==640 && size.height==480) found=true;
            if(!found) { finish("SKIP: Camera1 640x480 not supported",token); return; }
            parameters.setPreviewSize(640,480); parameters.setPreviewFormat(ImageFormat.NV21); ep.old.setParameters(parameters);
            int[] texture=new int[1]; GLES20.glGenTextures(1,texture,0);
            ep.texture=new SurfaceTexture(texture[0]);
            ep.texture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener(){public void onFrameAvailable(SurfaceTexture source){
                if(valid(token)) try { source.updateTexImage(); } catch(RuntimeException e) { finish("FAIL Camera1 texture: "+error(e),token); }
            }},work);
            ep.old.setPreviewTexture(ep.texture);
            ep.old.setPreviewCallback(new android.hardware.Camera.PreviewCallback(){public void onPreviewFrame(byte[] bytes,android.hardware.Camera camera){if(valid(token) && bytes!=null) ep.frames++;}});
            ep.old.startPreview();
        }
        work.postDelayed(new Runnable(){public void run(){if(valid(token)) finish((endpoints.get(0).frames>=5 && endpoints.get(1).frames>=5 ? "PASS" : "FAIL: insufficient frames")+" "+counts(),token);}},3000);
    }
    private void legacyGL() {
        glDisplay=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];
        if(!EGL14.eglInitialize(glDisplay,version,0,version,1)) throw new IllegalStateException("EGL initialize failed");
        EGLConfig[] configs=new EGLConfig[1]; int[] count=new int[1];
        EGL14.eglChooseConfig(glDisplay,new int[]{EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0);
        if(count[0]<1) throw new IllegalStateException("No EGL configuration");
        glContext=EGL14.eglCreateContext(glDisplay,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        glSurface=EGL14.eglCreatePbufferSurface(glDisplay,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(glDisplay,glSurface,glSurface,glContext)) throw new IllegalStateException("EGL make current failed");
    }
    private String counts() { String result=""; for(Endpoint ep:endpoints) result+="camera="+ep.id+" frames="+ep.frames+" encoded="+ep.samples+"; "; return result; }
    private void finish(String result,int token) {
        if(!valid(token)) return;
        long elapsed=SystemClock.elapsedRealtime()-began;
        generation++; current=null;
        boolean finalized=cleanup();
        log((!finalized && result.startsWith("PASS") ? "FAIL MP4 finalization; capture result was: " : "")+result+" elapsed="+elapsed+"ms");
        final int epoch=generation;
        work.postDelayed(new Runnable(){public void run(){if(generation==epoch) next();}},1000);
    }
    private boolean cleanup() {
        boolean finalized=true;
        for(Endpoint ep:endpoints) {
            if(ep.session!=null) try { ep.session.close(); } catch(Exception ignored) {}
            if(ep.device!=null) try { ep.device.close(); } catch(Exception ignored) {}
            if(ep.old!=null) { try { ep.old.setPreviewCallback(null); ep.old.stopPreview(); } catch(Exception ignored) {} try { ep.old.release(); } catch(Exception ignored) {} }
            if(ep.texture!=null) try { ep.texture.release(); } catch(Exception ignored) {}
            if(ep.reader!=null) try { ep.reader.close(); } catch(Exception ignored) {}
            if(ep.codec!=null) { try { if(ep.codecStarted) ep.codec.stop(); } catch(Exception ignored) {} try { ep.codec.release(); } catch(Exception ignored) {} }
            if(ep.mux!=null) { try { if(ep.muxStarted) ep.mux.stop(); } catch(Exception e) { finalized=false; log("  FAIL MP4 finalization "+ep.id+": "+error(e)); } try { ep.mux.release(); } catch(Exception ignored) {} }
            if(ep.surface!=null && ep.reader==null) try { ep.surface.release(); } catch(Exception ignored) {}
            if(ep.file!=null) { log("  Temporary MP4 camera="+ep.id+" bytes="+ep.file.length()); if(!ep.file.delete()) log("  Could not delete temporary file: "+ep.file.getName()); }
        }
        endpoints.clear();
        if(glDisplay!=EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(glDisplay,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(glSurface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(glDisplay,glSurface);
            if(glContext!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(glDisplay,glContext);
            EGL14.eglTerminate(glDisplay); EGL14.eglReleaseThread();
            glDisplay=EGL14.EGL_NO_DISPLAY; glSurface=EGL14.EGL_NO_SURFACE; glContext=EGL14.EGL_NO_CONTEXT;
        }
        return finalized;
    }
    private void cancelInternal(boolean report) { generation++; queue.clear(); current=null; cleanup(); if(report) { log("STOPPED: remaining probes cancelled; cameras released."); listener.idle(); } }
    void cancel() { work.post(new Runnable(){public void run(){cancelInternal(true);}}); }
    void close() {
        final CountDownLatch done=new CountDownLatch(1); closed=true;
        work.post(new Runnable(){public void run(){try { cancelInternal(false); } finally { done.countDown(); if(openings==0) endLease(); else work.postDelayed(new Runnable(){public void run(){endLease();}},15000); }}});
        try { done.await(500,TimeUnit.MILLISECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private static String error(Exception e) { return e.getClass().getSimpleName()+": "+e.getMessage(); }
}
