package com.base.vpn;

import android.content.res.Resources;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.HandlerThread;

import com.common.utils.TimesUtils;

import java.util.Vector;

public final class NetTraffics {
    private long preTxBytes;
    private long preRxBytes;
    private long preIn;
    private long preOut;

    private Vector<IVPN.ByteCountListener> byteCountListener;
    private Handler mHandler;
    private RunTraffics runTraffics;
    private HandlerThread thread;

    private final static class Holder{
        private static final NetTraffics INSTANCE = new NetTraffics();
    }

    private NetTraffics(){
        byteCountListener = new Vector<>();
        preRxBytes = TrafficStats.getTotalRxBytes();
        preTxBytes = TrafficStats.getTotalTxBytes();
        thread = new HandlerThread("net traffics");
        thread.start();
        init();
    }

    private void init() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        mHandler = new Handler(thread.getLooper());
        runTraffics = new RunTraffics();
        mHandler.post(runTraffics);
    }

    private void process(){
        try {
            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();
            long diffin = (rx - preIn);
            long diffout = (tx - preOut);
            preIn = rx;
            preOut = tx;
            long in = rx - preRxBytes;
            long out = tx - preTxBytes;
            update(in, out, diffin, diffout);
            //Log.w("VPN22","net traffics");
            TimesUtils.sleep(1000);
            mHandler.removeCallbacksAndMessages(null);
        }catch (Throwable e){
            e.printStackTrace();
        }finally {
            mHandler.postDelayed(runTraffics,100);
        }
    }
    class RunTraffics implements Runnable{
        @Override
        public void run() {
            process();
        }
    }

    public static NetTraffics getInstance(){
        return Holder.INSTANCE;
    }

    public void reset(){
        preRxBytes = TrafficStats.getTotalRxBytes();
        preTxBytes = TrafficStats.getTotalTxBytes();
    }

    private void update(long in, long out, long diffin, long diffout) {
        for(IVPN.ByteCountListener l:byteCountListener){
            try {
                l.updateByteCount(in, out, diffin, diffout);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public long getTotalTx(){
        return TrafficStats.getTotalTxBytes() - preTxBytes;
    }

    public long getTotalRx(){
        return TrafficStats.getTotalRxBytes() - preRxBytes;
    }

    public void addByteCountListener(IVPN.ByteCountListener listener) {
        synchronized (getInstance()){
            if (listener != null && !byteCountListener.contains(listener)) {
                byteCountListener.add(listener);
            }
        }
    }

    public void removeByteByteCountListener(IVPN.ByteCountListener listener) {
        synchronized (getInstance()){
            if (listener != null) {
                byteCountListener.remove(listener);
            }
        }
    }
    public void removeAllListener(){
        byteCountListener.clear();
    }


    public static String humanReadableByteCount(long bytes, boolean speed, Resources res) {
        if (speed) {
            bytes = bytes * 8;
        }
        int unit = speed ? 1000 : 1024;
        int exp = Math.max(0, Math.min((int) (Math.log(bytes) / Math.log(unit)), 3));
        float bytesUnit = (float) (bytes / Math.pow(unit, exp));
        if (speed)
            switch (exp) {
                case 0:
                    return res.getString(R.string.bits_per_second, bytesUnit);
                case 1:
                    return res.getString(R.string.kbits_per_second, bytesUnit);
                case 2:
                    return res.getString(R.string.mbits_per_second, bytesUnit);
                default:
                    return res.getString(R.string.gbits_per_second, bytesUnit);
            }
        else
            switch (exp) {
                case 0:
                    return res.getString(R.string.volume_byte, bytesUnit);
                case 1:
                    return res.getString(R.string.volume_kbyte, bytesUnit);
                case 2:
                    return res.getString(R.string.volume_mbyte, bytesUnit);
                default:
                    return res.getString(R.string.volume_gbyte, bytesUnit);
            }
    }
}
