package com.module.vpncore.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.base.vpn.IVPN;
import com.base.vpn.VPNConfig;
import com.module.vpncore.R;
import com.module.vpncore.vpn.IVPNOperation;
import com.module.vpncore.vpn.VPNInstance;

public class MainActivity extends AppCompatActivity implements IVPN.VPNCallback {

    /**
     * {
     * "alisa_name":"New York 1",
     * "bUdp":false,
     * "bVip":false,
     * "city":"New York",
     * "country":"US",
     * "countryName":"USA",
     * "group_name":"US New York",
     * "icon":"us",
     * "id":1533312970,
     * "ikevPorts":[
     * "500",
     * "68",
     * "4500"
     * ],
     * "ip":"45.55.58.202",
     * "pro":true,
     * "userSelect":false,
     * "load":1,
     * "name":"USA New York 1",
     * "node":"New York 1",
     * "osType":1,
     * "pingTime":250,
     * "tcpPorts":[
     * "443",
     * "102",
     * "8080"
     * ],
     * "udpPorts":[
     * "110",
     * "119",
     * "800"
     * ]
     * }
     *
     */

    /**
     * {
     * "alisa_name":"Singapore 11",
     * "bUdp":false,
     * "bVip":false,
     * "city":"Singapore",
     * "country":"SG",
     * "countryName":"Singapore",
     * "group_name":"SG Singapore",
     * "icon":"singapore",
     * "id":-986527822,
     * "ikevPorts":[
     * "500",
     * "68",
     * "4500"
     * ],
     * "ip":"81.90.188.100",
     * "pro":false,
     * "userSelect":true,
     * "load":23,
     * "name":"Singapore Singapore 11",
     * "node":"Singapore 11",
     * "osType":3,
     * "pingTime":188,
     * "tcpPorts":[
     * "443",
     * "102",
     * "8080"
     * ],
     * "udpPorts":[
     * "110",
     * "119",
     * "800"
     * ]
     * }
     */
    private IVPN.VPNState mCurrentState;

    private EditText mInputIP;
    private EditText mInputPort;
    private TextView mState;
    private RadioGroup mRadioGroup;
    private Button mConnect;
    private Button mDisconnect;
    private Button mAppProxy;

    private static final int UDP_TYPE = 0;
    private static final int TCP_TYPE = 1;
    private static final int IKEV2_TYPE = 2;
    int mVPNType = UDP_TYPE;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mInputIP = findViewById(R.id.ip);
        mInputPort = findViewById(R.id.port);
        mRadioGroup = findViewById(R.id.vpn_type);

        mConnect = findViewById(R.id.connect);
        mDisconnect = findViewById(R.id.disconnect);
        mState = findViewById(R.id.state);
        mAppProxy = findViewById(R.id.app_proxy);

        mInputIP.setText(getCacheIP());
        mInputPort.setText(getCachePort());

