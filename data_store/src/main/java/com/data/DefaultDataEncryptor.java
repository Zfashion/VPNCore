package com.data;

public class DefaultDataEncryptor implements IDataEncryptor {
    @Override
    public byte[] encode(byte[] inData, byte[] key) {
        return inData;
    }

    @Override
    public byte[] decode(byte[] inData, byte[] key) {
        return inData;
    }
}
