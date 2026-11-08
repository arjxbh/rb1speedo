package club.midlife.rb1speed;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Gauge artwork only. Android views own the controls and digital readout. */
final class DashboardDial extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF(-375,-375,375,375);
    private final Path pointer = new Path();
    private Bitmap face;
    private DashboardSkin skin = DashboardSkin.RB1;
    private boolean valid;
    private float needle;
    private long previous;

    DashboardDial(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pointer.moveTo(-86,-9); pointer.lineTo(330,-4);
        pointer.lineTo(367,0); pointer.lineTo(330,4); pointer.lineTo(-86,9); pointer.close();
    }
    void update(DashboardSkin value, double speed, boolean valid, long now) {
        if (skin != value) { skin=value; discardFace(); }
        this.valid=valid;
        float dt=previous==0?.033f:Math.min(.1f,(now-previous)/1000f); previous=now;
        float target=(float)Math.min(120,Math.max(0,speed));
        needle+=(target-needle)*(1-(float)Math.exp(-dt/.13f));
        invalidate();
    }
    private void discardFace() {
        if (face != null) { face.recycle(); face=null; }
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { discardFace(); }
    @Override protected void onDetachedFromWindow() { discardFace(); super.onDetachedFromWindow(); }
    private void reset(int color) { paint.reset(); paint.setAntiAlias(true); paint.setColor(color); }
    private void circle(Canvas canvas,float radius,int color) { reset(color); canvas.drawCircle(0,0,radius,paint); }
    private void text(Canvas canvas,String text,float x,float y,float size,int color) {
        reset(color); paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(skin.font,skin==DashboardSkin.INTEGRA?Typeface.BOLD:Typeface.NORMAL));
        canvas.drawText(text,x,y,paint);
    }
    private int blend(int a,int b,float fraction) {
        return Color.rgb(Math.round(Color.red(a)*(1-fraction)+Color.red(b)*fraction),
            Math.round(Color.green(a)*(1-fraction)+Color.green(b)*fraction),
            Math.round(Color.blue(a)*(1-fraction)+Color.blue(b)*fraction));
    }
    private void drawFace(Canvas canvas) {
        reset(0xff000000); paint.setShadowLayer(14,0,12,0xaa000000);
        canvas.drawCircle(0,0,425,paint);
        // A machined rim with a bright upper lip and a dark underside.
        reset(0xffffffff);
        paint.setShader(new LinearGradient(-300,-420,280,420,
            new int[]{0xff7a8795,0xff242c35,0xff0a0d12,0xff515f6b},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(0,0,427,paint);
        circle(canvas,416,0xff080b10);
        reset(0xffffffff); paint.setShader(new LinearGradient(0,-410,0,410,
            new int[]{0xff080b10,0xff3d4b59},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(0,0,409,paint);
        // Recessed face: overhead lighting falls off towards the perimeter.
        reset(skin.face);
        paint.setShader(new RadialGradient(-90,-130,560,
            new int[]{blend(skin.face,0xffffffff,skin==DashboardSkin.INTEGRA?.10f:.035f),skin.face,blend(skin.face,0xff000000,.4f)},
            new float[]{0,.65f,1},Shader.TileMode.CLAMP));
        canvas.drawCircle(0,0,400,paint);
        if (skin==DashboardSkin.RB1) {
            reset(0xffffffff); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(132);
            paint.setShader(new RadialGradient(0,0,405,new int[]{0xff020611,0xff103fe8,0xff041338},new float[]{.56f,.79f,1},Shader.TileMode.CLAMP));
            canvas.drawArc(new RectF(-320,-320,320,320),skin.start,skin.sweep,false,paint);
        } else {
            reset(skin.accent); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
            canvas.drawCircle(0,0,390,paint);
            reset(skin.pointer); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(7);
            canvas.drawArc(arc,skin.start+skin.sweep*100/120,skin.sweep*20/120,false,paint);
        }
        for (int speed=0;speed<=120;speed+=2) {
            double angle=Math.toRadians(skin.start+speed*skin.sweep/120);
            float cos=(float)Math.cos(angle), sin=(float)Math.sin(angle);
            boolean major=speed%10==0;
            reset(skin==DashboardSkin.RB1 && !major ? 0xffe5f6ff : skin.tick); paint.setStrokeWidth(major?5:2); paint.setStrokeCap(Paint.Cap.ROUND);
            if (skin==DashboardSkin.RB1 && major) paint.setShadowLayer(7,0,0,skin.accent);
            canvas.drawLine(cos*377,sin*377,cos*(major?345:362),sin*(major?345:362),paint);
            if (speed%skin.labelStep==0) text(canvas,String.valueOf(speed),cos*302,sin*302+14,skin.labelStep==10?33:45,skin.ink);
        }
        if (skin != DashboardSkin.RB1) {
            text(canvas,skin.title,0,-125,25,skin.ink);
            text(canvas,"mph",0,-85,24,skin.ink);
            text(canvas,skin.code,0,198,24,skin.ink);
            text(canvas,"GPS SPEED",0,226,14,skin==DashboardSkin.INTEGRA?0xff565e63:0xff92a2b0);
        }
        // Small reflected edge highlights communicate the glass and bezel depth.
        reset(0x667d95aa); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        canvas.drawArc(new RectF(-420,-420,420,420),205,95,false,paint);
        reset(0x302f465d); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        canvas.drawArc(new RectF(-397,-397,397,397),20,120,false,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width=getWidth(),height=getHeight();
        if (width<=0||height<=0) return;
        float scale=Math.min(width,height)/900f;
        if (face==null) {
            face=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas background=new Canvas(face);
            background.translate(width/2f,height/2f); background.scale(scale,scale);
            drawFace(background);
        }
        canvas.drawBitmap(face,0,0,null);
        canvas.save(); canvas.translate(width/2f,height/2f); canvas.scale(scale,scale);
        if (valid) {
            canvas.save(); canvas.rotate(skin.start+needle*skin.sweep/120);
            reset(0x99000000); paint.setShadowLayer(6,0,8,0x99000000);
            canvas.drawPath(pointer,paint);
            reset(skin.pointer);
            paint.setShader(new LinearGradient(0,-9,0,9,new int[]{blend(skin.pointer,0xffffffff,.6f),skin.pointer,blend(skin.pointer,0xff000000,.5f)},null,Shader.TileMode.CLAMP));
            canvas.drawPath(pointer,paint);
            canvas.restore();
        }
        if (skin==DashboardSkin.RB1) {
            // The real RB1 masks the spindle and needle tail behind a large opaque disc.
            // Composite this AFTER the pointer so only its outer segment remains visible.
            reset(0xff000000); paint.setShadowLayer(7,0,4,0xcc000000);
            canvas.drawCircle(0,0,245,paint);
            reset(0xff000000);
            paint.setShader(new RadialGradient(-65,-110,390,
                new int[]{0xff080b10,0xff020305,0xff000000},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(0,0,245,paint);
            reset(0x333b4856); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
            canvas.drawArc(new RectF(-245,-245,245,245),205,100,false,paint);
            text(canvas,skin.title,0,-80,25,skin.ink);
            text(canvas,"mph",0,-40,24,skin.ink);
            text(canvas,skin.code,0,150,24,skin.ink);
            text(canvas,"GPS SPEED",0,178,14,0xff92a2b0);
        } else {
            reset(0xff05070a); paint.setShadowLayer(8,0,7,0xaa000000); canvas.drawCircle(0,0,37,paint);
            reset(0xffffffff); paint.setShader(new LinearGradient(-20,-35,20,35,new int[]{0xff7b8792,0xff151c24,0xff060a0e},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(0,0,34,paint);
            circle(canvas,26,0xff101720);
            reset(0xff6c7e8c); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
            canvas.drawArc(new RectF(-26,-26,26,26),205,115,false,paint);
        }
        canvas.restore();
    }
}
