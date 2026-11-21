package com.example.bubblepilot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Permission screen + settings screen (plain Android, zero third-party dependencies). */
public final class MainActivity extends Activity {
    private SecretStore store;
    private LinearLayout content;
    private TextView permissionStatus;
    private EditText api,endpoint,model,topY,launchY;
    private CheckBox aiEnabled;
    private final int fg=Color.rgb(235,243,253),muted=Color.rgb(169,186,207);
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store=new SecretStore(this);
        // Upgrade an older Bubble Pilot installation to the newly requested model.
        if("gpt-6.1-sol".equals(store.getText("model","gpt-6-astra")))
            store.putText("model","gpt-6-astra");
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
        content.addView(text("Điều khiển bóng theo pixel • Không cần root",14,muted,false));
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
        space(23);
        cardHeader("2. Cài đặt mô hình AI");
        content.addView(text("Phân tích pixel và tìm điểm số cao nhất chạy trực tiếp trên máy. Astra chỉ hỗ trợ khi điểm dự đoán thấp; bot không chờ API quá lâu.",13,muted,false));
        space(10);
        endpoint=field("API base URL",store.getText("endpoint","https://api.xingchiapi.com/v1"),false);
        model=field("Model",store.getText("model","gpt-6-astra"),false);
        api=field("API key (để trống để giữ key cũ)","",true);
        aiEnabled=new CheckBox(this);
        aiEnabled.setText("Bật GPT-6 Astra hỗ trợ khi cần (gửi ảnh màn hình tới API)");
        aiEnabled.setTextColor(fg);
        aiEnabled.setChecked(store.getBoolean("ai_enabled",true));
        content.addView(aiEnabled);
        Button erase=button("Xoá API key đã lưu");
        content.addView(erase);
        erase.setOnClickListener(v -> {
            try {store.saveKey("");api.setText("");toast("Đã xoá API key");}
            catch(Exception ex) {toast("Lỗi xoá key: "+ex.getMessage());}
        });
        space(20);
        cardHeader("3. Hiệu chỉnh cho game trong ảnh");
        content.addView(text("Giữ mặc định nếu game hiển thị như ảnh mẫu. Bot quét liên tục và đợi bàn ổn định 2 khung hình sau mỗi phát bắn để tránh bắn trùng.",13,muted,false));
        space(5);
        topY=field("Tâm hàng bóng đầu (% chiều cao)",store.getText("top_pct","11.36"),false);
        launchY=field("Tâm bóng ở nòng bắn (% chiều cao)",store.getText("launch_pct","75.86"),false);
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
            GameAccessibilityService service=GameAccessibilityService.get();
            if(service==null) {toast("Bạn cần bật quyền CLICK trong Trợ năng trước");return;}
            service.openOverlay();
            toast("Mở game và nhấn ▶ trên thanh nổi để chơi. Nhấn ■ để dừng.");
        });
        space(19);
        content.addView(text("Lưu ý: Accessibility có thể đọc ảnh màn hình và tạo thao tác chạm. " +
                "Chỉ bật khi bạn dùng app. Endpoint trên là bên thứ ba, không phải máy chủ OpenAI chính thức; " +
                "hãy chỉ nhập API key mà bạn tin tưởng sử dụng với dịch vụ đó.",12,muted,false));
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
    private EditText field(String label,String value,boolean password) {
        content.addView(text(label,13,muted,false));
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(fg);e.setTextSize(14);
        e.setHintTextColor(muted);e.setBackground(bg(Color.rgb(35,47,67)));
        e.setPadding(dp(12),dp(13),dp(12),dp(13));e.setText(value);
        if(password)e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(e,new LinearLayout.LayoutParams(-1,dp(51)));
        space(9);return e;
    }
    private boolean saveSettings() {
        try {
            String root=endpoint.getText().toString().trim();
            if(!root.startsWith("https://"))throw new IllegalArgumentException("Endpoint phải sử dụng HTTPS");
            String name=model.getText().toString().trim();
            if(name.isEmpty())throw new IllegalArgumentException("Chưa nhập tên model");
            double top=Double.parseDouble(topY.getText().toString().trim());
            double launch=Double.parseDouble(launchY.getText().toString().trim());
            if(top<6 || top>24 || launch<50 || launch>91 || launch<top+23)
                throw new IllegalArgumentException("Tỷ lệ bố cục không hợp lệ");
            store.putText("endpoint",root);store.putText("model",name);
            store.putText("top_pct",String.valueOf(top));
            store.putText("launch_pct",String.valueOf(launch));
            store.putBoolean("ai_enabled",aiEnabled.isChecked());
            String newKey=api.getText().toString().trim();
            if(!newKey.isEmpty()) {store.saveKey(newKey);api.setText("");}
            toast("Đã lưu cấu hình");return true;
        } catch(Exception e) {toast("Lỗi: "+e.getMessage());return false;}
    }
    private void updateStatus() {
        boolean click=GameAccessibilityService.get()!=null;
        boolean floating=Settings.canDrawOverlays(this);
        if(permissionStatus!=null)permissionStatus.setText(
                "CLICK: "+(click?"✓ Đã bật":"✗ Chưa bật")+"     FLOAT: "+
                        (floating?"✓ Đã bật":"✗ Chưa bật"));
    }
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public void onResume(){super.onResume();updateStatus();}
}