        initVPNType();
    }

    private void initVPNType() {
        mRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.tcp:
                    mVPNType = TCP_TYPE;
                    VPNInstance.get().setVPNType(IVPNOperation.TYPE_OPEN_VPN);
                    break;
                case R.id.udp:
                    mVPNType = UDP_TYPE;
                    VPNInstance.get().setVPNType(IVPNOperation.TYPE_OPEN_VPN);
                    break;
                case R.id.ikev2:
                    mVPNType = IKEV2_TYPE;
                    VPNInstance.get().setVPNType(IVPNOperation.TYPE_IKEV2_VPN);
                    break;
            }
        });
        mVPNType = getCacheVPNType();
        switch (mVPNType) {
            case TCP_TYPE:
                mRadioGroup.check(R.id.tcp);
                VPNInstance.get().setVPNType(IVPNOperation.TYPE_OPEN_VPN);
                break;
            case UDP_TYPE:
                mRadioGroup.check(R.id.udp);
                VPNInstance.get().setVPNType(IVPNOperation.TYPE_OPEN_VPN);
                break;
            case IKEV2_TYPE:
                mRadioGroup.check(R.id.ikev2);
                VPNInstance.get().setVPNType(IVPNOperation.TYPE_IKEV2_VPN);
                break;
        }

    }

    public void connect(View view) {
        String ip = mInputIP.getText().toString();
        String port = mInputPort.getText().toString();
        if (TextUtils.isEmpty(ip) || TextUtils.isEmpty(port)) {
            Toast.makeText(this, "端口或者IP不能为空啊 兄嘚!!!!!!", Toast.LENGTH_SHORT).show();
            return;
        }
        saveIP(ip);
        savePort(port);
        saveType(mVPNType);
        Bundle bundle = new Bundle();
        bundle.putString(IVPNOperation.EXTRA_IP, ip);
        bundle.putString(IVPNOperation.EXTRA_PORT, port);
        bundle.putString(IVPNOperation.EXTRA_SERVER_NODE_NAME, "Test Server Node");

        IVPNOperation operation = VPNInstance.get();
        bundle.putBoolean(IVPNOperation.EXTRA_OPENVPN_UDP, mRadioGroup.getCheckedRadioButtonId() == R.id.udp);
        operation.attach(this);
        operation.addIVPNCallBack(this);
        operation.connect(bundle);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        VPNInstance.get().onActivityResult(requestCode, resultCode, data);
    }

    public void disconnect(View view) {
        VPNInstance.get().addIVPNCallBack(this);
        VPNInstance.get().disconnect();
    }

    private boolean isServerReply = false;

    @Override
    public void stateChange(IVPN.VPNState state) {
        mCurrentState = state;
        runOnUiThread(() -> {
            mState.setText(state.name());
            switch (mCurrentState) {
                case CONNECTING:
                case CONNECTED:
                    isServerReply = true;
                    enableView(false);
                    break;
                case CONNECT_FAIL:
                    isServerReply = false;
                    enableView(false);
                    break;
                case DISCONNECTED:
                    if (!isServerReply) {
                        stateChange(IVPN.VPNState.RECONNECT);
                        return;
                    }
                    enableView(true);
                    isServerReply = false;
                    break;
                case RECONNECT:
//                    1.如果有端口就切换端口 重连
//                    mPort = xxxx各种方式得到端口
//                    enableView(false);
//                    startConnect();
//                    return;
//                    2.没有可用端口 就拜拜
                    mState.setText(IVPN.VPNState.DISCONNECTED.name());
                    isServerReply = false;
                    enableView(true);
                    break;
            }
        });
    }

    @Override
    public void message(String msg) {

    }


    private void enableView(boolean enable) {
        int count = mRadioGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            mRadioGroup.getChildAt(i).setEnabled(enable);
        }
        mInputPort.setEnabled(enable);
        mInputIP.setEnabled(enable);
        mAppProxy.setEnabled(enable);
        mConnect.setEnabled(enable);
        mDisconnect.setEnabled(!enable);
    }

    private String getCachePort() {
        return VPNConfig.dataStore.getString("port", "");
    }

    private String getCacheIP() {
        return VPNConfig.dataStore.getString("ip", "");
    }

    private int getCacheVPNType() {
        return VPNConfig.dataStore.getInt("vpn_type", UDP_TYPE);
    }

    private void saveIP(String ip) {
        VPNConfig.dataStore.putString("ip", ip);
    }

    private void savePort(String port) {
        VPNConfig.dataStore.putString("port", port);
    }

    private void saveType(int type) {
        VPNConfig.dataStore.putInt("vpn_type", type);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VPNInstance.get().detach();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (VPNInstance.get().isConnecting() || VPNInstance.get().isConnected()) {
            enableView(false);
        }
        mState.setText(VPNInstance.get().getState().name());
    }

    public void appProxy(View view) {
        startActivity(new Intent(this,AppProxyActivity.class));
    }
}
