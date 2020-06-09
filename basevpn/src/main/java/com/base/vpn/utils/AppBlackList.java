package com.base.vpn.utils;

import java.util.ArrayList;
import java.util.List;

public class AppBlackList {
    private final static List<String> sEmailBlackList = new ArrayList<>();
    private final static List<String> sBTBlackList = new ArrayList<>();

    static {
        //email
        sEmailBlackList.add("com.android.email");//Android默认
        sEmailBlackList.add("com.google.android.gm");//Gmail
        sEmailBlackList.add("com.google.android.gm.lite");//Gmail
        sEmailBlackList.add("com.microsoft.office.outlook");//Outlook
        sEmailBlackList.add("com.yahoo.mobile.client.android.mail");//Yahoo
        sEmailBlackList.add("com.yahoo.mobile.client.android.mail.lite");//Yahoo
        sEmailBlackList.add("com.samsung.android.email.provider");//Samsung Email
        sEmailBlackList.add("com.my.mail");//myMail
        sEmailBlackList.add("com.tencent.androidqqmail");//QQ
        sEmailBlackList.add("com.netease.mail");//网易
        sEmailBlackList.add("com.netease.mobimail");//网易
        sEmailBlackList.add("com.corp21cn.mail189");//189邮箱
        sEmailBlackList.add("com.alibaba.cloudmail");//阿里邮箱
        sEmailBlackList.add("cn.cj.pe");//139邮箱
        sEmailBlackList.add("com.asiainfo.android");//沃邮箱
        sEmailBlackList.add("com.sina.mail.free");//新浪邮箱
        sEmailBlackList.add("com.sohu.mail.client.cordova");//搜狐邮箱
        sEmailBlackList.add("com.mobile.wmail");//263企业邮箱
        sEmailBlackList.add("com.smartisan.email");//锤子邮箱
        sEmailBlackList.add("com.kingsoft.email");//WPS邮箱
        sEmailBlackList.add("com.xunlei.filemail");//迅雷邮箱
        sEmailBlackList.add("cn.mailchat.campus");//邮洽
        //其他不知名APP
        sEmailBlackList.add("com.apkpure.aegon");
        sEmailBlackList.add("park.outlook.sign.in.client");
        sEmailBlackList.add("com.mail.emails");
        sEmailBlackList.add("com.syntomo.email");
        sEmailBlackList.add("com.easilydo.mail");
        sEmailBlackList.add("org.kman.AquaMail");
        sEmailBlackList.add("eu.faircode.email");
        sEmailBlackList.add("com.criptext.mail");
        sEmailBlackList.add("ch.protonmail.android");
        sEmailBlackList.add("com.pingapp.app");
        sEmailBlackList.add("me.bluemail.mail");
        sEmailBlackList.add("com.tohsoft.mail.email.emailclient");
        sEmailBlackList.add("com.cleanmail.hotmail");
        sEmailBlackList.add("com.tohsoft.mail.email.emailclient.pro");
        sEmailBlackList.add("io.cleanfox.android");
        sEmailBlackList.add("com.fsck.k9");
        sEmailBlackList.add("com.ninefolders.hd3");
        sEmailBlackList.add("com.cloudmagic.mail");
        sEmailBlackList.add("com.readdle.spark");
        sEmailBlackList.add("com.trtf.blue");
        sEmailBlackList.add("de.tutao.tutanota");
        sEmailBlackList.add("com.yomail.app");

     /**********************************************************************/
        //bt下载工具
        //迅雷
        sBTBlackList.add("com.xunlei.downloadprovider");
        //魔电
        sBTBlackList.add("com.modianxiazai");
        //闪电下载
        sBTBlackList.add("com.flash.download");
        //IMD
        sBTBlackList.add("idm.internet.download.manager.plus");
        //Flud种子客户端
        sBTBlackList.add("com.delphicoder.flud");
        //磁力种子搜索下载播放器
        sBTBlackList.add("com.xiajbsou.magnet.torrentsearchdownloadplay");
        //Torrent下载
        sBTBlackList.add("com.akingi.torrent");
        //Advanced Download Manager
        sBTBlackList.add("com.dv.adm");
        //Transmission BTC - Torrent Downloader
        sBTBlackList.add("com.ap.transmission.btc");
        //Torrent Downloader
        sBTBlackList.add("com.halle.torrentdownloader");
        //Torrent Downloader
        sBTBlackList.add("com.samp.money.carinsurance");
        //BitTorrent®- Torrent Downloads
        sBTBlackList.add("com.bittorrent.client");
        //磁力下载器BT
        sBTBlackList.add("com.better.app.torrent");
        //WeTorrent
        sBTBlackList.add("co.we.torrent");
        //Vuze Torrent Downloader
        sBTBlackList.add("com.vuze.torrent.downloader");
        //磁力player
        sBTBlackList.add("cn.sddman.downloadllsts");
        //大鱼影视
        sBTBlackList.add("cn.babayu.hotvideo");
        //BT彗星
        sBTBlackList.add("com.bt.star");
        //Checketry
        sBTBlackList.add("com.checketry.downloadmanager");
        //FrostWire
        sBTBlackList.add("com.frostwire.android");
        //LibreTorrent
        sBTBlackList.add("org.proninyaroslav.libretorrent");
        //TorrDroid
        sBTBlackList.add("intelligems.torrdroid");
        //Transdrone
        sBTBlackList.add("org.transdroid.lite");
        //Torrent Lite
        sBTBlackList.add("thu.tagsoft.ttorrent.lite");
    }

    public static List<String> getAppList() {
        List<String> list = new ArrayList<>(sEmailBlackList);
        list.addAll(sBTBlackList);
        return list;
    }

    public static List<String> getEmailBlackList() {
        return sEmailBlackList;
    }

    public static List<String> getBTBlackList() {
        return sBTBlackList;
    }

    public static boolean containerAppList(String packName){
        return inBTBlackList(packName) || inEmailBlackList(packName);
    }

    public static boolean inEmailBlackList(String packName){
        return sEmailBlackList.contains(packName);
    }

    public static boolean inBTBlackList(String packName){
        return sBTBlackList.contains(packName);
    }
}
