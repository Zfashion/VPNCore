package com.data;

import android.content.Context;
import android.text.TextUtils;

import androidx.collection.ArraySet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DataStore implements IDataStore {

    private String STRING_SET_LINE_TAG = "<line>\n";
    private String DATA_TYPE_INT = "_int";
    private String DATA_TYPE_LONG = "_long";
    private String DATA_TYPE_FLOAT = "_float";
    private String DATA_TYPE_BOOLEAN = "_boolean";
    private String DATA_TYPE_SET = "_set";

    private Context mContext;
    private IDataStore mDataStore;
    private IDataEncryptor mEncryptor;
    private byte[] mEncryptorKey;

    private DataStore() {
    }

    DataStore(Builder builder) {
        mContext = builder.context;
        mDataStore = builder.dataSource;
        mEncryptor = builder.encryptor;
        mEncryptorKey = builder.encryptorKey;
    }

    @Override
    public boolean putInt(String key, int data) {
        return putData(assembleInternalKey(key, DATA_TYPE_INT), data);
    }

    @Override
    public boolean putLong(String key, long data) {
        return putData(assembleInternalKey(key, DATA_TYPE_LONG), data);
    }

    @Override
    public boolean putFloat(String key, float data) {
        return putData(assembleInternalKey(key, DATA_TYPE_FLOAT), data);
    }

    @Override
    public boolean putBoolean(String key, boolean data) {
        return putData(assembleInternalKey(key, DATA_TYPE_BOOLEAN), data ? 1 : 0);
    }

    @Override
    public boolean putString(String key, String data) {
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            bytes = mEncryptor.encode(bytes, mEncryptorKey);
            return mDataStore.putString(key, new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }


    @Override
    public int getInt(String key, int defaultValue) {
        return getNumericalData(assembleInternalKey(key, DATA_TYPE_INT), defaultValue);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return getNumericalData(assembleInternalKey(key, DATA_TYPE_LONG), defaultValue);
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return getNumericalData(assembleInternalKey(key, DATA_TYPE_FLOAT), defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return getNumericalData(assembleInternalKey(key, DATA_TYPE_BOOLEAN), defaultValue ? 1 : 0) == 1;
    }

    @Override
    public boolean putStringSet(String key, Set<String> strings) {
        if (strings != null && strings.size() > 0) {
            String str = TextUtils.join(STRING_SET_LINE_TAG, strings.toArray());
            return putString(assembleInternalKey(key, DATA_TYPE_SET), str);
        }
        return false;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defaultValue) {
        key = assembleInternalKey(key, DATA_TYPE_SET);
        if (mDataStore.contains(key)) {
            String str = getString(key, null);
            if (!TextUtils.isEmpty(str)) {
                String[] strings = str.split(STRING_SET_LINE_TAG);
                //noinspection ConstantConditions
                if (strings != null && strings.length > 0) {
                    return new ArraySet<>(Arrays.asList(strings));
                }
            }
        }
        return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
        try {
            if (mDataStore.contains(key)) {
                String str = mDataStore.getString(key, defaultValue);
                if (!TextUtils.equals(defaultValue, str)) {
                    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
                    bytes = mEncryptor.decode(bytes, mEncryptorKey);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return defaultValue;
    }

    @Override
    public boolean contains(String originKey) {
        List<String> keys = new ArrayList<>();
        keys.add(assembleInternalKey(originKey, DATA_TYPE_INT));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_LONG));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_FLOAT));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_BOOLEAN));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_SET));
        for (String key : keys) {
            if (mDataStore.contains(key)) {
                return true;
            }
        }
        return mDataStore.remove(originKey);
    }

    @Override
    public boolean remove(String originKey) {
        List<String> keys = new ArrayList<>();
        keys.add(assembleInternalKey(originKey, DATA_TYPE_INT));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_LONG));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_FLOAT));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_BOOLEAN));
        keys.add(assembleInternalKey(originKey, DATA_TYPE_SET));
        for (String key : keys) {
            if (mDataStore.contains(key)){
                return mDataStore.remove(key);
            }
        }
        return mDataStore.remove(originKey);
    }

    @Override
    public boolean clear() {
        return mDataStore.clear();
    }

    /**
     * @param key
     * @param number
     * @return
     */
    private boolean putData(String key, Number number) {
        try {
            String str = null;
            if (number instanceof Integer) {
                str = String.valueOf(number.intValue());
            } else if (number instanceof Long) {
                str = String.valueOf(number.longValue());
            } else if (number instanceof Float) {
                str = String.valueOf(number.floatValue());
            } else if (number instanceof Double) {
                str = String.valueOf(number.doubleValue());
            }
            if (!TextUtils.isEmpty(str)) {
                return putString(key, str);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return false;
    }

    private <T extends Number> T getNumericalData(String key, T defaultValue) {
        try {
            if (mDataStore.contains(key)) {
                String str = mDataStore.getString(key, null);
                if (!TextUtils.isEmpty(str)) {
                    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
                    bytes = mEncryptor.encode(bytes, mEncryptorKey);
                    str = new String(bytes, StandardCharsets.UTF_8);
                    Number value = defaultValue;
                    if (defaultValue instanceof Integer) {
                        value = Integer.parseInt(str);
                    }
                    if (defaultValue instanceof Long) {
                        value = Long.parseLong(str);
                    }
                    if (defaultValue instanceof Float) {
                        value = Float.parseFloat(str);
                    }
                    if (defaultValue instanceof Double) {
                        value = Double.parseDouble(str);
                    }
                    //noinspection unchecked
                    return (T) value;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    private String assembleInternalKey(String originKey, String type) {
        return String.format("%s%s", originKey, type);
    }

    public static class Builder {
        Context context;
        IDataEncryptor encryptor;
        IDataStore dataSource;
        byte[] encryptorKey;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder dataStore(IDataStore dataStore) {
            this.dataSource = dataStore;
            return this;
        }

        public Builder encryptor(IDataEncryptor encryptor) {
            this.encryptor = encryptor;
            return this;
        }

        public Builder encryptorKey(byte[] encryptorKey) {
            this.encryptorKey = encryptorKey;
            return this;
        }

        public DataStore build() {
            if (dataSource == null) {
                dataSource = new DefaultDataStore(context);
            }
            if (encryptor == null) {
                encryptor = new DefaultDataEncryptor();
            }
            return new DataStore(this);
        }
    }
}
