package in.co.unifytech.socket.utils;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// https://stackoverflow.com/questions/15554296/simple-java-aes-encrypt-decrypt-example
public class EncryptorDecryptorServerDomainNameForAppAndSocket
{
    public static String decrypt(String key, String initVector, String encrypted_base64_encoded_text)
    {
        try
	    {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes("UTF-8"), "AES"), new IvParameterSpec(initVector.getBytes("UTF-8")));

            return new String(cipher.doFinal(Base64.decode(encrypted_base64_encoded_text, Base64.DEFAULT)));
        }catch (Exception ex)
	    {
            ex.printStackTrace();
        }

        return null;
    }
}
