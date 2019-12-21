package in.co.unifytech.socket.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class AssetsUtil
{
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static boolean checkIfFileExistsInAssets(Context context, String pathInAssets)
    {
        AssetManager assetManager = context.getResources().getAssets();
        InputStream inputStream;

        try
        {
            inputStream = assetManager.open(pathInAssets);
            inputStream.close();
            return true;
        }catch (IOException e)
        {
            return false;
        }
    }

    public static String readAssetFileAsHexString(Context context, String pathInAssets, boolean convertToHexString)
    {
        String fileContentsHexString = null;
        AssetManager assetManager = context.getAssets();

        try
        {
            InputStream inputStream = assetManager.open(pathInAssets);
            byte[] fileContents = new byte[8192];
            int result = inputStream.read(fileContents);
            inputStream.close();

            if (result != -1)
            {
                if (convertToHexString)
                    fileContentsHexString = bytesToHex(Arrays.copyOfRange(fileContents, 0, result));
                else
                    fileContentsHexString = new String(Arrays.copyOfRange(fileContents, 0, result), "ASCII");
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }

        return fileContentsHexString;
    }

    private static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++ )
        {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
