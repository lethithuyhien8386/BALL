package com.example.bubblepilot;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional HTTPS vision fallback, invoked only for uncertain local shots. */
public final class RemoteAdvisor {
    public interface Callback { void onResult(long signature, Point point, String status); }
    public static final class Point {
        public final int x,y;
        Point(int x,int y) {this.x=x;this.y=y;}
    }
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);

    public boolean request(Bitmap frame,long signature,String endpoint,String model,
                           String key,Callback callback) {
        if(key==null || key.isEmpty() || !busy.compareAndSet(false,true))return false;
        // A detached reduced-size copy prevents the UI's bitmap lifecycle from racing requests.
        Bitmap resized=Bitmap.createScaledBitmap(frame,
                Math.min(384,frame.getWidth()),
                Math.max(1,(int)(frame.getHeight()*Math.min(384,frame.getWidth())/(double)frame.getWidth())),true);
        // createScaledBitmap may return the original object when dimensions match.
        final Bitmap scaled=resized==frame?frame.copy(Bitmap.Config.ARGB_8888,false):resized;
        final int originalW=frame.getWidth(), originalH=frame.getHeight();
        executor.execute(() -> {
            Point point=null;String status="API không khả dụng";
            try {
                if(!endpoint.startsWith("https://"))throw new IllegalArgumentException("Chỉ hỗ trợ HTTPS");
                // Do not send anything to a URL with query/fragment or embedded credentials.
                URL base=new URL(endpoint);
                if(base.getUserInfo()!=null || base.getQuery()!=null || base.getRef()!=null)
                    throw new IllegalArgumentException("Endpoint không hợp lệ");
                String root=endpoint.endsWith("/")?endpoint.substring(0,endpoint.length()-1):endpoint;
                URL url=new URL(root+"/responses");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG,72,out);
                String b64=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
                JSONObject image=new JSONObject()
                        .put("type","input_image")
                        .put("image_url","data:image/jpeg;base64,"+b64);
                JSONObject instruction=new JSONObject()
                        .put("type","input_text")
                        .put("text","You are analyzing a bubble shooter game screen. " +
                                "Choose the tap location that aims the current projectile into " +
                                "a large cluster of the SAME color, accounting for any walls " +
                                "and avoiding balls blocking other balls. " +
                                "The screenshot was resized for transmission, but return " +
                                "coordinates in the ORIGINAL screen size " +originalW+"x"+originalH+". " +
                                "Reply ONLY with a JSON object {\"x\":number,\"y\":number} " +
                                "representing a tap target ABOVE the shooter. No other text.");
                JSONObject prompt=new JSONObject()
                        .put("role","user")
                        .put("content",new JSONArray().put(instruction).put(image));
                JSONObject json=new JSONObject()
                        .put("model",model)
                        .put("store",false)
                        .put("max_output_tokens",150)
                        .put("input",new JSONArray().put(prompt));
                HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(2500);
                    connection.setReadTimeout(4500);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type","application/json");
                    connection.setRequestProperty("Authorization","Bearer "+key);
                    connection.setRequestProperty("x-openai-actor-authorization","local-image-extension");
                    try(OutputStream stream=connection.getOutputStream()) {
                        stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code=connection.getResponseCode();
                    InputStream stream=code>=200 && code<300?
                            connection.getInputStream():connection.getErrorStream();
                    String content;
                    try(InputStream body=stream) {
                        if(body==null)throw new IllegalStateException("HTTP "+code);
                        byte[] bytes=new byte[1048576];
                        int size=0,n;
                        while(size<bytes.length && (n=body.read(bytes,size,bytes.length-size))>0)size+=n;
                        content=new String(bytes,0,size,StandardCharsets.UTF_8);
                    }
                    if(code<200 || code>=300) throw new IllegalStateException("HTTP "+code);
                    JSONObject response=new JSONObject(content);
                    String answer=response.optString("output_text","");
                    if(answer.isEmpty()) {
                        JSONArray items=response.optJSONArray("output");
                        if(items!=null)for(int i=0;i<items.length();i++) {
                            JSONArray contents=items.optJSONObject(i).optJSONArray("content");
                            if(contents!=null)for(int k=0;k<contents.length();k++) {
                                JSONObject part=contents.optJSONObject(k);
                                if(part!=null)answer+=part.optString("text","");
                            }
                        }
                    }
                    int start=answer.indexOf('{'),end=answer.lastIndexOf('}');
                    if(start<0 || end<=start)throw new IllegalStateException("Sai định dạng JSON");
                    JSONObject coords=new JSONObject(answer.substring(start,end+1));
                    int x=coords.getInt("x"),y=coords.getInt("y");
                    if(x<2 || x>=originalW-2 || y<originalH*.085 || y>=originalH*.74)
                        throw new IllegalStateException("Toạ độ AI nằm ngoài sân");
                    point=new Point(x,y);status="API OK";
                } finally {connection.disconnect();}
            } catch (Exception e) { status=e.getMessage()==null?"API lỗi":e.getMessage(); }
            finally {
                scaled.recycle();
                busy.set(false);
            }
            final Point result=point;
            final String info=status;
            main.post(() -> callback.onResult(signature,result,info));
        });
        return true;
    }
    public void close() {executor.shutdownNow();}
}
