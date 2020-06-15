package com.data;

import java.util.Set;

public interface IDataStore {

    boolean putInt(String key, int data);

    int getInt(String key, int defaultValue);


    boolean putString(String key, String data);

    String getString(String key, String defaultValue);


    boolean putLong(String key, long data);

    long getLong(String key, long defaultValue);


    boolean putFloat(String key, float data);

    float getFloat(String key, float defaultValue);


    boolean putBoolean(String key, boolean data);

    boolean getBoolean(String key, boolean defaultValue);


    boolean putStringSet(String key, Set<String> strings);

    Set<String> getStringSet(String key, Set<String> defaultValue);

    boolean contains(String key);

    boolean remove(String key);

    boolean clear();


    class DataStoreFactory{
        // TODO: 2020-06-09
    }

    class EncryptorFactory{
        // TODO: 2020-06-09
    }
}
