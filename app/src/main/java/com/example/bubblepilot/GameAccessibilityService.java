package com.example.bubblepilot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-controlled screen screenshot + tap automation service.
 * No root, no hidden access: system requires explicit accessibility approval.
 */
public final class GameAccessibilityService extends AccessibilityService {
    private static volatile GameAccessibilityService current;
    public static GameAccessibilityService get() {return current;}

    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService visionWorker=Executors.newSingleThreadExecutor();
    private final RemoteAdvisor remote=new RemoteAdvisor();
    private WindowManager wm;
    private View floating;
    private WindowManager.LayoutParams floatParams;
    private TextView status;
    private boolean running=false, screenshotPending=false;
    private final ShotGate shotGate = new ShotGate();
    private static final long FRAME_CHECK_MS = 115;
    private static final long ASTRA_MAX_WAIT_MS = 170;
    private long pendingApiSignature=Long.MIN_VALUE,pendingApiSince=0;
    private long remoteSignature=Long.MIN_VALUE,remoteReceivedAt=0;
    private RemoteAdvisor.Point remotePoint=null;
    private String lastMessage="Sẵn sàng";
    private int runId=0;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();current=this;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {stopBot();}
    @Override public boolean onUnbind(android.content.Intent intent) {
        stopBot();hideOverlay();current=null;return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        stopBot();hideOverlay();current=null;
        visionWorker.shutdownNow();remote.close();super.onDestroy();
    }
    private int dp(float n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bubble(int c) {
        GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(15));
        d.setStroke(dp(1),Color.rgb(77,98,125));return d;
    }
    private TextView label(String s,int size,int color) {
        TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER);
        return v;
    }
    public void openOverlay() {
        if(Looper.myLooper()!=Looper.getMainLooper()) {
            main.post(this::openOverlay);return;
        }
        if(!Settings.canDrawOverlays(this))return;
        if(floating!=null)return;
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6),dp(5),dp(6),dp(5));bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(bubble(Color.rgb(16,32,56)));
        TextView drag=label("⠿",21,Color.rgb(205,225,250));
        drag.setPadding(dp(10),0,dp(10),0);
        bar.addView(drag,new LinearLayout.LayoutParams(dp(47),dp(44)));
        TextView play=label("▶",22,Color.rgb(89,239,153));
        bar.addView(play,new LinearLayout.LayoutParams(dp(43),dp(44)));
        TextView stop=label("■",21,Color.rgb(255,112,126));
        bar.addView(stop,new LinearLayout.LayoutParams(dp(43),dp(44)));
        status=label("READY",10,Color.WHITE);
        status.setPadding(dp(6),0,dp(6),0);
        bar.addView(status,new LinearLayout.LayoutParams(dp(74),dp(44)));
        TextView close=label("×",22,Color.rgb(191,205,224));
        bar.addView(close,new LinearLayout.LayoutParams(dp(32),dp(44)));
        floatParams=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        floatParams.gravity=Gravity.TOP | Gravity.LEFT;
        // Default to the top HUD to avoid covering balls on the board.
        floatParams.x=dp(12);floatParams.y=dp(7);
        drag.setOnTouchListener(new View.OnTouchListener() {
            int initialX,initialY;float touchX,touchY;
            @Override public boolean onTouch(View v,MotionEvent event) {
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX=floatParams.x;initialY=floatParams.y;
                        touchX=event.getRawX();touchY=event.getRawY();return true;
                    case MotionEvent.ACTION_MOVE:
                        floatParams.x=initialX+(int)(event.getRawX()-touchX);
                        floatParams.y=initialY+(int)(event.getRawY()-touchY);
                        try {wm.updateViewLayout(floating,floatParams);}catch(Exception ignore){}
                        return true;
                    default:return true;
                }
            }
        });
        play.setOnClickListener(v->startBot());
        stop.setOnClickListener(v->stopBot());
        close.setOnClickListener(v->{stopBot();hideOverlay();});
        floating=bar;
        try {wm.addView(floating,floatParams);} catch(Exception e) {floating=null;status=null;}
    }
    private void hideOverlay() {
        if(floating!=null && wm!=null) {
            try {wm.removeView(floating);}catch(Exception ignore){}
        }
        floating=null;status=null;
    }
    private void updateStatus(String value) {
        lastMessage=value;
        if(status!=null)status.setText(value.length()>12?value.substring(0,12):value);
    }
    private GameSolver solver() {
        SecretStore p=new SecretStore(this);
        GameSolver.Configuration cfg=new GameSolver.Configuration();
        try {
            cfg.topY=Double.parseDouble(p.getText("top_pct","11.36"))/100.;
            cfg.launchY=Double.parseDouble(p.getText("launch_pct","75.86"))/100.;
        } catch(NumberFormatException ignored){}
        return new GameSolver(cfg);
    }
    private void startBot() {
        if(running)return;
        running=true;runId++;
        GameSolver.resetCalibration();
        shotGate.reset();
        pendingApiSignature=Long.MIN_VALUE;remoteSignature=Long.MIN_VALUE;
        remotePoint=null;
        updateStatus("RUNNING");
        scheduleFrame(runId,100);
    }
    private void stopBot() {
        running=false;runId++;
        updateStatus("STOPPED");
    }
    private void scheduleFrame(int token,long delayMs) {
        main.postDelayed(()->{if(running && token==runId)captureFrame(token);},delayMs);
    }
    private void captureFrame(int token) {
        if(screenshotPending){scheduleFrame(token,FRAME_CHECK_MS);return;}
        screenshotPending=true;
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.R) {
            LegacyProjectionService legacy=LegacyProjectionService.get();
            if(legacy==null || !LegacyProjectionService.isReady()) {
                screenshotPending=false;
                updateStatus("CAPTURE OFF");
                scheduleFrame(token,800);
                return;
            }
            visionWorker.execute(()->{
                Bitmap bitmap=legacy.acquireBitmap();
                if(bitmap==null) {
                    main.post(()->{
                        screenshotPending=false;
                        if(token==runId && running)scheduleFrame(token,220);
                    });
                } else processBitmap(bitmap,token);
            });
            return;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result) {
                    Bitmap copy=null;
                    HardwareBuffer hw=result.getHardwareBuffer();
                    try {
                        Bitmap original=Bitmap.wrapHardwareBuffer(hw,result.getColorSpace());
                        if(original!=null)copy=original.copy(Bitmap.Config.ARGB_8888,false);
                        if(original!=null)original.recycle();
                    } catch(Exception ignore){} finally {if(hw!=null)hw.close();}
                    if(copy==null) {
                        screenshotPending=false;
                        if(token==runId && running) {updateStatus("ẢNH LỖI");scheduleFrame(token,500);}
                        return;
                    }
                    final Bitmap bitmap=copy;
                    visionWorker.execute(()->processBitmap(bitmap,token));
                }
                @Override public void onFailure(int code) {
                    screenshotPending=false;
                    if(token==runId && running) {
                        updateStatus("SCREEN "+code);
                        scheduleFrame(token,code==ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT?450:900);
                    }
                }
            });
        } catch(Exception e) {
            screenshotPending=false;
            if(token==runId && running) {updateStatus("NO SCREEN");scheduleFrame(token,800);}
        }
    }
    private void processBitmap(Bitmap original,int token) {
        Bitmap analysis=original;
        try {
            final int screenW=original.getWidth(),screenH=original.getHeight();
            // Keep per-frame pixel scoring fast on large CloudPhone and phone
            // screenshots without losing the original gesture coordinates.
            if(screenW>560) {
                int w=480,h=Math.max(1,Math.round(screenH*(480f/screenW)));
                analysis=Bitmap.createScaledBitmap(original,w,h,true);
                if(analysis!=original)original.recycle();
            }
            final Bitmap image=analysis;
            int w=image.getWidth(),h=image.getHeight();
            int[] rgba=new int[w*h];
            image.getPixels(rgba,0,w,0,0,w,h);
            GameSolver planner=solver();
            GameSolver.Board board=planner.detect(rgba,w,h);
            GameSolver.Shot move=planner.bestShot(board);
            final String report=planner.diagnosis();
            main.post(()->{
                screenshotPending=false;
                if(token==runId && running)
                    onFrame(token,image,board,move,report,
                            screenW/(float)w,screenH/(float)h);
                else image.recycle();
            });
        } catch(Exception error) {
            if(analysis!=null && !analysis.isRecycled())analysis.recycle();
            main.post(()->{
                screenshotPending=false;
                if(token==runId && running) {updateStatus("VISION ERR");scheduleFrame(token,600);}
            });
        }
    }
    private void onFrame(int token,Bitmap image,GameSolver.Board board,GameSolver.Shot shot,
                         String report,float scaleX,float scaleY) {
        if(board==null) {
            image.recycle();
            shotGate.observedNoBoard();
            updateStatus(report);
            scheduleFrame(token,FRAME_CHECK_MS);
            return;
        }
        final long now=SystemClock.elapsedRealtime();
        if(!shotGate.canShoot(board.signature,now)) {
            image.recycle();
            updateStatus("CHỜ LƯỢT");
            scheduleFrame(token,FRAME_CHECK_MS);
            return;
        }
        // A non-matching projectile LOSES points in this game. The old code
        // sent shots with matched=0 as soon as the API wait expired. Block
        // these entirely; an uncertain shot is worse than waiting.
        if(shot==null || shot.matched<3 || shot.reliability<.80) {
            SecretStore prefs=new SecretStore(this);
            boolean useAstra=prefs.getBoolean("ai_enabled",true)
                    && !prefs.readKey().isEmpty();
            if(useAstra && pendingApiSignature!=board.signature) {
                pendingApiSignature=board.signature;
                remotePoint=null;remoteSignature=Long.MIN_VALUE;
                final int requestToken=token;
                remote.request(image,board.signature,
                        prefs.getText("endpoint","https://api.xingchiapi.com/v1"),
                        prefs.getText("model","gpt-6-astra"),prefs.readKey(),
                        (signature,point,status)->{
                            if(requestToken!=runId || !running)return;
                            remoteSignature=signature;
                            remotePoint=point;
                            remoteReceivedAt=SystemClock.elapsedRealtime();
                        });
            }
            // Astra is a suggestion source, NEVER a bypass of collision,
            // match-3 or small-aim-perturbation validation.
            GameSolver.Shot verified=null;
            if(remoteSignature==board.signature && remotePoint!=null &&
                    now-remoteReceivedAt<15000)
                verified=solver().safeShotAtTap(board,remotePoint.x,remotePoint.y);
            image.recycle();
            if(verified==null) {
                updateStatus(!useAstra || remoteSignature==board.signature?
                        "KHÔNG CÓ POP":"CHỜ POP/AI");
                scheduleFrame(token,FRAME_CHECK_MS);
                return;
            }
            performTap(token,verified,scaleX,scaleY);
            return;
        }
        // CPU plan is available and validated; never wait for network latency.
        image.recycle();
        performTap(token,shot,scaleX,scaleY);
    }
    private void performTap(int token,GameSolver.Shot shot,float scaleX,float scaleY) {
        if(!running || token!=runId)return;
        if(shot==null || shot.matched<3 || shot.reliability<.80) {
            updateStatus("BLOCK NO POP");
            scheduleFrame(token,FRAME_CHECK_MS);
            return;
        }
        Path path=new Path();
        path.moveTo(Math.round(shot.tapX*scaleX),Math.round(shot.tapY*scaleY));
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,55);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(stroke).build();
        try {
            boolean sent=dispatchGesture(gesture,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription d) {
                    if(token==runId && running)scheduleFrame(token,FRAME_CHECK_MS);
                }
                @Override public void onCancelled(GestureDescription d) {
                    if(token==runId && running) {
                        shotGate.reset();updateStatus("TAP FAIL");scheduleFrame(token,450);
                    }
                }
            },main);
            if(sent) {
                shotGate.shotDispatched(shot.stateSignature,SystemClock.elapsedRealtime());
                String routeLabel=shot.bounces>0?"B"+shot.bounces+" ":"";
                updateStatus(routeLabel+(shot.matched>=3?"POP "+shot.matched:
                        "AIM "+Math.round(shot.angle)));
            } else {updateStatus("TAP ERR");scheduleFrame(token,700);}
        } catch(Exception e) {updateStatus("TAP ERR");scheduleFrame(token,700);}
    }
}
