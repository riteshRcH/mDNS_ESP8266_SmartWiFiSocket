package in.co.unifytech.socket;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import in.co.unifytech.R;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// https://developer.android.com/guide/components/services.html
public class MobileChargingAutoTurnOffService extends Service
{
    private final String LOG_TAG = "MobCharginAutoOffSrvice";
    private final int NOTIFICATION_ID = 0;

    private NotificationManager notificationManager;
    private NotificationChannel mobChargingAutoTurnOffNotificationChannel;
    private SharedPreferences sharedPreferences;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;

    // http://alexzh.com/tutorials/android-battery-status-use-broadcastReceiver/
    // https://stackoverflow.com/questions/6217692/detecting-the-device-being-plugged-in
    private BroadcastReceiver batteryLevelBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int currentLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            switch (plugged)
            {
                case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                case BatteryManager.BATTERY_PLUGGED_AC:
                    Intent intent1 = registerReceiver(null, new IntentFilter("android.hardware.usb.action.USB_STATE"));
                    // https://stackoverflow.com/questions/4600896/android-detecting-usb
                    // double check USB not connected
                    if (intent1 == null || (intent1.getExtras() != null && !intent1.getExtras().getBoolean("connected")))
                    {
                        int MobileChargingAutoTurnOffPercent = sharedPreferences.getInt("MobileChargingAutoTurnOffPercent", 50);
                        if (currentLevel >= (MobileChargingAutoTurnOffPercent + 50))
                        {
                            String MobileChargingAutoTurnOffSocket = sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null);
                            if (MobileChargingAutoTurnOffSocket != null)
                                connectToPreConfiguredWiFiNetwork(sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null), false);
                        }
                    }else
                        connectToPreConfiguredWiFiNetwork(sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null), false);
                    break;

                case 0:                                     // when on battery power
                case BatteryManager.BATTERY_PLUGGED_USB:    // when charging over USB
                    connectToPreConfiguredWiFiNetwork(sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null), false);
                    break;

                default:
                    // intent didn't include extra info; do nothing
                    break;
            }
        }
    };

    private String currentlyConnectingToPreConfiguredWiFi = null;
    private boolean desiredState = false;
    private final BroadcastReceiver preConfiguredWiFiConnectivityEventsBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()))
            {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
                {
                    String connectedSSID = wifiManager.getConnectionInfo().getSSID();
                    if (connectedSSID != null)
                    {
                        if (connectedSSID.matches("^\".*\"$") && connectedSSID.replaceAll("^\"|\"$", "").equals(currentlyConnectingToPreConfiguredWiFi))     // SSID surrounded in quotes hence it means it is returned as String instead of hex digits
                        {
                            unregisterReceiver(preConfiguredWiFiConnectivityEventsBroadcastReceiver);
                            new BgTaskStateChangeOverWiFi(desiredState).execute();
                        }else
                        {
                            WifiConfiguration preConfiguredWifiConfig = null;
                            for (WifiConfiguration wifiConfiguration : wifiManager.getConfiguredNetworks())
                            {
                                if (wifiConfiguration.SSID.matches("^\".*\"$"))
                                {
                                    if (wifiConfiguration.SSID.replaceAll("^\"|\"$", "").equals(currentlyConnectingToPreConfiguredWiFi))
                                    {
                                        preConfiguredWifiConfig = wifiConfiguration;
                                        break;
                                    }
                                }
                            }

                            if (preConfiguredWifiConfig == null)
                                showInfoGreyToast("WiFi not saved");
                            else
                            {
                                wifiManager.disconnect();
                                wifiManager.enableNetwork(preConfiguredWifiConfig.networkId, true);
                                wifiManager.reconnect();
                            }
                        }
                    }
                }
            }
        }
    };

    private String getCurrentSSID()
    {
        String ssid = null;
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
        {
            String connectedSSID = wifiManager.getConnectionInfo().getSSID();
            if (connectedSSID != null)
            {
                if (connectedSSID.matches("^\".*\"$"))     // SSID surrounded in quotes hence it means it is returned as String instead of hex digits
                    ssid = connectedSSID.replaceAll("^\"|\"$", "");
            }
        }

        return ssid;
    }

    private void connectToPreConfiguredWiFiNetwork(final String preConfiguredWiFi, final boolean desiredStateForBroadcastReceiver)
    {
        if (!wifiManager.isWifiEnabled())
        {
            showInfoGreyToast("Turning on WiFi. Please wait.");
            wifiManager.setWifiEnabled(true);
        }

        new android.os.Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                while (!wifiManager.isWifiEnabled())
                {
                    try
                    {
                        Thread.sleep(220);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

                if (preConfiguredWiFi.equals(getCurrentSSID()))
                    new BgTaskStateChangeOverWiFi(desiredStateForBroadcastReceiver).execute();
                else
                {
                    WifiConfiguration preConfiguredWifiConfig = null;
                    for (WifiConfiguration wifiConfiguration : wifiManager.getConfiguredNetworks())
                    {
                        if (wifiConfiguration.SSID.matches("^\".*\"$"))
                        {
                            if (wifiConfiguration.SSID.replaceAll("^\"|\"$", "").equals(preConfiguredWiFi))
                            {
                                preConfiguredWifiConfig = wifiConfiguration;
                                break;
                            }
                        }
                    }

                    if (preConfiguredWifiConfig == null)
                        showInfoGreyToast("WiFi not saved");
                    else
                    {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                        currentlyConnectingToPreConfiguredWiFi = preConfiguredWiFi;
                        desiredState = desiredStateForBroadcastReceiver;
                        registerReceiver(preConfiguredWiFiConnectivityEventsBroadcastReceiver, intentFilter);

                        wifiManager.disconnect();
                        wifiManager.enableNetwork(preConfiguredWifiConfig.networkId, true);
                        wifiManager.reconnect();
                    }
                }
            }
        }, 1100);
    }

    private BroadcastReceiver chargingAutoTurnOffTurnOnSocketBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            connectToPreConfiguredWiFiNetwork(sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null), true);
        }
    };

    private class BgTaskStateChangeOverWiFi extends AsyncTask<Void, Void, Exception>
    {
        private final String PORTABLE_SOCKET_SERVER_IP = "192.168.9.1";
        private final int SERVICE_PORT = 9911;
        private boolean stateChangeSuccessIndicator = false;
        private boolean desiredState;

        // reference : https://stackoverflow.com/questions/3075009/android-how-can-i-pass-parameters-to-asynctasks-onPreExecute
        BgTaskStateChangeOverWiFi(boolean desiredState)
        {
            this.desiredState = desiredState;
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = new Request.Builder()
                            .url("http://"+PORTABLE_SOCKET_SERVER_IP+':'+SERVICE_PORT+"/gpio/relay/"+(desiredState?'1':'0'))
                            .build();

                if (request != null)
                {
                    Response response = SmartWiFiSocketActivity.okHttpWLANClient.newCall(request).execute();

                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    ResponseBody responseBody = response.body();

                    if (responseBody != null)
                    {
                        String responseBodyString = responseBody.string();
                        if (responseBodyString != null)
                            responseBodyString = responseBodyString.trim();
                        stateChangeSuccessIndicator = "success".equalsIgnoreCase(responseBodyString);
                    }
                }
            }catch(Exception e)
            {
                Log.e(LOG_TAG, e.getMessage() == null ? "null" : e.getMessage());
                e.printStackTrace();
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception exceptionOccurred)
        {
            if(exceptionOccurred==null)
            {
                if (stateChangeSuccessIndicator)
                {
                    SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
                    sharedPreferencesEditor.putBoolean("MobileChargingAutoTurnOffIsCharging", desiredState);
                    while (!sharedPreferencesEditor.commit())
                        Log.d(LOG_TAG, "Retrying commit for Shared Preferences");

                    cancelAndCreateNotification(!sharedPreferences.getBoolean("MobileChargingAutoTurnOffIsCharging", false));

                    if (desiredState)
                    {
                        showSuccessGreenToast("Started Charging");
                        registerReceiver(batteryLevelBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    }else
                    {
                        showSuccessGreenToast("Stopped Charging");
                        unregisterReceiver(batteryLevelBroadcastReceiver);
                    }

                    if (sharedPreferences.getBoolean("MobileChargingAutoTurnOffDisableWiFiOnceCharged", false))
                        wifiManager.setWifiEnabled(false);
                }else
                    showErrorRedToast("Could not turn appliance "+(desiredState ? getString(R.string.stateON):getString(R.string.stateOFF))+". Please try again");
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    // Custom layout Toast in Services took from here
    // https://stackoverflow.com/questions/6992748/android-findViewById-in-service

    private void showSuccessGreenToast(final String message)
    {
        new Handler(Looper.getMainLooper()).post(new Runnable()
        {
            @Override
            public void run()
            {
                View layout = View.inflate(MobileChargingAutoTurnOffService.this, R.layout.toast_sucess_green, null);

                TextView text = layout.findViewById(R.id.txtViewCustomInfoToast);
                text.setText(message);

                Toast toast = new Toast(MobileChargingAutoTurnOffService.this);
                toast.setGravity(Gravity.BOTTOM, 0, 255);
                toast.setDuration(Toast.LENGTH_SHORT);
                toast.setView(layout);
                toast.show();
            }
        });
    }

    private void showInfoGreyToast(final String msg)
    {
        new Handler(Looper.getMainLooper()).post(new Runnable()
        {
            @Override
            public void run()
            {
                View layout = View.inflate(MobileChargingAutoTurnOffService.this, R.layout.toast_info, null);

                TextView text = layout.findViewById(R.id.txtViewCustomInfoToast);
                text.setText(msg);

                Toast toast = new Toast(MobileChargingAutoTurnOffService.this);
                toast.setGravity(Gravity.BOTTOM, 0, 255);
                toast.setDuration(Toast.LENGTH_SHORT);
                toast.setView(layout);
                toast.show();
            }
        });
    }

    void showErrorRedToast(String message)
    {
        View layout = View.inflate(MobileChargingAutoTurnOffService.this, R.layout.toast_error_red, null);

        TextView text = layout.findViewById(R.id.txtViewCustomInfoToast);
        text.setText(message);

        Toast toast = new Toast(MobileChargingAutoTurnOffService.this);
        toast.setGravity(Gravity.BOTTOM, 0, 255);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    /**
     * Return the communication channel to the service.  May return null if
     * clients can not bind to the service.  The returned
     * {@link IBinder} is usually for a complex interface
     * that has been <a href="{@docRoot}guide/components/aidl.html">described using
     * aidl</a>.
     * <p>
     * <p><em>Note that unlike other application components, calls on to the
     * IBinder interface returned here may not happen on the main thread
     * of the process</em>.  More information about the main thread can be found in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html">Processes and
     * Threads</a>.</p>
     *
     * @param intent The Intent that was used to bind to this service,
     *               as given to {@link Context#bindService
     *               Context.bindService}.  Note that any extras that were included with
     *               the Intent at that point will <em>not</em> be seen here.
     * @return Return an IBinder through which clients can call on to the
     * service.
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    /**
     * Called by the system every time a client explicitly starts the service by calling
     * {@link Context#startService}, providing the arguments it supplied and a
     * unique integer token representing the start request.  Do not call this method directly.
     * <p>
     * <p>For backwards compatibility, the default implementation calls
     * {@link #onStart} and returns either {@link #START_STICKY}
     * or {@link #START_STICKY_COMPATIBILITY}.
     * <p>
     * <p>If you need your application to run on platform versions prior to API
     * level 5, you can use the following model to handle the older {@link #onStart}
     * callback in that case.  The <code>handleCommand</code> method is implemented by
     * you as appropriate:
     * <p>
     * {Sample development/samples/ApiDemos/src/com/example/android/apis/app/ForegroundService.java
     * start_compatibility}
     * <p>
     * <p class="caution">Note that the system calls this on your
     * service's main thread.  A service's main thread is the same
     * thread where UI operations take place for Activities running in the
     * same process.  You should always avoid stalling the main
     * thread's event loop.  When doing long-running operations,
     * network calls, or heavy disk I/O, you should kick off a new
     * thread, or use {@link AsyncTask}.</p>
     *
     * @param intent  The Intent supplied to {@link Context#startService},
     *                as given.  This may be null if the service is being restarted after
     *                its process has gone away, and it had previously returned anything
     *                except {@link #START_STICKY_COMPATIBILITY}.
     * @param flags   Additional data about this start request.  Currently either
     *                0, {@link #START_FLAG_REDELIVERY}, or {@link #START_FLAG_RETRY}.
     * @param startId A unique integer representing this specific request to
     *                start.  Use with {@link #stopSelfResult(int)}.
     * @return The return value indicates what semantics the system should
     * use for the service's current started state.  It may be one of the
     * constants associated with the {@link #START_CONTINUATION_MASK} bits.
     * @see #stopSelfResult(int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null) == null)
            stopSelf();
        else
        {
            cancelAndCreateNotification(!sharedPreferences.getBoolean("MobileChargingAutoTurnOffIsCharging", false));

            if (sharedPreferences.getBoolean("MobileChargingAutoTurnOffIsCharging", false))
                registerReceiver(batteryLevelBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            else
                registerReceiver(chargingAutoTurnOffTurnOnSocketBroadcastReceiver, new IntentFilter("in.unify.SmartWiFiSocket.MobileChargingAutoTurnOffService.StartCharging"));
        }

        return Service.START_STICKY;
    }

    private void cancelAndCreateNotification(boolean showNotificationToStartCharging)
    {
        cancelNotification();

        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder = new NotificationCompat.Builder(MobileChargingAutoTurnOffService.this, mobChargingAutoTurnOffNotificationChannel.getId());
        else
            builder = new NotificationCompat.Builder(MobileChargingAutoTurnOffService.this);

        builder.setContentTitle("Charging Auto Turn Off")
                .setContentText((50+sharedPreferences.getInt("MobileChargingAutoTurnOffPercent", 50))+"% using "+(sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null))+(sharedPreferences.getBoolean("MobileChargingAutoTurnOffDisableWiFiOnceCharged", false) ? " -> WiFi OFF" : ""))
                .setSmallIcon(R.mipmap.ic_app)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(PendingIntent.getActivity(MobileChargingAutoTurnOffService.this, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT));

        // https://stackoverflow.com/questions/38761062/calling-a-method-in-service-after-click-on-notification
        if (showNotificationToStartCharging)
            builder.addAction(new NotificationCompat.Action.Builder(R.mipmap.ic_portable_socket_start_charging, "Start Charging", PendingIntent.getBroadcast(MobileChargingAutoTurnOffService.this, 4, new Intent("in.unify.SmartWiFiSocket.MobileChargingAutoTurnOffService.StartCharging"), PendingIntent.FLAG_UPDATE_CURRENT)).build());

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void cancelNotification()
    {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    /**
     * Called by the system when the service is first created.  Do not call this method directly.
     */
    @Override
    public void onCreate()
    {
        super.onCreate();

        sharedPreferences = getSharedPreferences("SmartSocketPreferences", MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            mobChargingAutoTurnOffNotificationChannel = new NotificationChannel("mobChargingAutoTurnOffNotiChannel", "Mob Charging Auto Off", NotificationManager.IMPORTANCE_HIGH);
            mobChargingAutoTurnOffNotificationChannel.setDescription("Mob Charging Auto Off");
            notificationManager.createNotificationChannel(mobChargingAutoTurnOffNotificationChannel);
        }
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.  The
     * service should clean up any resources it holds (threads, registered
     * receivers, etc) at this point.  Upon return, there will be no more calls
     * in to this Service object and it is effectively dead.  Do not call this method directly.
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(batteryLevelBroadcastReceiver);
        unregisterReceiver(chargingAutoTurnOffTurnOnSocketBroadcastReceiver);
    }
}