//
// Created by tml on 2019/3/7.
//
#include <sstream>

extern "C" {
#include <jni.h>
//#include <stdio.h>
# include <openssl/aes.h>
}

#define KEY "3ac5f19cd7378a12"
#define IV  "10af4d2f3a5ba8ed"

#define KEY_BIT 128
/*


//把字符串转成十六进制字符串
std::string char2hex(std::string s) {
    std::string ret;
    for (unsigned i = 0; i != s.size(); ++i) {
        char hex[5];
        sprintf(hex, "%.2x", (unsigned char) s[i]);
        ret += hex;
    }
    return ret;
}

//把十六进制字符串转成字符串
std::string hex2char(std::string s) {
    std::string ret;
    int length = (int) s.length();
    for (int i = 0; i < length; i += 2) {
        std::string buf = "0x" + s.substr(i, 2);
        unsigned int value;
        sscanf(buf.c_str(), "%x", &value);
        ret += ((char) value);
    }
    return ret;
}

int hexCharToInt(char c) {
    if (c >= '0' && c <= '9') return (c - '0');
    if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
    if (c >= 'a' && c <= 'f') return (c - 'a' + 10);
    return 0;
}

//十六进制字符串转成十六进制数组
char *hexstringToBytes(std::string s) {
    int sz = (int) s.length();
    char *ret = new char[sz / 2];
    for (int i = 0; i < sz; i += 2) {
        ret[i / 2] = (char) ((hexCharToInt(s.at(i)) << 4) | hexCharToInt(s.at(i + 1)));
    }
    return ret;
}

//十六进制数组转成十六进制字符串
std::string bytestohexstring(char *bytes, int bytelength) {
    std::string str("");
    std::string str2("0123456789abcdef");
    for (int i = 0; i < bytelength; ++i) {
        int b;
        b = 0x0f & (bytes[i] >> 4);
        char s1 = str2.at(b);
        str.append(1, str2.at(b));
        b = 0x0f & bytes[i];
        str.append(1, str2.at(b));
//        char s2 = str2.at(b);
    }
    return str;
}

//加密
std::string EncodeAES(const unsigned char *master_key, std::string data, const unsigned char *iv) {
    AES_KEY key;
    AES_set_encrypt_key(master_key, KEY_BIT, &key);

    unsigned char ivc[AES_BLOCK_SIZE];

    std::string data_bak = data.c_str();
    unsigned int data_length = (unsigned int) data_bak.length();
    int padding = 0;
    if (data_bak.length() % AES_BLOCK_SIZE >= 0) {
        padding = (int) (AES_BLOCK_SIZE - data_bak.length() % AES_BLOCK_SIZE);
    }
    data_length += padding;
    while (padding > 0) {
        data_bak += '\0';
        padding--;
    }

    memcpy(ivc, iv, AES_BLOCK_SIZE * sizeof(char));
    std::string encryhex;
    for (unsigned int i = 0; i < data_length / AES_BLOCK_SIZE; i++) {
        std::string str16 = data_bak.substr(i * AES_BLOCK_SIZE, AES_BLOCK_SIZE);
        unsigned char out[AES_BLOCK_SIZE];
        memset(out, 0, AES_BLOCK_SIZE);
        AES_cbc_encrypt((const unsigned char *) str16.c_str(), out, 16, &key, ivc, AES_ENCRYPT);
        encryhex += bytestohexstring((char *) out, AES_BLOCK_SIZE);
    }
    return encryhex;

}
*/

typedef struct{
    unsigned char *data;
    unsigned int len;
}OUT_DATA;

void pkcs7padding(unsigned char * data,unsigned int paddinglen,unsigned int len){
    unsigned char padding_data = AES_BLOCK_SIZE - (len%AES_BLOCK_SIZE);

    for ( unsigned int index = len; index < paddinglen; index++ ){
        data[index] = padding_data;
    }
}

unsigned int pkcs7unpadding(unsigned char * data,unsigned int len){
    unsigned int real_len;
    real_len = len - data[len-1];
    return real_len;
}

OUT_DATA AESEncodeData(const unsigned char *master_key, unsigned char* data, unsigned int len,const unsigned char *iv){
    AES_KEY key;
    AES_set_encrypt_key(master_key, KEY_BIT, &key);

    unsigned char ivc[AES_BLOCK_SIZE];

    unsigned int data_length;
    int padding = 0;

    padding = (int) (AES_BLOCK_SIZE - len % AES_BLOCK_SIZE);

    data_length = len+padding;
    unsigned char *data_bak = new unsigned char[data_length];
    memset(data_bak,0,data_length);
    memcpy(data_bak,data,len);
    pkcs7padding(data_bak,data_length,len);
    memcpy(ivc, iv, AES_BLOCK_SIZE * sizeof(char));

    unsigned char *out = new unsigned char[data_length];
    memset(out, 0, data_length);

    /*for(unsigned int i = 0; i < data_length/AES_BLOCK_SIZE; i++ ){
        AES_cbc_encrypt((const unsigned char *) data_bak+i*AES_BLOCK_SIZE, out+i*AES_BLOCK_SIZE, AES_BLOCK_SIZE, &key, ivc, AES_ENCRYPT);
    }*/
    AES_cbc_encrypt((const unsigned char *) data_bak, out, data_length, &key, ivc, AES_ENCRYPT);
    delete[] data_bak;
    OUT_DATA outData={out,data_length};
    return outData;
}
//解密
/*std::string DecodeAES(const unsigned char *master_key, std::string data, const unsigned char *iv) {
    AES_KEY key;
    AES_set_decrypt_key(master_key, KEY_BIT, &key);

    unsigned char ivc[AES_BLOCK_SIZE];
    memcpy(ivc, iv, AES_BLOCK_SIZE * sizeof(char));
    std::string ret;
    for (unsigned int i = 0; i < data.length() / (AES_BLOCK_SIZE * 2); i++) {
        std::string str16 = data.substr(i * AES_BLOCK_SIZE * 2, AES_BLOCK_SIZE * 2);
        unsigned char out[AES_BLOCK_SIZE];
        memset(out, 0, AES_BLOCK_SIZE);
        char *buf = hexstringToBytes(str16);
        AES_cbc_encrypt((const unsigned char *) buf, out, AES_BLOCK_SIZE, &key, ivc, AES_DECRYPT);
        delete (buf);
        ret += hex2char(bytestohexstring((char *) out, AES_BLOCK_SIZE));
    }
    return ret;
}*/

