package com.zz.encrypter.service;

import com.zz.encrypter.utils.cryptography.HashService;
import com.zz.encrypter.utils.cryptography.InputStreamEncrypter;
import com.zz.encrypter.utils.cryptography.OutputStreamEncrypter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;

import static com.zz.encrypter.utils.basicwork.ByteArrayUtils.concat;
import static com.zz.encrypter.utils.basicwork.ByteArrayUtils.isEqual;
import static com.zz.encrypter.utils.basicwork.ByteArrayUtils.long2byte;

public class FileEncrypter {

    private FileEncrypter(){}

    /**
     *
     * @param from 要加密的原始明文
     * @param to 密文要储存到的目标流
     * @param password 用于加密的密码
     * @param stream_length 输入流数据长度
     * @param salt 用于加密的盐，可以忽略以生成随机盐，也可以自定义盐
     */
    public static void encrypt(InputStream from,OutputStream to,byte[] password,long stream_length,byte[] salt) throws Exception {
        CheckSum sum=new CheckSum();
        CipherFileHead head=new CipherFileHead(password,salt,stream_length);
        OutputStreamEncrypter target=new OutputStreamEncrypter(concat(password,salt),to);
        sum.addBytes(long2byte(stream_length));//校验和需要的第一部分数据是原文长度
        try {
            head.writeToStream(to);//写入头部
            while(from.available()>0&&stream_length>0){//加密并写入数据区
                stream_length--;
                byte data= (byte) from.read();
                sum.addByte(data);
                target.write(data);
            }
            target.write(sum.getSum());//最后写入校验和
        } catch (IOException e) {
            throw new Exception("加密过程在输入输出时出现异常。",e);
        }
    }
    public static void encrypt(InputStream from,OutputStream to,byte[] password,long stream_length) throws Exception {
        byte[]salt=HashService.md5.getRandomHash().getByteArray();
        encrypt(from,to,password,stream_length,salt);
    }
    /**
     *
     * @param from 要解密的密文
     * @param to 目标明文
     * @param password 用于解密的密码
     * @throws Exception 输入输入被中断，比如输入数据格式错误或者提前结束，或者输出流无法写入。
     */
    public static void decrypt(InputStream from,OutputStream to,byte[] password) throws Exception {
        CheckSum sum=new CheckSum();
        CipherFileHead head=new CipherFileHead(password);
        head.readFromStream(from);//读取密文的头部
        byte[] salt=head.getSalt();//得到文件头部储存的盐 用于解密
        long length=head.getLength();
        sum.addBytes(long2byte(length));//校验和需要的第一部分数据是原文长度
        InputStreamEncrypter source=new InputStreamEncrypter(concat(password,salt),from);
        try {
            while(source.available()>0 && length>0){//读取加密区
                length--;//计算按照头文件的预计 应该有多少字节被读出来
                byte data= (byte) source.read();
                sum.addByte(data);
                to.write(data);
            }
            byte[] sum2=new byte[16];
            source.read(sum2);//读取校验和
            if(!isEqual(sum.getSum(),sum2))throw new CheckSumException("数据解密完成但校验和不正确。");
        } catch (IOException e) {
            throw new Exception("解密过程在输入输出时出现异常。",e);
        }

    }

    /**
     *
     * @param source 密文源
     * @param password 密码
     * @return 返回检查结果，即这个密文是不是由这个密码加密的。
     * @throws IOException 读取数据中断或者输入格式错误
     */
    public static boolean isEncryptedBy(InputStream source, byte[] password) throws IOException {
        CipherFileHead head=new CipherFileHead(password);
        try {
            head.readFromStream(source);
        } catch (IOException e) {
            throw new IOException("输入流工作异常",e);
        } catch (InputFormatException e) {
            throw new IOException("输入流字节格式错误",e);
        } catch (PasswordException e) {
            return false;
        }
        return true;
    }
}
