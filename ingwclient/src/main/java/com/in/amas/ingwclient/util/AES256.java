package com.in.amas.ingwclient.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AES256 {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private final SecretKeySpec secretKey;
    private final IvParameterSpec iv;

    /**
     * @param key 32바이트(256비트) 비밀키 (예: "0123456789abcdef0123456789abcdef")
     * @param iv 16바이트 초기화 벡터 (예: "abcdef9876543210")
     */
    public AES256(String key, String iv) {
        if (key == null || key.length() != 32) {
            throw new IllegalArgumentException("Key length must be 32 characters (256 bits)");
        }
        if (iv == null || iv.length() != 16) {
            throw new IllegalArgumentException("IV length must be 16 characters (128 bits)");
        }
        this.secretKey = new SecretKeySpec(key.getBytes(), "AES");
        this.iv = new IvParameterSpec(iv.getBytes());
    }

    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        byte[] decodedBytes = Base64.getDecoder().decode(cipherText);
        byte[] decrypted = cipher.doFinal(decodedBytes);
        return new String(decrypted, "UTF-8");
    }
}
