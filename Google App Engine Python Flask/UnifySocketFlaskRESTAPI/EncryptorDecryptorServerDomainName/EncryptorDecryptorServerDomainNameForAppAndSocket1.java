import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.security.SecureRandom;

public class EncryptorDecryptorServerDomainNameForAppAndSocket1
{
    public static String encrypt(String key, String initVector, String value)
    {
        try
	{
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value.getBytes());

            return Base64.getEncoder().encodeToString(encrypted);
	}catch (Exception ex)
	{
            ex.printStackTrace();
        }

        return null;
    }

    public static String decrypt(String key, String initVector, String encrypted)
    {
        try
	{
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));

            return new String(original);
        }catch (Exception ex)
	{
            ex.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args)
    {
	// String serverDomainNameForApp = "http://10.10.10.4:9911";
	// String serverDomainNameForSocket = serverDomainNameForApp;
	String serverDomainNameForApp = "https://unifysocket.appspot.com";
	String serverDomainNameForSocket = "http://unifysocket.appspot.com";

	String encryptedBase64EncodedString = encrypt("put password here", "IV", serverDomainNameForApp);
	System.out.println("Original String: \t\t\t\t" + serverDomainNameForApp);
	System.out.println("Encrypted Base64 encoded String: \t\t" + encryptedBase64EncodedString);
        System.out.println("Base64 decoded Decrypted String: \t\t" + decrypt("put password here", "IV", encryptedBase64EncodedString));

	System.out.println("\n");

	encryptedBase64EncodedString = encrypt("put password here", "IV", serverDomainNameForSocket);
	System.out.println("Original String: \t\t\t\t" + serverDomainNameForSocket);
	System.out.println("Encrypted Base64 encoded String: \t\t" + encryptedBase64EncodedString);
        System.out.println("Base64 decoded Decrypted String: \t\t" + decrypt("put password here", "IV", encryptedBase64EncodedString));
    }
}
