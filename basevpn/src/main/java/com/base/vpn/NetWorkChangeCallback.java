package com.base.vpn;

import android.content.Context;

public interface NetWorkChangeCallback {

    void networkChange(boolean available);

    Context getContext();
}
