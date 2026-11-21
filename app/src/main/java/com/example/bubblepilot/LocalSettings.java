package com.example.bubblepilot;
import android.content.Context;
import android.content.SharedPreferences;
import java.security.KeyStore;
/** Local geometry settings only. Removes obsolete credentials from upgrades. */
final class LocalSettings {
    private final SharedPreferences prefs;
    LocalSettings(Context context) {
        prefs=context.getSharedPreferences("bubblepilot_settings",Context.MODE_PRIVATE);
        if(prefs.contains("api_cipher") || prefs.contains("api_iv") || prefs.contains("endpoint") || prefs.contains("model") || prefs.contains("ai_enabled")) {
            prefs.edit().remove("api_cipher").remove("api_iv").remove("endpoint").remove("model").remove("ai_enabled").apply();
            try {KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry("bubblepilot_api_key_v1");} catch(Exception ignored){}
        }
    }
    String getText(String key,String fallback){return prefs.getString(key,fallback);}
    void putText(String key,String val){prefs.edit().putString(key,val).apply();}
}
