/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.blinkt.openvpn.core;

import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.InvalidKeyException;

public class NativeUtils {

    public static native byte[] encryptData(byte[] data);
    public static native byte[] decryptData(byte[] data);

    public static native byte[] rsasign(byte[] input, int pkey) throws InvalidKeyException;

    public static native String[] getIfconfig() throws IllegalArgumentException;

    static native void jniclose(int fdint);

    private static native String getJNIAPI();

    public static native String getOpenVPN2GitVersion();

    public static native double[] getOpenSSLSpeed(String algorithm, int testnum);

    public static String getNativeAPI() {
        if (isRoboUnitTest())
            return "ROBO";
        else
            return getJNIAPI();
    }

    public final static int[] openSSLlengths = {
            16, 64, 256, 1024, 8 * 1024, 16 * 1024
    };

    static {
        if (!isRoboUnitTest()) {
            System.loadLibrary("opvpnutil");
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                System.loadLibrary("jbcrypto");
            }
        }
    }

    public static boolean isRoboUnitTest() {
        return "robolectric".equals(Build.FINGERPRINT);
    }

    public static byte[] encryptString(String str) {
        return encryptData(str.getBytes());
    }

    public static String decryptString(byte[] data) {
        byte[] decode = decryptData(data);
        return new String(decode);
    }

    public static void encryptStream(FileInputStream inputStream, FileOutputStream outputStream) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(inputStream.available());
            FileChannel channel = inputStream.getChannel();
            channel.read(byteBuffer);
            channel.close();
            byte[] enc = encryptData(byteBuffer.array());
            channel = outputStream.getChannel();
            byteBuffer = ByteBuffer.allocate(enc.length);
            byteBuffer.put(enc);
            byteBuffer.flip();
            channel.write(byteBuffer);
            channel.close();
            outputStream.flush();
        } catch (Exception e) {
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                }
            }
        }
    }

    public static byte[] encryptStream(InputStream inputStream) {
        try {
            if (inputStream == null){
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] data = new byte[2048];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                outputStream.write(data, 0, read);
            }
            outputStream.flush();
            data = outputStream.toByteArray();
            outputStream.close();
            inputStream.close();
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] decryptStream(File inFile) {
        try {
            return decryptStream(new FileInputStream(inFile));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decryptStream(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try {
            byte[] byteBuffer = new byte[inputStream.available()];
            inputStream.read(byteBuffer);
            return decryptData(byteBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                }
            }
        }
        return null;
    }
}
