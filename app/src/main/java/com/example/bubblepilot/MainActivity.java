package com.example.bubblepilot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.content.Context;
import android.os.Build;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import android.view.View;
import android.widget.Button;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Permission screen + settings screen (plain Android, zero third-party dependencies). */
public final class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST = 1007;
    private LocalSettings store;
    private LinearLayout content;
    private TextView permissionStatus;
    private EditText topY,launchY;
    private final int fg=Color.rgb(235,243,253),muted=Color.rgb(169,186,207);
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store=new LocalSettings(this);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13,21,37));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(19),dp(30),dp(19),dp(36));
        scroll.addView(content);
        setContentView(scroll);

        TextView heading=text("BUBBLE PILOT",25,fg,true);
        content.addView(heading);
        content.addView(text("v0.8.0 • Bắn liên tục • Ưu tiên POP, fallback đổi bóng • Không root",14,muted,false));
        space(20);
        cardHeader("1. Cấp quyền");
        permissionStatus=text("Đang kiểm tra...",14,fg,false);
        content.addView(permissionStatus);
        space(8);
        Button access=button("Cấp quyền CLICK (Accessibility)");
        content.addView(access);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        space(7);
        Button floatPermission=button("Cấp quyền FLOAT (hiển thị nổi)");
        content.addView(floatPermission);
        floatPermission.setOnClickListener(v -> {
            try {
                Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+getPackageName()));
                startActivity(i);
            } catch (Exception ex) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
        });
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.R) {
            space(7);
            content.addView(text("Android 8–10 cần thêm quyền CAPTURE do hệ thống cấp để chụp màn hình game.",12,muted,false));
            Button capture=button("Cấp quyền CAPTURE (Android 8–10)");
            content.addView(capture);
            capture.setOnClickListener(v->requestCapture());
        }
        space(23);
        cardHeader("2. Hiệu chỉnh trò chơi offline");
        content.addView(text("Tự căn lại lưới khi đổi hàng, mô phỏng va chạm đầu tiên và bật tường 1–3 lần. Khi không có POP, chọn đường ghép cùng màu hoặc bắn đổi bóng, không dừng tự động. Đợi 2 ảnh giống nhau để chống bắn trùng.",13,muted,false));
        space(5);
        topY=field("Tâm hàng bóng đầu (% chiều cao)",store.getText("top_pct","11.36"));
        launchY=field("Tâm bóng ở nòng bắn (% chiều cao)",store.getText("launch_pct","75.86"));
        space(14);
        Button save=button("LƯU CÀI ĐẶT");
        content.addView(save);
        save.setOnClickListener(v -> saveSettings());
        space(9);
        Button showOverlay=button("START — HIỆN THANH NỔI");
        content.addView(showOverlay);
        showOverlay.setOnClickListener(v -> {
            if(!saveSettings())return;
            if(!Settings.canDrawOverlays(this)) {
                toast("Bạn cần cấp quyền FLOAT trước");return;
            }
            if(Build.VERSION.SDK_INT<Build.VERSION_CODES.R && !LegacyProjectionService.isReady()) {
                toast("Android 8–10 cần cấp quyền CAPTURE trước");requestCapture();return;
            }
            GameAccessibilityService service=GameAccessibilityService.get();
            if(service==null) {toast("Bạn cần bật quyền CLICK trong Trợ năng trước");return;}
            service.openOverlay();
            toast("Mở game và nhấn ▶ trên thanh nổi để chơi. Nhấn ■ để dừng.");
        });
        space(19);
        content.addView(text("Chỉ dùng trong môi trường chơi thử offline. Xử lý ảnh tại thiết bị, không gửi ảnh qua mạng. Nhấn ■ để dừng.",12,muted,false));
        updateStatus();
    }
    private int dp(int px) {return Math.round(getResources().getDisplayMetrics().density*px);}
    private GradientDrawable bg(int color) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color);d.setCornerRadius(dp(11));return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView tv=new TextView(this);tv.setText(s);tv.setTextSize(size);tv.setTextColor(color);
        if(bold)tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        tv.setLineSpacing(dp(2),1.08f);return tv;
    }
    private void space(int px) {
        View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(px)));
    }
    private void cardHeader(String s) {content.addView(text(s,17,fg,true));space(10);}
    private Button button(String label) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackground(bg(Color.rgb(35,99,180)));
        b.setMinHeight(dp(48));b.setPadding(dp(9),dp(9),dp(9),dp(9));return b;
    }
    private EditText field(String label,String value) {
        content.addView(text(label,13,muted,false));
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(fg);e.setTextSize(14);
        e.setHintTextColor(muted);e.setBackground(bg(Color.rgb(35,47,67)));
        e.setPadding(dp(12),dp(13),dp(12),dp(13));e.setText(value);

        content.addView(e,new LinearLayout.LayoutParams(-1,dp(51)));
        space(9);return e;
    }
    private boolean saveSettings() {
        try {
            double top=Double.parseDouble(topY.getText().toString().trim());
            double launch=Double.parseDouble(launchY.getText().toString().trim());
            if(!Double.isFinite(top) || !Double.isFinite(launch) || top<6 || top>24 || launch<50 || launch>91 || launch<top+23)
                throw new IllegalArgumentException("Tỷ lệ bố cục không hợp lệ");
            store.putText("top_pct",String.valueOf(top));
            store.putText("launch_pct",String.valueOf(launch));
            toast("Đã lưu cấu hình");return true;
        } catch(Exception e) {toast("Lỗi: "+e.getMessage());return false;}
    }
    private void requestCapture() {
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if(manager==null) {toast("Thiết bị không hỗ trợ chụp màn hình");return;}
        startActivityForResult(manager.createScreenCaptureIntent(), CAPTURE_REQUEST);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CAPTURE_REQUEST) {
            if(resultCode==RESULT_OK && data!=null) {
                Intent i=new Intent(this,LegacyProjectionService.class);
                i.putExtra("result_code",resultCode);
                i.putExtra("result_data",data);
                startForegroundService(i);
                toast("Đã cho phép chia sẻ màn hình. Chọn START sau khi cấp CLICK/FLOAT.");
            } else toast("Bạn chưa cấp quyền chụp màn hình");
        }
    }
    private void updateStatus() {
        boolean click=GameAccessibilityService.get()!=null;
        boolean floating=Settings.canDrawOverlays(this);
        if(permissionStatus!=null)permissionStatus.setText(
                "CLICK: "+(click?"✓ Đã bật":"✗ Chưa bật")+"     FLOAT: "+
                        (floating?"✓ Đã bật":"✗ Chưa bật")+
                        (Build.VERSION.SDK_INT<Build.VERSION_CODES.R?
                                "     CAPTURE: "+(LegacyProjectionService.isReady()?"✓ Đã bật":"✗ Chưa bật"):""));
    }
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public void onResume(){super.onResume();updateStatus();}
}
