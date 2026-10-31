package com.clashai.assistant;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE="resultCode", EXTRA_RESULT_DATA="resultData";
    public static final String ACTION_FRAME="com.clashai.assistant.FRAME";
    public static final String EXTRA_TEXT="text", EXTRA_STATUS="status";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private com.google.mlkit.vision.text.TextRecognizer recognizer;
    private final AtomicBoolean processing=new AtomicBoolean(false);
    private long lastFrame=0;

    @Override public void onCreate(){ super.onCreate(); recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS); }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        createNotification();
        if(intent==null){ stopSelf(); return START_NOT_STICKY; }
        int code=intent.getIntExtra(EXTRA_RESULT_CODE,0);
        Intent data=intent.getParcelableExtra(EXTRA_RESULT_DATA);
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=manager.getMediaProjection(code,data);
        if(projection==null){ broadcast("No se pudo iniciar la captura.",""); stopSelf(); return START_NOT_STICKY; }
        projection.registerCallback(new MediaProjection.Callback(){ @Override public void onStop(){ stopSelf(); } },new Handler(Looper.getMainLooper()));
        startReader();
        broadcast("Captura activa. OCR en proceso; no identifica ilustraciones de cartas.","");
        return START_NOT_STICKY;
    }
    private void startReader(){
        int w=getResources().getDisplayMetrics().widthPixels, h=getResources().getDisplayMetrics().heightPixels, dpi=getResources().getDisplayMetrics().densityDpi;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->{
            Image image=null;
            try{
                image=r.acquireLatestImage();
                if(image==null || processing.get() || System.currentTimeMillis()-lastFrame<1800) return;
                processing.set(true); lastFrame=System.currentTimeMillis();
                Image.Plane plane=image.getPlanes()[0]; ByteBuffer buffer=plane.getBuffer();
                int pixelStride=plane.getPixelStride(), rowStride=plane.getRowStride();
                Bitmap full=Bitmap.createBitmap(rowStride/pixelStride,image.getHeight(),Bitmap.Config.ARGB_8888);
                full.copyPixelsFromBuffer(buffer);
                Bitmap bmp=Bitmap.createBitmap(full,0,0,image.getWidth(),image.getHeight());
                full.recycle();
                image.close(); image=null;
                recognizer.process(InputImage.fromBitmap(bmp,0))
                    .addOnSuccessListener(result->{ String t=result.getText(); broadcast("Captura activa. OCR ejecutado; cartas no reconocidas.",t); })
                    .addOnFailureListener(e->broadcast("Captura activa; error al procesar texto.",""))
                    .addOnCompleteListener(task->{ bmp.recycle(); processing.set(false); });
            }catch(Exception e){ if(image!=null) image.close(); processing.set(false); }
        },new Handler(Looper.getMainLooper()));
        display=projection.createVirtualDisplay("ClashAI",w,h,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
    }
    private void broadcast(String status,String text){ Intent i=new Intent(ACTION_FRAME); i.setPackage(getPackageName()); i.putExtra(EXTRA_STATUS,status); i.putExtra(EXTRA_TEXT,text); sendBroadcast(i); }
    private void createNotification(){
        String id="clashai_capture";
        if(Build.VERSION.SDK_INT>=26){ NotificationChannel c=new NotificationChannel(id,"Captura Clash AI",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); }
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,id):new Notification.Builder(this);
        startForeground(1,b.setContentTitle("Clash AI").setContentText("Captura de pantalla activa").setSmallIcon(android.R.drawable.ic_menu_camera).build());
    }
    @Override public void onDestroy(){
        if(display!=null) display.release();
        if(reader!=null) reader.close();
        if(projection!=null) projection.stop();
        if(recognizer!=null) recognizer.close();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){ return null; }
}
