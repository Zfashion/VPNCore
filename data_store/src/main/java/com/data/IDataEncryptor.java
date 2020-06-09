package com.data;

public interface IDataEncryptor {

    byte[] encode(byte[] inData, byte[] key);

    byte[] decode(byte[] inData, byte[] key);
}
