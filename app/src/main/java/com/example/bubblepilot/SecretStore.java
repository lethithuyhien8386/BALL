package com.example.bubblepilot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** API token stored encrypted at rest with an Android Keystore AES-GCM key. */
public final class SecretStore {
    private static final String ALIAS = "bubblepilot_api_key_v1";
    private final SharedPreferences prefs;

    public SecretStore(Context context) {
        prefs = context.getSharedPreferences("bubblepilot_settings", Context.MODE_PRIVATE);
    }

    public void putText(String field, String value) {
        prefs.edit().putString(field, value).apply();
    }

    public String getText(String field, String fallback) {
        return prefs.getString(field, fallback);
    }

    public void putBoolean(String field, boolean value) {
        prefs.edit().putBoolean(field, value).apply();
    }

    public boolean getBoolean(String field, boolean fallback) {
        return prefs.getBoolean(field, fallback);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry = store.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec params = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build();
        generator.init(params);
        return generator.generateKey();
    }

    public void saveKey(String value) throws Exception {
        if (value.isEmpty()) {
            prefs.edit().remove("api_iv").remove("api_cipher").apply();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString("api_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("api_cipher", Base64.encodeToString(cipherText, Base64.NO_WRAP))
                .apply();
    }

    public String readKey() {
        String cipherText = prefs.getString("api_cipher", "");
        String iv = prefs.getString("api_iv", "");
        if (cipherText.isEmpty() || iv.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ""; // Invalidated keystore key: user must re-enter token.
        }
    }
}
