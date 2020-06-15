/*
 * Copyright (C) 2014 Tobias Brunner
 * HSR Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.ikev2.android.logic;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import org.ikev2.android.security.LocalCertificateKeyStoreProvider;

import java.security.Security;

public class StrongSwanApplication extends Application
{
	private static Context mContext;

	static {
		Security.addProvider(new LocalCertificateKeyStoreProvider());
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		StrongSwanApplication.mContext = getApplicationContext();
	}

	/**
	 * Returns the current application context
	 * @return context
	 */
	public static Context getContext()
	{
		return StrongSwanApplication.mContext;
	}

	private static final boolean USE_BYOD = true;

	/*
	 * The libraries are extracted to /data/data/org.ikev2.android/...
	 * during installation.  On newer releases most are loaded in JNI_OnLoad.
	 */
	static
	{
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
		{
			System.loadLibrary("strongswan");

			if (USE_BYOD)
			{
				System.loadLibrary("tpmtss");
				System.loadLibrary("tncif");
				System.loadLibrary("");
				System.loadLibrary("imcv");
			}

			System.loadLibrary("charon");
			System.loadLibrary("ipsec");
		}
		System.loadLibrary("androidbridge");
	}
}
