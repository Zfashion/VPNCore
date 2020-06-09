package com.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Set;

public class DefaultDataStore implements IDataStore {

    private final static String DEFAULT_PREFERENCES_NAME = "default_data_store";
    private SharedPreferences mPreferences;

    public DefaultDataStore(@NonNull Context mContext) {
        mPreferences = mContext.getSharedPreferences(DEFAULT_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public boolean putInt(String key, int data) {
        return mPreferences.edit().putInt(key, data).commit();
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return mPreferences.getInt(key, defaultValue);
    }

    @Override
    public boolean putString(String key, String data) {
        return mPreferences.edit().putString(key, data).commit();
    }

    @Override
    public String getString(String key, String defaultValue) {
        return mPreferences.getString(key, defaultValue);
    }

    @Override
    public boolean putLong(String key, long data) {
        return mPreferences.edit().putLong(key, data).commit();
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return mPreferences.getLong(key, defaultValue);
    }

    @Override
    public boolean putFloat(String key, float data) {
        return mPreferences.edit().putFloat(key, data).commit();
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return mPreferences.getFloat(key, defaultValue);
    }

    @Override
    public boolean putBoolean(String key, boolean data) {
        return mPreferences.edit().putBoolean(key, data).commit();
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return mPreferences.getBoolean(key, defaultValue);
    }

    @Override
    public boolean putStringSet(String key, Set<String> strings) {
        return mPreferences.edit().putStringSet(key, strings).commit();
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defaultValue) {
        return mPreferences.getStringSet(key, defaultValue);
    }

    @Override
    public boolean contains(String key) {
        return mPreferences.contains(key);
    }

    @Override
    public boolean remove(String key) {
        return mPreferences.edit().remove(key).commit();
    }

    @Override
    public boolean clear() {
        return mPreferences.edit().clear().commit();
    }
}