OUT_DATA AESDecodeData(const unsigned char *master_key, unsigned char* data, unsigned int len,const unsigned char *iv) {
    AES_KEY key;
    AES_set_decrypt_key(master_key, KEY_BIT, &key);

    unsigned char ivc[AES_BLOCK_SIZE];
    memcpy(ivc, iv, AES_BLOCK_SIZE * sizeof(char));
    unsigned char *out = new unsigned char[len];
    memset(out,0,len);
    AES_cbc_encrypt((const unsigned char *) data, out, len, &key, ivc, AES_DECRYPT);
    len = pkcs7unpadding(out,len);
    OUT_DATA out_data = {out,len};
    return out_data;
}

const unsigned char * get_master_key(){
    return (const unsigned char *)KEY;
}

const unsigned char * get_iv(){
    return (const unsigned char *)IV;
}
/*

extern "C" JNIEXPORT jstring JNICALL Java_com_blinkt_openvpn_core_NativeUtils_encryptString(JNIEnv *env, jclass type, jstring str_) {
    const char *str = env->GetStringUTFChars(str_, 0);

    const unsigned char *master_key = get_master_key();
    const unsigned char *iv = get_iv();

    OUT_DATA  out = AESEncodeData(master_key, (unsigned char*)str,(unsigned int)strlen(str), iv);
    std::string h = bytestohexstring((char *) out.data, out.len);//EncodeAES(master_key, str, iv);
    env->ReleaseStringUTFChars(str_, str);
    delete[] out.data;
    return env->NewStringUTF(h.c_str());
}

extern "C" JNIEXPORT jstring JNICALL Java_com_blinkt_openvpn_core_NativeUtils_decryptString(JNIEnv *env, jclass type, jstring str_) {
    const char *str = env->GetStringUTFChars(str_, 0);

    const unsigned char *master_key = get_master_key();
    const unsigned char *iv = get_iv();

    //std::string s = DecodeAES(master_key, str, iv);
    char *buf = hexstringToBytes(str);
    OUT_DATA  out = AESDecodeData(master_key, (unsigned char*)buf,(unsigned int)strlen(buf), iv);
    std::string s = hex2char(bytestohexstring((char *) out.data, out.len));
    delete[] out.data;
    env->ReleaseStringUTFChars(str_, str);

    return env->NewStringUTF(s.c_str());

}
*/
/*static void testEncode(unsigned char *data,int len){
    data[0] = (byte) (((data1[0] >> 4) & 0x0F) | ((data1[0] << 4) & 0xF0));
    for (int i = 1; i < len; i++) {
        data[i] = (byte) ((data1[i] & 0xFF) ^ (data1[i - 1] & 0xFF));
    }
}

static int testDecode(unsigned char *data,int len){
    data[0] = (byte) (((data[0] >> 4) & 0x0F) | ((data[0] << 4) & 0xF0));
    for (int i = 1; i < len; i++)
        data[i] = (byte) ((data[i] & 0xFF) ^ (data[i - 1] & 0xFF));
}*/

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_blinkt_openvpn_core_NativeUtils_encryptData(JNIEnv *env, jclass type, jbyteArray data_) {
    jbyte* data = env->GetByteArrayElements(data_,NULL);
    jsize len = env->GetArrayLength(data_);

    const unsigned char *master_key = get_master_key();
    const unsigned char *iv = get_iv();

    OUT_DATA  out = AESEncodeData(master_key, (unsigned char*)data,(unsigned int)len, iv);

    jbyteArray out_data = env->NewByteArray(out.len);
    env->SetByteArrayRegion(out_data,0,out.len,(const jbyte *)out.data);
    delete[] out.data;
    env->ReleaseByteArrayElements(data_,data,0);
    return out_data;


}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_blinkt_openvpn_core_NativeUtils_decryptData(JNIEnv *env, jclass type, jbyteArray data_) {
    jbyte* data = env->GetByteArrayElements(data_,NULL);
    jsize len = env->GetArrayLength(data_);

    const unsigned char *master_key = get_master_key();
    const unsigned char *iv = get_iv();

    OUT_DATA  out = AESDecodeData(master_key, (unsigned char*)data,(unsigned int)len, iv);
    jbyteArray out_data = env->NewByteArray(out.len);
    env->SetByteArrayRegion(out_data,0,out.len,(const jbyte *)out.data);
    delete[] out.data;
    env->ReleaseByteArrayElements(data_,data,0);
    return out_data;
}