package in.co.unifytech.socket;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import in.co.unifytech.R;
import in.co.unifytech.socket.pojos.PerFixedSocketConfig;
import in.co.unifytech.socket.pojos.PojoScheduleTimerInfo;
import in.co.unifytech.socket.utils.AssetsUtil;
import in.co.unifytech.socket.utils.AsyncTaskCustomProgressDialog;
import in.co.unifytech.socket.utils.CustomExpandableListAdapter;
import in.co.unifytech.socket.utils.EncryptorDecryptorServerDomainNameForAppAndSocket;
import in.co.unifytech.socket.utils.SelectFixedSocketCustomAdapter;
import in.co.unifytech.socket.utils.TimezoneData;
import mobi.upod.timedurationpicker.TimeDurationPicker;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SmartWiFiSocketActivity extends AppCompatActivity //implements IFragmentListener
{
    private final String LOG_TAG = SmartWiFiSocketActivity.class.getSimpleName();

    /*                     was a Javadoc comment earlier
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    // private TabsPagerAdapter mTabsPagerAdapter;

    // View references
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private Toolbar toolbar;
    private FloatingActionButton fab;
    private TabLayout tabLayout;
    private CustomProgressDialog customProgressDialogSearchingForSelectedFixedSocket, customProgressDialogSearchingInitialSetupSSID, customProgressDialogConnectingInitialSetupSSID, customProgressDialogConnectingPreConfiguredWiFi, customProgressDialogSearchingForAllFixedSockets;
    private Snackbar snackbar;
    private StateChangeFragment fragmentStateChange;
    private ScheduleTimersFragment fragmentScheduleTimers;
    private SocketConfigurationFragment fragmentSocketConfiguration;
    private MiscFragment fragmentMisc;

    enum States {ON_WITH_NO_TIMER, ON_WITH_TIMER, ON_DOUBLE_TIMER_TIMER1, ON_DOUBLE_TIMER_TIMER2, OFF_WITH_NO_TIMER, OFF_WITH_TIMER, OFF_DOUBLE_TIMER_TIMER1, OFF_DOUBLE_TIMER_TIMER2, DISCONNECTED }
    private enum Tabs { STATE_CHANGE_TAB, SCHEDULE_TIMERS_TAB, CONFIG_TAB, MISC_TAB }

    // private volatile boolean doubleBackToExitPressedOnce = false;
    // private Handler handler = new Handler();

    // System service references
    SharedPreferences sharedPreferences;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;

    // Models/State maintainers
    private List<PerFixedSocketConfig> configuredFixedSockets = new ArrayList<>();
    static PerFixedSocketConfig currentlySelectedFixedSocket = null;
    static String currentlySelectedPortableSocket = null;
    private Set<String> searchAvailableFixedSocketsResults = new HashSet<>(64);
    private Set<String> configuredPortableSockets = new TreeSet<>();
    private boolean nsdForSearchingAllFixedSockets = false;              // if true, NSD listeners behavior is to find all sockets, 3.2 seconds after last resolve or user cancel cancels the search and if false NSD listeners behavior is to search for selected socket
    private boolean alsoTriggerInitialSetupAfterSearchingAllFixedSockets = false;

    // Models/State maintainers => fields for currentlySelectedFixedSocket/currentlySelectedPortableSocket
    static volatile States currentState = States.DISCONNECTED;
    static volatile long lastStateUpdatedTimestampMillisecs = 0;
    static volatile int socketSoftwareVersion = 0;
    private static Handler handlerGetLatestStateViaWLANEverySecond = null;

    // Models/State maintainers => fields for currentlySelectedFixedSocket
    static List<PojoScheduleTimerInfo> allScheduleTimerInfoPojos = new ArrayList<>();
    static volatile boolean isInternetModeActivated = false;
    // private boolean doesListHaveOSCT = false;
    static volatile boolean madeAnyChangeToRunSkipForTodaysScheduleTimer = false;
    static volatile boolean madeAnyChangeToEnabledOSFTScheduleTimers = false, madeAnyChangeToEnabledFSScheduleTimers = false, madeAnyChangeToEnabledRTScheduleTimers = false, madeAnyChangeToEnabledRSScheduleTimers = false;
    static boolean isAnyTimerRunning = false;
    private volatile boolean doneRetrievingLatestScheduleTimerList = false;
    static String fixedSocketResolvedIPAddress = "";
    static int fixedSocketResolvedPort = 0;             // even though the port is hard coded as 9911, we have to resolve it from MDNS so that we know the service is Smart WiFi Socket and not some other IOT device
    static volatile String lastUpdatedByEmailOrSocketForInternetMode = null;
    static volatile String lastUpdateActionByAppInternetMode = null;

    // Models/State maintainers => fields for currentlySelectedPortableSocket
    static boolean doubleTimerRunning = false;
    static long doubleTimerTimer1DurationSecs = 0, doubleTimerTimer2DurationSecs = 0, currentTimer1Timer2Count = 0, totalTimer1Timer2Count = 0;
    static boolean doubleTimerTimer1State = false;

    // constants for Initial Setup/Add new socket, password put as local variable into events broadcast receiver
    static final String DEFAULT_SETUP_SSID = "Setup Socket";

    // Models/State maintainers => fields for Initial Setup/Add new socket
    private boolean setupNewSocketAsFixedSocket = true;
    private List<ScanResult> scanResults;
    private Set<String> socketScannedNearbyWiFiNetworks = new LinkedHashSet<>();
    private String localtimeHexStringToTransfer = null;

    // constants for Normal Working mode
    private final String FIXED_SOCKET_SERVICE_TYPE = "_http._tcp.";
    private static final String SETUP_SERVER_IP = "192.168.10.1";
    static final String PORTABLE_SOCKET_SERVER_IP = "192.168.9.1";
    static final int SERVICE_PORT = 9911;
    // private static LinkedHashMap<String, String> regionToTZOffsetMappings = new LinkedHashMap<>();
    // For type 2 for socket collator India 19800, Japan 32400, South Korea 32400, Singapore 28800, Sri Lanka 19800, Thailand 25200, United Arab Emirates 14400

    // Models/State maintainers =>  fields for Normal Working mode
    // private long ESP_MAX_TIMER_INTERVAL = 6870947;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;
    static final OkHttpClient okHttpWLANClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build();
    static final OkHttpClient okHttpInternetClient = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build();
    private static final Gson gson = new Gson();

    private boolean connectToInitialSetupSSIDForSocketVersionUpgrade = false;
    private final BroadcastReceiver initialSetupNetworkConnectivityEventsBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent != null && intent.getAction() != null)
            {
                switch (intent.getAction())
                {
                    case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                        final String DEFAULT_SETUP_PSK = "Sm@rTWiFiUniFyS0cKeT:)";
                        boolean foundSetupSSID = false;
                        scanResults = wifiManager.getScanResults();
                        for (int i = 0; i < scanResults.size(); i++)
                        {
                            if (DEFAULT_SETUP_SSID.equals(scanResults.get(i).SSID))
                            {
                                foundSetupSSID = true;
                                break;
                            }
                        }

                        if (foundSetupSSID)
                        {
                            dismissProgressDialogSearchingInitialSetupSSID();
                            showProgressDialogConnectingInitialSetupSSID();

                            WifiConfiguration wifiConfig = new WifiConfiguration();
                            wifiConfig.SSID = String.format("\"%s\"", DEFAULT_SETUP_SSID);
                            wifiConfig.preSharedKey = String.format("\"%s\"", DEFAULT_SETUP_PSK);

                            wifiManager.disconnect();
                            wifiManager.enableNetwork(wifiManager.addNetwork(wifiConfig), true);
                            wifiManager.reconnect();
                        } else
                        {
                            dismissProgressDialogSearchingInitialSetupSSID();
                            showInfoGreyToast("Unable to find WiFi: \"" + DEFAULT_SETUP_SSID + "\"");
                            unregisterReceiver(initialSetupNetworkConnectivityEventsBroadcastReceiver);
                        }
                        break;

                    case ConnectivityManager.CONNECTIVITY_ACTION:
                        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                        if (networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
                        {
                            String connectedSSID = wifiManager.getConnectionInfo().getSSID();
                            if (connectedSSID != null)
                            {
                                if (connectedSSID.matches("^\".*\"$") && connectedSSID.replaceAll("^\"|\"$", "").equals(DEFAULT_SETUP_SSID))     // SSID surrounded in quotes hence it means it is returned as String instead of hex digits
                                {
                                    unregisterReceiver(initialSetupNetworkConnectivityEventsBroadcastReceiver);
                                    dismissProgressDialogConnectingInitialSetupSSID();

                                    if (connectToInitialSetupSSIDForSocketVersionUpgrade)
                                        new BgTaskGetSocketSoftwareVersion(false, false).execute();
                                    else
                                        showAndValidateInitialUserSetup();
                                } else
                                {
                                    // WiFi got connected but to some other WiFi network hence we restart the scanning process
                                    wifiManager.disconnect();
                                    wifiManager.startScan();
                                }
                            }
                        }
                        break;
                }
            }
        }
    };

    // https://stackoverflow.com/questions/5888502/how-to-detect-when-wifi-connection-has-been-established-in-android
    private String currentlyConnectingToPreConfiguredWiFi = null;
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
                            dismissProgressDialogConnectingPreConfiguredWiFi();

                            if (currentlySelectedFixedSocket != null)
                            {
                                nsdForSearchingAllFixedSockets = false;
                                nsdManager.discoverServices(FIXED_SOCKET_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
                                countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.start();

                                customProgressDialogSearchingForSelectedFixedSocket = new CustomProgressDialog("Searching for " + currentlySelectedFixedSocket.getMDNSHostName() + " in WiFi: " + currentlyConnectingToPreConfiguredWiFi, "Please wait");
                                customProgressDialogSearchingForSelectedFixedSocket.setOnCancelListener(new DialogInterface.OnCancelListener()
                                {
                                    @Override
                                    public void onCancel(DialogInterface dialogInterface)
                                    {
                                        dialogInterface.dismiss();
                                        countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.cancel();

                                        nsdManager.stopServiceDiscovery(discoveryListener);
                                        resetStateMaintainerModel();
                                    }
                                });
                                customProgressDialogSearchingForSelectedFixedSocket.show();
                            }

                            if (currentlySelectedPortableSocket != null)
                                new BgTaskGetLatestStateViaWLAN(true, false, false, false, null, false, false).execute();
                        }else
                        {
                            if (currentlySelectedPortableSocket != null || currentlySelectedFixedSocket != null)            // was trying to connect to Portable Socket/External WiFi SSID of Fixed Socket but instead connected to some other WiFi network
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
                                {
                                    resetStateMaintainerModel();
                                    showInfoGreyToast("Please connect to WiFi: " + currentlyConnectingToPreConfiguredWiFi + " manually and select Socket from menu.");
                                } else
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
        }
    };

    private final BroadcastReceiver wiFiInternetDisConnectivityEventsBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()))
            {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo == null || !networkInfo.isConnected())
                {
                    if (currentlySelectedFixedSocket != null || currentlySelectedPortableSocket != null)
                    {
                        resetStateMaintainerModel();
                        showInfoGreyToast("Internet/WiFi Disconnected");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver internetConnectivityEventsBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()))
                new BgTaskTryGoogleSilentSignInIfConnectedToInternet().execute();
        }
    };

    // interval for onTick is 10 secs while countdown timer runs for 9 secs which means we don't need to call onTick
    private CountDownTimer countDownTimerStopSearchingForSelectedFixedSocketOnExpiration = new CountDownTimer(9000, 10000)
    {
        @Override
        public void onTick(long millisUntilFinished)
        {
            // nothing to do
        }

        @Override
        public void onFinish()
        {
            dismissDialogSearchingForSelectedFixedSocket();
            showInfoGreyToast("Could not find Socket. Please make sure Socket is powered on.");

            nsdManager.stopServiceDiscovery(discoveryListener);
            resetStateMaintainerModel();
        }
    };

    // interval for onTick is 6 secs while countdown timer runs for 5 secs which means we don't need to call onTick
    private CountDownTimer countDownTimerStopNSDSearchForAllFixedSocketsOnExpiration = new CountDownTimer(5000, 6000)
    {
        @Override
        public void onTick(long millisUntilFinished)
        {
            // nothing to do
        }

        @Override
        public void onFinish()
        {
            int newlyAddedFixedSocketsCount = 0;

            if (nsdForSearchingAllFixedSockets)
            {
                nsdManager.stopServiceDiscovery(discoveryListener);

                dismissDialogSearchingForAllFixedSockets();

                // APPROACH 1 add only missing/newly found Sockets (those Sockets which user has not forgotten yet still remain but anyways on selecting those NSD wont be able to find it) hence for efficiency purpose this approach is used

                retrieveConfiguredSockets();

                for (String socketName : searchAvailableFixedSocketsResults)
                    if (!checkIfSocketNameAlreadyInFixedSockets(socketName))
                    {
                        configuredFixedSockets.add(new PerFixedSocketConfig(socketName, getCurrentSSID(), ""));   // default timezoneDetails = empty string, it will anyways get updated in  BgTaskGetLatestState
                        newlyAddedFixedSocketsCount++;
                    }

                saveConfiguredSockets();

                /* APPROACH 2 clear all saved list and make new based on latest Sockets retrieved

                if (configuredFixedSockets == null)
                    configuredFixedSockets = new ArrayList<>();

                configuredFixedSockets.clear();

                for (String socketName : searchAvailableFixedSocketsResults)
                    configuredFixedSockets.add(new PerFixedSocketConfig(socketName, false, false)); */

                if (alsoTriggerInitialSetupAfterSearchingAllFixedSockets)
                {
                    connectToInitialSetupSSIDForSocketVersionUpgrade = false;
                    triggerInitialSetupForNewSocket();
                }else
                    showSuccessGreenToast("Added "+newlyAddedFixedSocketsCount+" Fixed Sockets from current WiFi network");
            }
        }
    };

    static PojoScheduleTimerInfo runningTimerPojoObject;
    static long runningTimerSecsLeft;
    static String runningTimerCronMaskConfigString = null, runningTimerType = null;
    private CountDownTimer countDownTimerForDisplay = null;

    private GoogleApiClient googleApiClient = null;
    static GoogleSignInAccount googleSignInAccount;
    static boolean calledGoogleSilentSignInOnceForThisSession = false;
    private boolean calledRemovePastScheduleTimerEntriesOnceForThisSession = false;

    GoogleApiClient getGoogleApiClient()
    {
        if (googleApiClient == null)
        {
            return (googleApiClient = new GoogleApiClient.Builder(SmartWiFiSocketActivity.this)
                    .enableAutoManage(SmartWiFiSocketActivity.this, new GoogleApiClient.OnConnectionFailedListener()
                    {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
                        {
                            showErrorRedToast("Unable to communicate with Google");
                        }
                    })
                    .addApi(Auth.GOOGLE_SIGN_IN_API, new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestIdToken(getString(R.string.servers_client_id))
                            .build())
                    .build());
        }else
            return googleApiClient;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unify_smart_wifi_socket);

        Log.d(LOG_TAG, "in onCreate");

        getViews();
        initViews();
        retrieveConfiguredSockets();
        getSystemServices();

        refreshViewsAccordingToSelectedTab();

        new BgTaskTryGoogleSilentSignInIfConnectedToInternet().execute();

        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {

                if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
                    showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (currentlySelectedFixedSocket != null)
                    {
                        if (isInternetModeActivated)
                            showInfoGreyToast(getString(R.string.schedule_timer_unavailable_internet_mode));
                        else if (isAnyTimerRunning)
                            showInfoGreyToast(getString(R.string.another_timer_already_running));
                        else
                        {
                            if (doneRetrievingLatestScheduleTimerList)
                            {
                                new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                        .setTitle("Select schedule/timer type")
                                        .setCancelable(true)
                                        .setSingleChoiceItems(R.array.all_schedule_timer_types, -1, new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int selectedItemIndex)
                                            {
                                                dialogInterface.dismiss();
                                                switch (selectedItemIndex)
                                                {
                                                    case 0:
                                                        new BgTaskGetLatestStateViaWLAN(true, false, false, true, PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER, false, false).execute();
                                                        break;

                                                    case 1:
                                                        new BgTaskGetLatestStateViaWLAN(true, false, false, true, PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER, false, false).execute();
                                                        break;

                                                    case 2:
                                                        new BgTaskGetLatestStateViaWLAN(true, false, false, true, PojoScheduleTimerInfo.ScheduleTimerType.FUTURE_SCHEDULE, false, false).execute();
                                                        break;

                                                    case 3:
                                                        new BgTaskGetLatestStateViaWLAN(true, false, false, true, PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_SCHEDULE, false, false).execute();
                                                        break;

                                                    case 4:
                                                        new BgTaskGetLatestStateViaWLAN(true, false, false, true, PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER, false, false).execute();
                                                        break;
                                                }
                                            }
                                        })
                                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i)
                                            {
                                                dialogInterface.dismiss();
                                            }
                                        }).show();
                            } else
                                new BgTaskGetLatestScheduleTimerInfo(false).execute();
                        }
                    }

                    if (currentlySelectedPortableSocket != null)
                        showInfoGreyToast(getString(R.string.schedule_timer_unavailable_portable_socket));
                }
            }
        });

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener()
        {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
            {

            }

            @Override
            public void onPageSelected(int position)
            {
                refreshViewsAccordingToSelectedTab();
                // refreshViewsForAllTabs();
            }

            @Override
            public void onPageScrollStateChanged(int state)
            {

            }
        });

        resetStateMaintainerModel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /*@Override
    protected void onPause()
    {
        super.onPause();
    }*/

    @Override
    protected void onStart()
    {
        super.onStart();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(internetConnectivityEventsBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        stop_handlerGetLatestStateEveryNSeconds();
        stop_countDownTimerForDisplay(null);

        resetStateMaintainerModel();

        try
        {
            unregisterReceiver(internetConnectivityEventsBroadcastReceiver);
        }catch (IllegalArgumentException iae)
        {
            System.err.println("Ignored unregisterReceiver error receiver not registered for internetConnectivityEventsBroadcastReceiver");
        }

        String MobileChargingAutoTurnOffSocket = sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null);
        if (MobileChargingAutoTurnOffSocket == null)
            notificationManager.cancel(0);              // as per private final int NOTIFICATION_ID = 0; in MobileChargingAutoTurnOffService
        else
        {
            if (!isMobChargingAutoTurnOffServiceRunning(MobileChargingAutoTurnOffService.class))
                startService(new Intent(SmartWiFiSocketActivity.this, MobileChargingAutoTurnOffService.class));
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        /*if (wifiManager.isWifiEnabled())
            removePreConfiguredSavedWiFiConfiguration();
        else
        {
            wifiManager.setWifiEnabled(true);
            removePreConfiguredSavedWiFiConfiguration();
            wifiManager.setWifiEnabled(false);
        }*/

        /*if (!isMobChargingAutoTurnOffServiceRunning(MobileChargingAutoTurnOffService.class))
            startService(new Intent(SmartWiFiSocketActivity.this, MobileChargingAutoTurnOffService.class));*/
    }

    private boolean isMobChargingAutoTurnOffServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null)
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
                if (serviceClass.getName().equals(service.service.getClassName()))
                    return true;
        return false;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            requestAppPermissions();
        } else
        {
            if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
                showListOfConfiguredSockets(false, false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId())
        {
            case R.id.menu_item_select_socket:
                showListOfConfiguredSockets(false, true);
                return true;

            case R.id.menu_item_refresh_all_tabs_after_data_retrieval:
                new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                        .setTitle("Select Action")
                        .setCancelable(true)
                        .setSingleChoiceItems(new CharSequence[]{"Reconnect to same Socket", "Refresh all Tabs"}, -1, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();

                                if (which == 0)
                                    handleNetworkConnectivityChange();
                                else if (which == 1)
                                    refreshViewsForAllTabs(true);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();
                            }
                        }).create().show();
                return true;

            case R.id.menu_item_add_new_socket:
                // Manual message: If you experience problems in connecting the Socket to your external WiFi then ensure the following:\n\n1. Your external WiFi name and password consist only of uppercase/lowercase characters and digits.\n\n2. You connect your mobile to your external WiFi using DHCP (If unsure you can check in WiFi settings)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    if (isLocationEnabled(SmartWiFiSocketActivity.this))
                    {
                        add_new_socket_menu_item_handler();
                    }else
                    {
                        new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                .setTitle("Enable Location Services")
                                .setMessage("Due to Android Limitation, Location needs to ON for scanning nearby WiFi networks.\n\nPlease turn on Location Services.")
                                .setCancelable(false)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        dialog.dismiss();
                                    }
                                })
                                .create().show();
                    }
                }else
                {
                    add_new_socket_menu_item_handler();
                }
                return true;

            case R.id.menu_item_search_available_WLAN_sockets:
                new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                        .setTitle("Search available WiFi Sockets")
                        .setMessage("This feature searches for other Fixed Sockets in your WiFi. It is helpful when:\n\n1. You have forgotten a Fixed Socket and want it back\n\n2. Someone else has configured (completed Initial Setup) a Fixed Socket in your WiFi and you want it in your list")
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();
                            }
                        })
                        .setPositiveButton("Start Searching", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();
                                alsoTriggerInitialSetupAfterSearchingAllFixedSockets = false;
                                startNSDAndShowDialogSearchingForAllFixedSockets();
                            }
                        })
                        .create().show();
                return true;

            case R.id.menu_item_forget_a_configured_socket:
                showListOfConfiguredSockets(true, true);
                return true;

            case R.id.menu_item_disconnect_from_current_socket:
                if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
                    showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                    resetStateMaintainerModel();
                return true;

            case R.id.menu_item_storage_details:
                if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
                    showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (currentlySelectedFixedSocket != null)
                    {
                        if (isInternetModeActivated)
                            showInfoGreyToast("Storage Details not available in Internet Mode");
                        else
                            new BgTaskShowStorageDetails().execute();
                    }

                    if (currentlySelectedPortableSocket != null)
                        new BgTaskShowStorageDetails().execute();
                }
                return true;

            case R.id.menu_item_factory_reset_redo_initial_setup:
                if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
                    showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    String socketNameToFactoryReset = null;

                    if (currentlySelectedFixedSocket != null)
                    {
                        if (isInternetModeActivated)
                            showInfoGreyToast("Factory Reset not available in Internet Mode");
                        else
                            socketNameToFactoryReset = "Fixed Socket: " + currentlySelectedFixedSocket.getMDNSHostName();
                    }

                    if (currentlySelectedPortableSocket != null)
                        socketNameToFactoryReset = "Portable Socket: "+currentlySelectedPortableSocket;

                    if (socketNameToFactoryReset != null)
                        new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                .setTitle("Confirm Factory Reset?")
                                .setMessage("Are you REALLY SURE you want to Factory Reset/Redo Initial Setup for "+socketNameToFactoryReset+"?")
                                .setCancelable(true)
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i)
                                    {
                                        dialogInterface.dismiss();
                                        new BgTaskFactoryResetSelectedSocket().execute();
                                    }
                                })
                                .setNegativeButton("No", new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i)
                                    {
                                        dialogInterface.dismiss();
                                    }
                                }).create().show();
                }
                return true;

            case R.id.menu_item_wifi:
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                return true;

            /*case R.id.menu_item_download_apply_socket_software:
                resetStateMaintainerModel();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    if (isLocationEnabled(SmartWiFiSocketActivity.this))
                    {
                        connectToInitialSetupSSIDForSocketVersionUpgrade = true;
                        triggerInitialSetupForNewSocket();
                    }else
                    {
                        new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                .setTitle("Enable Location Services")
                                .setMessage("Due to Android Limitation, Location needs to ON for scanning nearby WiFi networks.\n\nPlease turn on Location Services.")
                                .setCancelable(false)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        dialog.dismiss();
                                    }
                                })
                                .create().show();
                    }
                }else
                {
                    connectToInitialSetupSSIDForSocketVersionUpgrade = true;
                    triggerInitialSetupForNewSocket();
                }
                return true;*/
        }

        return super.onOptionsItemSelected(item);
    }

    private static boolean isLocationEnabled(Context context)
    {
        int locationMode;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            try
            {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e)
            {
                e.printStackTrace();
                return false;
            }

            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        } else
        {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    private void add_new_socket_menu_item_handler()
    {
        new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Setup new Socket as")
                .setCancelable(true)
                .setSingleChoiceItems(R.array.setup_new_socket_types, -1, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int selectedItemIndex)
                    {
                        dialog.dismiss();
                        resetStateMaintainerModel();
                        switch (selectedItemIndex)
                        {
                            case 0:
                                alsoTriggerInitialSetupAfterSearchingAllFixedSockets = setupNewSocketAsFixedSocket = true;
                                startNSDAndShowDialogSearchingForAllFixedSockets();
                                break;

                            case 1:
                                setupNewSocketAsFixedSocket = connectToInitialSetupSSIDForSocketVersionUpgrade = false;
                                triggerInitialSetupForNewSocket();
                                break;
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }
                }).create().show();
    }

    private void requestAppPermissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0x1234567);
            }
        }
    }


    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if(requestCode == 0x1234567)
        {
            boolean grantedAllPermissions = true;
            for (int grantResult : grantResults)
                if (grantResult == PackageManager.PERMISSION_DENIED)
                {
                    grantedAllPermissions = false;
                    break;
                }

            if (grantedAllPermissions)
            {
                if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
                    showListOfConfiguredSockets(false, false);
            } else
                showInfoGreyToast("Please grant all permissions to the app");
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class TabsPagerAdapter extends FragmentPagerAdapter
    {
        TabsPagerAdapter(FragmentManager fm)
        {
            super(fm);
        }

        @Override
        public Fragment getItem(int position)
        {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            switch (position)
            {
                case 0:
                    fragmentStateChange = new StateChangeFragment();
                    return fragmentStateChange;

                case 1:
                    fragmentScheduleTimers = new ScheduleTimersFragment();
                    return fragmentScheduleTimers;

                case 2:
                    fragmentSocketConfiguration = new SocketConfigurationFragment();
                    return fragmentSocketConfiguration;

                case 3:
                    fragmentMisc = new MiscFragment();
                    return fragmentMisc;
            }
            //return PlaceholderFragment.newInstance(position + 1);
            return null;
        }

        @Override
        public int getCount()
        {
            // Show 4 total pages.
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position)
        {
            switch (position)
            {
                case 0:
                    return "Status";
                case 1:
                    return "Schedule/Timers";
                case 2:
                    return "Config";
                case 3:
                    return "Misc";
            }
            return null;
        }
    }

    @Override
    public void onBackPressed()
    {
        if(snackbar.isShown())
        {
            super.onBackPressed();
            finish();
        }else
            snackbar.show();
        /*if (doubleBackToExitPressedOnce)
        {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Snackbar.make(findViewById(R.id.fab), "Press back again to exit", Snackbar.LENGTH_SHORT).show();

        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);*/
    }

    private void getViews()
    {
        snackbar = Snackbar.make(findViewById(R.id.main_content), "Press back again to exit", Snackbar.LENGTH_SHORT);

        fab = findViewById(R.id.fab);

        toolbar = findViewById(R.id.mainActivityToolbar);
        setSupportActionBar(toolbar);

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        // Create the adapter that will return a fragment for each of the
        // primary sections of the activity.
        mViewPager.setAdapter(new TabsPagerAdapter(getSupportFragmentManager()));

        tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
    }

    private void initViews()
    {

    }

    void saveConfiguredSockets()
    {
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();

        sharedPreferencesEditor.putStringSet("configuredPortableSockets", configuredPortableSockets);
        // while (!sharedPreferencesEditor.commit())
        //      Log.d(LOG_TAG, "Retrying commit for Shared Preferences");

        // reference: https://stackoverflow.com/questions/22984696/storing-array-list-object-in-sharedPreferences

        sharedPreferencesEditor.putString("configuredFixedSockets", gson.toJson(configuredFixedSockets));
        while (!sharedPreferencesEditor.commit())
            Log.d(LOG_TAG, "Retrying commit for Shared Preferences");
    }

    private void retrieveConfiguredSockets()
    {
        if(sharedPreferences == null)
        {
            // give 5 seconds for android system to create shared preferences, if after 5 seconds it couldn't create it delete the files in app directory's shared_prefs folder
            new android.os.Handler().postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    if (sharedPreferences == null)
                    {
                        File sharedPreferencesDir = new File(getApplicationContext().getFilesDir().getPath() + getPackageName() + "/shared_prefs/");
                        File[] listFiles = sharedPreferencesDir.listFiles();
                        if (listFiles != null)
                        {
                            for (File file : listFiles)
                            {
                                while (!file.delete())
                                {
                                    try
                                    {
                                        Thread.sleep(320);
                                    } catch (InterruptedException e)
                                    {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }, 5000);
            sharedPreferences = SmartWiFiSocketActivity.this.getSharedPreferences("SmartSocketPreferences", MODE_PRIVATE);
        }

        if (configuredFixedSockets == null)
            configuredFixedSockets = new ArrayList<>();

        configuredFixedSockets.clear();

        if (configuredPortableSockets == null)
            configuredPortableSockets = new TreeSet<>();

        configuredPortableSockets.clear();

        // resetStateMaintainerModel();

        String configuredFixedSocketsJSON = sharedPreferences.getString("configuredFixedSockets", null);
        if (configuredFixedSocketsJSON != null)
            configuredFixedSockets = gson.fromJson(configuredFixedSocketsJSON, new TypeToken<ArrayList<PerFixedSocketConfig>>() {}.getType());

        // reference: https://stackoverflow.com/questions/22984696/storing-array-list-object-in-sharedPreferences

        Set<String> configuredPortableSocketsSet = sharedPreferences.getStringSet("configuredPortableSockets", null);
        if (configuredPortableSocketsSet != null)
        {
            configuredPortableSockets = new TreeSet<>();
            configuredPortableSockets.addAll(configuredPortableSocketsSet);
            /*for (String configuredPortableSocket : configuredPortableSocketsSet)
                configuredPortableSockets.add(configuredPortableSocket);*/
        }
    }

    private void getSystemServices()
    {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        nsdManager = (NsdManager) getApplicationContext().getSystemService(Context.NSD_SERVICE);
        initResolveListener();
        initDiscoveryListener();
    }

    private void refreshViewsAccordingToSelectedTab()
    {
        switch (Tabs.values()[tabLayout.getSelectedTabPosition()])
        {
            case STATE_CHANGE_TAB:
                fab.hide();
                if (fragmentStateChange != null)
                    fragmentStateChange.refreshViews(currentState, null, 0);
                break;

            case SCHEDULE_TIMERS_TAB:
                fab.show();
                if (fragmentScheduleTimers != null)
                {
                    fragmentScheduleTimers.refreshViews();
                    if (!calledRemovePastScheduleTimerEntriesOnceForThisSession)
                    {
                        fragmentScheduleTimers.removePastScheduleTimerEntries(true);
                        calledRemovePastScheduleTimerEntriesOnceForThisSession = true;
                    }
                }
                break;

            case CONFIG_TAB:
                fab.hide();
                if (fragmentSocketConfiguration != null)
                    fragmentSocketConfiguration.refreshViews();
                break;

            case MISC_TAB:
                fab.hide();
                if (fragmentMisc != null)
                    fragmentMisc.refreshViews();
        }
    }

    void removeChargingAutoTurnOffFromSharedPreferences()
    {
        SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
        sharedPreferencesEditor.remove("MobileChargingAutoTurnOffSocket");
        sharedPreferencesEditor.remove("MobileChargingAutoTurnOffPercent");
        sharedPreferencesEditor.remove("MobileChargingAutoTurnOffDisableWiFiOnceCharged");
        while (!sharedPreferencesEditor.commit())
            Log.d(LOG_TAG, "Retrying commit for Shared Preferences");
    }

    /**
     *
     * @param showToForgetSocket if true this dialog is shown to forget selected socket else selected socket becomes currentlySelectedFixedSocket
     */
    private void showListOfConfiguredSockets(final boolean showToForgetSocket, boolean calledFromMenuItem)
    {
        retrieveConfiguredSockets();

        if ( (showToForgetSocket || !calledFromMenuItem) && configuredFixedSockets.isEmpty() && configuredPortableSockets.isEmpty())
            showInfoGreyToast("No Sockets added yet");
        else
        {
            Collections.sort(configuredFixedSockets);

            // Below approach is better as it uses Android's Raw Views. another way to do it is using Fragments (library example: https://github.com/ashishbhandari/AndroidTabbedDialog)
            View selectSocketDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_select_socket, null);
            final TabHost tabHostSelectSocket = selectSocketDialogLayout.findViewById(R.id.tabHostSelectSocket);
            tabHostSelectSocket.setup();

            final AlertDialog dialogSelectSocket = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                    .setTitle("Select Socket" + (showToForgetSocket ? " to forget" : ""))
                    .setView(selectSocketDialogLayout)
                    .setCancelable(true)
                    .setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            dialog.dismiss();
                        }
                    }).create();

            final List<String> portableSocketNames = new ArrayList<>(configuredPortableSockets);
            Collections.sort(portableSocketNames);

            ListView listViewFixedSockets = selectSocketDialogLayout.findViewById(R.id.listViewFixedSockets);
            if (configuredFixedSockets.isEmpty())
            {
                listViewFixedSockets.setAdapter(new ArrayAdapter<>(SmartWiFiSocketActivity.this, android.R.layout.simple_list_item_1, Collections.singletonList("<Empty List>")));
                listViewFixedSockets.setEnabled(false);
            } else
            {
                listViewFixedSockets.setAdapter(new SelectFixedSocketCustomAdapter(SmartWiFiSocketActivity.this, configuredFixedSockets));
                listViewFixedSockets.setOnItemClickListener(new AdapterView.OnItemClickListener()
                {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
                    {
                        if (showToForgetSocket)
                        {
                            PerFixedSocketConfig forgottenConfiguredSocket = configuredFixedSockets.remove(position);
                            if (currentlySelectedFixedSocket != null && currentlySelectedFixedSocket.getMDNSHostName().equals(forgottenConfiguredSocket.getMDNSHostName()))
                                resetStateMaintainerModel();

                            saveConfiguredSockets();

                            dialogSelectSocket.dismiss();
                            showSuccessGreenToast((forgottenConfiguredSocket != null ? forgottenConfiguredSocket.getMDNSHostName() : "") + " Forgotten");
                        } else
                        {
                                final PerFixedSocketConfig toBeSelectedFixedSocket = configuredFixedSockets.get(position);

                                if (toBeSelectedFixedSocket.isInternetModeConfigured())
                                {
                                    dialogSelectSocket.dismiss();
                                    new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                            .setTitle("Connect via")
                                            .setSingleChoiceItems(new CharSequence[]{"Local WiFi", "Internet"}, -1, new DialogInterface.OnClickListener()
                                            {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which)
                                                {
                                                    if (currentlySelectedFixedSocket != null && currentlySelectedFixedSocket.getMDNSHostName().equals(toBeSelectedFixedSocket.getMDNSHostName()) && ((which == 1) == isInternetModeActivated))
                                                        showInfoGreyToast("Already connected to "+currentlySelectedFixedSocket.getMDNSHostName()+" via "+(which == 1 ? "Internet" : "Local WiFi"));
                                                    else
                                                    {
                                                        dialog.dismiss();
                                                        resetStateMaintainerModel();
                                                        currentlySelectedFixedSocket = toBeSelectedFixedSocket;
                                                        isInternetModeActivated = which == 1;
                                                        handleNetworkConnectivityChange();
                                                    }
                                                }
                                            })
                                            .setCancelable(true)
                                            .setOnCancelListener(new DialogInterface.OnCancelListener()
                                            {
                                                @Override
                                                public void onCancel(DialogInterface dialog)
                                                {
                                                    // resetStateMaintainerModel();
                                                    dialog.dismiss();
                                                }
                                            })
                                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                                            {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which)
                                                {
                                                    // resetStateMaintainerModel();
                                                    dialog.dismiss();
                                                }
                                            }).create().show();
                                }else
                                {
                                    if (currentlySelectedFixedSocket != null && currentlySelectedFixedSocket.getMDNSHostName().equals(toBeSelectedFixedSocket.getMDNSHostName()) && !isInternetModeActivated)
                                        showInfoGreyToast("Already connected to "+currentlySelectedFixedSocket.getMDNSHostName()+" via Local WiFi");
                                    else
                                    {
                                        dialogSelectSocket.dismiss();
                                        resetStateMaintainerModel();
                                        currentlySelectedFixedSocket = toBeSelectedFixedSocket;
                                        isInternetModeActivated = false;
                                        handleNetworkConnectivityChange();
                                    }
                                }

                        }
                    }
                });
            }

            ListView listViewPortableSockets = selectSocketDialogLayout.findViewById(R.id.listViewPortableSockets);
            if (showToForgetSocket)
            {
                if (configuredPortableSockets.isEmpty())
                {
                    listViewPortableSockets.setAdapter(new ArrayAdapter<>(SmartWiFiSocketActivity.this, android.R.layout.simple_list_item_1, Collections.singletonList("<Empty List>")));
                    listViewPortableSockets.setEnabled(false);
                }else
                    listViewPortableSockets.setAdapter(new ArrayAdapter<>(SmartWiFiSocketActivity.this, android.R.layout.simple_list_item_1, portableSocketNames));
            } else
            {
                portableSocketNames.add("<Use Current WiFi>");
                listViewPortableSockets.setAdapter(new ArrayAdapter<>(SmartWiFiSocketActivity.this, android.R.layout.simple_list_item_1, portableSocketNames));
            }

            listViewPortableSockets.setOnItemClickListener(new AdapterView.OnItemClickListener()
            {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id)
                {

                    if (showToForgetSocket)
                    {
                        String forgottenConfiguredPortableSocket =  portableSocketNames.get(position);
                        if (configuredPortableSockets.remove(forgottenConfiguredPortableSocket))
                        {
                            removePreConfiguredSavedWiFiConfiguration(forgottenConfiguredPortableSocket, true);
                            if (currentlySelectedPortableSocket != null && currentlySelectedPortableSocket.equals(forgottenConfiguredPortableSocket))
                                resetStateMaintainerModel();

                            saveConfiguredSockets();

                            // DEFAULT_SETUP_SSID is the default value because it is guaranteed that DEFAULT_SETUP_SSID will never be the name of a PortableSocket
                            if (sharedPreferences.getString("MobileChargingAutoTurnOffSocket", DEFAULT_SETUP_SSID).equals(forgottenConfiguredPortableSocket))
                                removeChargingAutoTurnOffFromSharedPreferences();

                            dialogSelectSocket.dismiss();
                        }
                    }else
                    {
                        String selectedPortableSocketName = portableSocketNames.get(position);
                        if ("<Use Current WiFi>".equalsIgnoreCase(selectedPortableSocketName))
                        {
                            String currentSSID = getCurrentSSID();
                            if (currentSSID == null)            // getCurrentSSID returned null i.e not connected to any WiFi i.e not connected to any Portable Socket
                                showInfoGreyToast("WiFi not connected");
                            else if (currentSSID.equals(currentlySelectedPortableSocket))
                                showInfoGreyToast("Please select a Socket apart from currently selected");
                            else
                            {
                                if (!configuredPortableSockets.contains(currentSSID))
                                {
                                    configuredPortableSockets.add(currentSSID);
                                    saveConfiguredSockets();
                                }

                                resetStateMaintainerModel();
                                currentlySelectedPortableSocket = currentSSID;
                                dialogSelectSocket.dismiss();
                                handleNetworkConnectivityChange();
                            }
                        }else if (selectedPortableSocketName.equals(currentlySelectedPortableSocket))
                            showInfoGreyToast("Please select a Socket apart from currently selected");
                        else
                        {
                            resetStateMaintainerModel();
                            currentlySelectedPortableSocket = selectedPortableSocketName;
                            dialogSelectSocket.dismiss();
                            handleNetworkConnectivityChange();
                        }
                    }
                }
            });

            // create tab 1
            TabHost.TabSpec fixedSocketTabSpec = tabHostSelectSocket.newTabSpec("Fixed");
            fixedSocketTabSpec.setContent(R.id.listViewFixedSockets);
            fixedSocketTabSpec.setIndicator("Fixed");
            tabHostSelectSocket.addTab(fixedSocketTabSpec);

            //create tab 2
            TabHost.TabSpec portableSocketTabSpec = tabHostSelectSocket.newTabSpec("Portable");
            portableSocketTabSpec.setContent(R.id.listViewPortableSockets);
            portableSocketTabSpec.setIndicator("Portable");
            tabHostSelectSocket.addTab(portableSocketTabSpec);

            dialogSelectSocket.show();

        /*new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Select Socket"+(showToForgetSocket?" to forget":""))
                .setCancelable(true)
                .setSingleChoiceItems(configuredSocketNames, -1, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {

                        if (showToForgetSocket)
                        {
                            PerFixedSocketConfig forgottenConfiguredSocket = configuredFixedSockets.remove(which);
                            if (currentlySelectedFixedSocket != null && currentlySelectedFixedSocket.getMDNSHostName().equals(forgottenConfiguredSocket.getMDNSHostName()))
                                resetStateMaintainerModel();

                            saveConfiguredSockets();

                            dialog.dismiss();
                            Toast.makeText(SmartWiFiSocketActivity.this, (forgottenConfiguredSocket != null ? forgottenConfiguredSocket.getMDNSHostName() : "")+" forgotten", Toast.LENGTH_SHORT).show();
                        }else
                        {
                            resetStateMaintainerModel();
                            currentlySelectedFixedSocket = configuredFixedSockets.get(which);
                            dialog.dismiss();
                            handleNetworkConnectivityChange();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }
                }).create().show();*/
        }
    }

    private void triggerInitialSetupForNewSocket()
    {
        showProgressDialogSearchingInitialSetupSSID();

        if (!wifiManager.isWifiEnabled())
        {
            // https://stackoverflow.com/questions/3930990/android-how-to-enable-disable-wifi-or-internet-connection-programmatically

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
                        Thread.sleep(320);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

                removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                resetStateMaintainerModel();

                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                registerReceiver(initialSetupNetworkConnectivityEventsBroadcastReceiver, intentFilter);

                wifiManager.disconnect();
                wifiManager.startScan();
            }
        }, 2300);
    }

    private void removePreConfiguredSavedWiFiConfiguration(final String wiFiToForget, final boolean fromForgetSocketDialog)
    {
        if (!wifiManager.isWifiEnabled())
        {
            showInfoGreyToast("Turning on WiFi. Please wait");
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

                List<WifiConfiguration> configuredWiFiNetworks = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration wifiConfiguration : configuredWiFiNetworks)
                {
                    if (("\"" + wiFiToForget + "\"").equals(wifiConfiguration.SSID))
                    {
                        wifiManager.removeNetwork(wifiConfiguration.networkId);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                            wifiManager.saveConfiguration();

                        if (fromForgetSocketDialog)
                            showSuccessGreenToast((wiFiToForget != null ? wiFiToForget : "") + " Forgotten");
                    }
                }
            }
        }, 1100);
    }

    private void showProgressDialogSearchingInitialSetupSSID()
    {
        if (customProgressDialogSearchingInitialSetupSSID == null)
        {
            customProgressDialogSearchingInitialSetupSSID = new CustomProgressDialog(null, "Searching for WiFi network: \n\n"+DEFAULT_SETUP_SSID);
            customProgressDialogSearchingInitialSetupSSID.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialogInterface)
                {
                    scanResults = null;
                    try
                    {
                        unregisterReceiver(initialSetupNetworkConnectivityEventsBroadcastReceiver);
                    } catch (IllegalArgumentException e)
                    {
                        System.err.println("Ignored unregisterReceiver error receiver not registered for initialSetupNetworkConnectivityEventsBroadcastReceiver");
                    }

                    removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    resetStateMaintainerModel();

                    dialogInterface.dismiss();
                    customProgressDialogSearchingInitialSetupSSID = null;
                    customProgressDialogConnectingInitialSetupSSID = null;
                }
            });
            customProgressDialogSearchingInitialSetupSSID.show();
        }
    }

    private void dismissProgressDialogSearchingInitialSetupSSID()
    {
        if (customProgressDialogSearchingInitialSetupSSID != null)
        {
            customProgressDialogSearchingInitialSetupSSID.dismiss();
            customProgressDialogSearchingInitialSetupSSID = null;
        }
    }

    private void showProgressDialogConnectingInitialSetupSSID()
    {
        if (customProgressDialogConnectingInitialSetupSSID == null)
        {
            customProgressDialogConnectingInitialSetupSSID = new CustomProgressDialog(null, "Found \""+DEFAULT_SETUP_SSID+"\"\n\nTrying to connect ..");
            customProgressDialogConnectingInitialSetupSSID.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialogInterface)
                {
                    scanResults = null;
                    try
                    {
                        unregisterReceiver(initialSetupNetworkConnectivityEventsBroadcastReceiver);
                    } catch (IllegalArgumentException e)
                    {
                        System.err.println("Ignored unregisterReceiver error receiver not registered for initialSetupNetworkConnectivityEventsBroadcastReceiver");
                    }

                    removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    resetStateMaintainerModel();

                    dialogInterface.dismiss();
                    customProgressDialogSearchingInitialSetupSSID = null;
                    customProgressDialogConnectingInitialSetupSSID = null;
                }
            });
            customProgressDialogConnectingInitialSetupSSID.show();
        }
    }

    private void dismissProgressDialogConnectingInitialSetupSSID()
    {
        if (customProgressDialogConnectingInitialSetupSSID != null)
        {
            customProgressDialogConnectingInitialSetupSSID.dismiss();
            customProgressDialogConnectingInitialSetupSSID = null;
        }
    }

    private void showProgressDialogConnectingPreConfiguredWiFi()
    {
        if (customProgressDialogConnectingPreConfiguredWiFi == null)
        {
            customProgressDialogConnectingPreConfiguredWiFi = new CustomProgressDialog(null, "Connecting to WiFi: " + currentlyConnectingToPreConfiguredWiFi);
            customProgressDialogConnectingPreConfiguredWiFi.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialogInterface)
                {
                    unregisterReceiver(preConfiguredWiFiConnectivityEventsBroadcastReceiver);
                    showInfoGreyToast("Please connect to WiFi: " + currentlyConnectingToPreConfiguredWiFi + " manually and select Socket from menu.");
                    resetStateMaintainerModel();

                    dialogInterface.dismiss();
                    customProgressDialogConnectingPreConfiguredWiFi = null;
                }
            });
            customProgressDialogConnectingPreConfiguredWiFi.show();
        }
    }

    private void dismissProgressDialogConnectingPreConfiguredWiFi()
    {
        if (customProgressDialogConnectingPreConfiguredWiFi != null)
        {
            customProgressDialogConnectingPreConfiguredWiFi.dismiss();
            customProgressDialogConnectingPreConfiguredWiFi = null;
        }

        // Show below Toast only when this dismiss method is called for connecting Home/External WiFi or while configuring new Portable Socket
        if (currentlySelectedPortableSocket == null && currentlySelectedFixedSocket == null)
            showSuccessGreenToast("Please select Socket from menu");
    }

    private long convertMilliSecsToSecs(long milliSecs)
    {
        return milliSecs / 1000;
    }

    private long convertSecsToMilliSecs(long secs)
    {
        return secs * 1000;
    }

    // https://stackoverflow.com/questions/8811315/how-to-get-current-wifi-connection-info-in-android
    // https://stackoverflow.com/questions/21391395/get-ssid-when-wifi-is-connected
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

    private boolean isConnectedToANetwork()
    {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    // https://stackoverflow.com/questions/6493517/detect-if-android-device-has-internet-connection
    boolean hasActiveInternetConnection()
    {
        /*try
        {
            HttpURLConnection url_conn = (HttpURLConnection) (new java.net.URL("http://www.google.com").openConnection());
            url_conn.setRequestProperty("User-Agent", "Test");
            url_conn.setRequestProperty("Connection", "close");
            url_conn.setConnectTimeout(1500);
            url_conn.connect();
            return (url_conn.getResponseCode() == 200);
        }catch (Exception e)
        {
            Log.e(LOG_TAG, "Internet connection un-available", e);
        }

        return false;*/

        if (!isConnectedToANetwork())
            return false;

        try
        {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);   // timeout in 1500 millisecs, connecting to Google DNS server 8.8.8.8 at port 53
            sock.close();

            return true;
        } catch (IOException e)
        {
            return false;
        }
    }

    // https://stackoverflow.com/questions/8818290/how-do-i-connect-to-a-specific-wi-fi-network-in-android-programmatically
    private void connectToPreConfiguredWiFiNetwork(final String preConfiguredWiFi)
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

                WifiConfiguration preConfiguredWifiConfig = null;
                List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                if (configuredNetworks != null)
                {
                    for (WifiConfiguration wifiConfiguration : configuredNetworks)
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
                }

                if (preConfiguredWifiConfig == null)
                {
                    resetStateMaintainerModel();
                    showInfoGreyToast("WiFi not saved. Please connect to WiFi: " + preConfiguredWiFi + " manually and select Socket from menu.");
                } else
                {
                    currentlyConnectingToPreConfiguredWiFi = preConfiguredWiFi;
                    showProgressDialogConnectingPreConfiguredWiFi();

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                    registerReceiver(preConfiguredWiFiConnectivityEventsBroadcastReceiver, intentFilter);

                    wifiManager.disconnect();
                    wifiManager.enableNetwork(preConfiguredWifiConfig.networkId, true);
                    wifiManager.reconnect();
                }

                //return isWiFiNetworkPreConfigured;
            }
        }, 1100);
    }

    private void savePortableSocketWiFiConfiguration(final String preConfiguredWiFi, final String newPortableSocketPwd)
    {
        if (!wifiManager.isWifiEnabled())
        {
            showInfoGreyToast("Turning on WiFi. Please wait");
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

                WifiConfiguration preConfiguredWifiConfig = new WifiConfiguration();
                preConfiguredWifiConfig.SSID = String.format("\"%s\"", preConfiguredWiFi);
                preConfiguredWifiConfig.preSharedKey = String.format("\"%s\"", newPortableSocketPwd);

                // showProgressDialogConnectingPreConfiguredWiFi(preConfiguredWiFi); => don't connect right away after Portable Socket Setup is done, let the user connect from Select menu, only add the network to configured networks

                    /*IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                    currentlyConnectingToPreConfiguredWiFi = preConfiguredWiFi;
                    registerReceiver(preConfiguredWiFiConnectivityEventsBroadcastReceiver, intentFilter);*/

                wifiManager.disconnect();
                wifiManager.enableNetwork(wifiManager.addNetwork(preConfiguredWifiConfig), false);  // false for attemptToConnect
                //wifiManager.reconnect();

                showSuccessGreenToast("Please select Socket from menu");

                //return true;    // true because we just configured/saved the WiFi network but didn't connect to it because of false to attemptToConnect
            }
        }, 1100);
    }

    private void startNSDAndShowDialogSearchingForSelectedFixedSocket()
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

                String currentSSID = getCurrentSSID();

                if (currentlySelectedFixedSocket.getExternalWiFiSSID().equals(currentSSID))
                {
                    nsdForSearchingAllFixedSockets = false;
                    nsdManager.discoverServices(FIXED_SOCKET_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
                    countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.start();

                    customProgressDialogSearchingForSelectedFixedSocket = new CustomProgressDialog("Searching for " + currentlySelectedFixedSocket.getMDNSHostName() + " in WiFi: " + currentSSID, "Please wait");
                    customProgressDialogSearchingForSelectedFixedSocket.setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialogInterface)
                        {
                            dialogInterface.dismiss();
                            countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.cancel();

                            nsdManager.stopServiceDiscovery(discoveryListener);
                            resetStateMaintainerModel();
                        }
                    });
                    customProgressDialogSearchingForSelectedFixedSocket.show();
                }else
                {
                    // not connected to any WiFi network or connected to some other network
                    currentlyConnectingToPreConfiguredWiFi = currentlySelectedFixedSocket.getExternalWiFiSSID();
                    connectToPreConfiguredWiFiNetwork(currentlySelectedFixedSocket.getExternalWiFiSSID());
                }
            }
        }, 1100);
    }

    private void dismissDialogSearchingForSelectedFixedSocket()
    {
        if (customProgressDialogSearchingForSelectedFixedSocket != null)
        {
            customProgressDialogSearchingForSelectedFixedSocket.dismiss();
            customProgressDialogSearchingForSelectedFixedSocket = null;
        }
    }

    private void startNSDAndShowDialogSearchingForAllFixedSockets()
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

                String currentSSID = getCurrentSSID();

                if (currentSSID == null)
                    showInfoGreyToast("Not connected to any external WiFi");
                else
                {
                    if (searchAvailableFixedSocketsResults == null)
                        searchAvailableFixedSocketsResults = new HashSet<>(64);
                    searchAvailableFixedSocketsResults.clear();

                    nsdForSearchingAllFixedSockets = true;

                    nsdManager.discoverServices(FIXED_SOCKET_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
                    countDownTimerStopNSDSearchForAllFixedSocketsOnExpiration.start();

                    customProgressDialogSearchingForAllFixedSockets = new CustomProgressDialog("Searching available Sockets in WiFi: " + currentSSID, "This may take a while. Please wait!");
                    customProgressDialogSearchingForAllFixedSockets.setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialogInterface)
                        {
                            countDownTimerStopNSDSearchForAllFixedSocketsOnExpiration.cancel();

                            nsdManager.stopServiceDiscovery(discoveryListener);
                            searchAvailableFixedSocketsResults.clear();
                        }
                    });
                    customProgressDialogSearchingForAllFixedSockets.show();
                }
            }
        }, 1100);
    }

    private void dismissDialogSearchingForAllFixedSockets()
    {
        if (customProgressDialogSearchingForAllFixedSockets != null)
        {
            customProgressDialogSearchingForAllFixedSockets.dismiss();
            customProgressDialogSearchingForAllFixedSockets = null;
        }
    }

    private void initDiscoveryListener()
    {
        discoveryListener = new NsdManager.DiscoveryListener()
        {
            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType)
            {
                Log.d(LOG_TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service)
            {
                Log.d(LOG_TAG, "Service discovery success: " + service);

                if (FIXED_SOCKET_SERVICE_TYPE.equals(service.getServiceType()))
                {
                    String serviceName = service.getServiceName();
                    if (serviceName != null)
                        serviceName = serviceName.trim();

                    if (serviceName != null && !serviceName.isEmpty() && serviceName.length() > 0)
                    {
                        if (nsdForSearchingAllFixedSockets)
                        {
                            searchAvailableFixedSocketsResults.add(serviceName);
                            // no need to call resolve because we don't need IP and port, we need only name of service. IP and port will be resolved anyways once user selects socket from menu

                            countDownTimerStopNSDSearchForAllFixedSocketsOnExpiration.cancel();
                            countDownTimerStopNSDSearchForAllFixedSocketsOnExpiration.start();
                        } else
                        {
                            if (serviceName.equalsIgnoreCase(currentlySelectedFixedSocket.getMDNSHostName()))
                                nsdManager.resolveService(service, resolveListener);
                            else
                            {
                                countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.cancel();
                                countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.start();
                            }
                        }
                    }
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service)
            {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(LOG_TAG, "service lost: " + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType)
            {
                Log.i(LOG_TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode)
            {
                Log.e(LOG_TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);

                if (!nsdForSearchingAllFixedSockets)
                    handleNetworkConnectivityChange();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode)
            {
                Log.e(LOG_TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);

                if (!nsdForSearchingAllFixedSockets)
                    handleNetworkConnectivityChange();
            }
        };
    }

    // no need to put checks on nsdForSearchingAllFixedSockets because resolve wont be called while searching for all sockets (see above comment)
    private void initResolveListener()
    {
        resolveListener = new NsdManager.ResolveListener()
        {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode)
            {
                // Called when the resolve fails. Use the error code to debug.
                Log.e(LOG_TAG, "Resolve failed" + errorCode);
                nsdManager.stopServiceDiscovery(discoveryListener);

                handleNetworkConnectivityChange();
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo)
            {
                nsdManager.stopServiceDiscovery(discoveryListener);
                Log.d(LOG_TAG, "Resolve Succeeded: " + serviceInfo);

                // port 9911 hardcoded for Smart WiFi Socket in the device
                fixedSocketResolvedPort = serviceInfo.getPort();

                if (fixedSocketResolvedPort == SERVICE_PORT)    // interested only in MDNS services that have port as 9911 (9911 for Smart WiFi Socket)
                {
                    fixedSocketResolvedIPAddress = serviceInfo.getHost().getHostAddress();

                    Log.d(LOG_TAG, fixedSocketResolvedIPAddress);

                    if (Patterns.IP_ADDRESS.matcher(fixedSocketResolvedIPAddress).matches())
                    {
                        countDownTimerStopSearchingForSelectedFixedSocketOnExpiration.cancel();
                        dismissDialogSearchingForSelectedFixedSocket();

                        //new BgTaskGetLatestStateViaWLAN(false, false).execute();
                        new BgTaskGetLatestScheduleTimerInfo(false).execute();           // BgTaskGetLatestStateViaWLAN is called On success of BgTaskGetLatestScheduleTimerInfo
                    } else
                        handleNetworkConnectivityChange();
                }
            }
        };
    }

    private void showAndValidateInitialUserSetup()
    {
        new BgTaskGetSocketSoftwareVersion(true, setupNewSocketAsFixedSocket).execute();

        /*if (setupNewSocketAsFixedSocket)
        {
            new BgTaskGetSocketScannedNearbyWiFiNetworks().execute();*/

            // Not using below as receiver sensitivity can be different of ESP and Mobile, hence making ESP scan nearby networks in init.lua so that we can send the list in Setup.lc
            /*if (scanResults != null && scanResults.size() > 1)    // scanResults size must be > 1 (1 for own SETUP SSID and more than 1 for Home/External WiFi)
            {
                List<CharSequence> nearbySSIDsList = new ArrayList<>();
                for (ScanResult scanResult : scanResults)
                    if (!scanResult.SSID.equals(DEFAULT_SETUP_SSID))
                        nearbySSIDsList.add(scanResult.SSID);

                final CharSequence nearbySSIDs[] = new CharSequence[nearbySSIDsList.size()];
                for (int i = 0; i < nearbySSIDsList.size(); i++)
                    nearbySSIDs[i] = nearbySSIDsList.get(i);

                if (nearbySSIDs.length > 0)
                {

                } else
                    Toast.makeText(SmartWiFiSocketActivity.this, "No nearby WiFi networks found for external WiFi", Toast.LENGTH_SHORT).show();
            }*/
        /*}else
        {
            new BgTaskGetSocketSoftwareVersion(false).execute();
        }*/
    }

    private boolean checkIfSocketNameAlreadyInFixedSockets(String socketName)
    {
        retrieveConfiguredSockets();

        for (PerFixedSocketConfig perFixedSocketConfig : configuredFixedSockets)
            if (perFixedSocketConfig.getMDNSHostName() != null && perFixedSocketConfig.getMDNSHostName().equals(socketName))
                return true;

        return false;
    }

    void resetStateMaintainerModel()
    {
        try
        {
            unregisterReceiver(wiFiInternetDisConnectivityEventsBroadcastReceiver);
        } catch (IllegalArgumentException e)
        {
            System.err.println("Ignored unregisterReceiver error receiver not registered for wiFiInternetDisConnectivityEventsBroadcastReceiver");
        }

        currentlySelectedFixedSocket = null;
        currentlySelectedPortableSocket = null;

        if (allScheduleTimerInfoPojos == null)
            allScheduleTimerInfoPojos = new ArrayList<>();

        allScheduleTimerInfoPojos.clear();

        currentState = States.DISCONNECTED;
        lastStateUpdatedTimestampMillisecs = 0;
        doneRetrievingLatestScheduleTimerList = false;
        isAnyTimerRunning = false;
        // doesListHaveOSCT = false;
        isInternetModeActivated = false;
        madeAnyChangeToRunSkipForTodaysScheduleTimer = false;
        madeAnyChangeToEnabledOSFTScheduleTimers = false;
        madeAnyChangeToEnabledFSScheduleTimers =  false;
        madeAnyChangeToEnabledRSScheduleTimers =  false;
        madeAnyChangeToEnabledRTScheduleTimers = false;

        doubleTimerTimer1DurationSecs = 0;
        doubleTimerTimer2DurationSecs = 0;
        doubleTimerTimer1State = false;
        doubleTimerRunning = false;

        stop_countDownTimerForDisplay(null);
        stop_handlerGetLatestStateEveryNSeconds();

        fixedSocketResolvedIPAddress = "";
        fixedSocketResolvedPort = 0;

        socketSoftwareVersion = 0;

        toolbar.setSubtitle(null);

        refreshViewsForAllTabs(false);
    }

    private void refreshViewsForAllTabs(boolean calledFromMenuItem)
    {
        if (fragmentStateChange != null)
            fragmentStateChange.refreshViews(currentState, null, 0);

        if (fragmentScheduleTimers != null)
            fragmentScheduleTimers.refreshViews();

        if (fragmentSocketConfiguration != null)
            fragmentSocketConfiguration.refreshViews();

        if (fragmentMisc != null)
            fragmentMisc.refreshViews();

        if (calledFromMenuItem)
            showSuccessGreenToast("Done Refreshing all Tabs");
    }

    private void handleNetworkConnectivityChange()
    {
        if (currentlySelectedFixedSocket == null && currentlySelectedPortableSocket == null)
            showInfoGreyToast(getString(R.string.no_socket_selected));
        else
        {
            if (currentlySelectedFixedSocket != null)
            {
                if (isInternetModeActivated)
                {
                    if (googleSignInAccount == null)
                    {
                        showInfoGreyToast("Google Sign In required");
                        resetStateMaintainerModel();
                    }else
                    {
                        showSuccessGreenToast("Internet Mode Activated");

                        toolbar.setSubtitle(currentlySelectedFixedSocket.getMDNSHostName() + "-Fixed(Net)");
                        new BgTaskGetLatestStateViaInternet(true, false, false).execute();
                    }
                } else
                {
                    startNSDAndShowDialogSearchingForSelectedFixedSocket();
                    toolbar.setSubtitle(currentlySelectedFixedSocket.getMDNSHostName() + "-Fixed(WiFi)");
                }
            }

            if(currentlySelectedPortableSocket != null)
            {
                currentlyConnectingToPreConfiguredWiFi = currentlySelectedPortableSocket;
                toolbar.setSubtitle(currentlySelectedPortableSocket + "-Portable(WiFi)");
                connectToPreConfiguredWiFiNetwork(currentlySelectedPortableSocket);
            }

            if (fragmentMisc != null)
                fragmentMisc.refreshViews();
        }
    }

    private void showAndValidateOneShotCurrentTimerDialog()
    {
        View oneShotCurrentTimerDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_one_shot_current_timer, null);

        final ToggleButton toggleBtnOneShotCurrentTimerBeforeState = oneShotCurrentTimerDialogLayout.findViewById(R.id.toggleBtnOneShotCurrentTimerBeforeState);
        final TimeDurationPicker timeDurationPickerOneShotCurrentTimer = oneShotCurrentTimerDialogLayout.findViewById(R.id.timeDurationPickerOneShotCurrentTimer);
        final TextView txtViewOneShotCurrentTimerAfterStateMsg = oneShotCurrentTimerDialogLayout.findViewById(R.id.txtViewOneShotCurrentTimerAfterStateMsg);

        txtViewOneShotCurrentTimerAfterStateMsg.setText(String.format(getString(R.string.after_timer_expires_turn_appliance_3), (toggleBtnOneShotCurrentTimerBeforeState.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON))));
        toggleBtnOneShotCurrentTimerBeforeState.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                txtViewOneShotCurrentTimerAfterStateMsg.setText(String.format(getString(R.string.after_timer_expires_turn_appliance_3), (toggleBtnOneShotCurrentTimerBeforeState.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON))));
            }
        });

        final AlertDialog oneShotCurrentTimerDialog = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("One Shot Current Timer")
                .setView(oneShotCurrentTimerDialogLayout)
                .setCancelable(true)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("Set Timer", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                    }
                })
                .create();

        oneShotCurrentTimerDialog.show();

        oneShotCurrentTimerDialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(timeDurationPickerOneShotCurrentTimer.getDuration()==0)
                    showInfoGreyToast("Timer duration should be greater than 0");
                /*else if (timeDurationPickerOneShotCurrentTimer.getDuration() > ESP_MAX_TIMER_INTERVAL)
                    showInfoGreyToast("Maximum Timer duration allowed is 1h 54m 30s");*/
                else
                {
                    Calendar nowCalendar = Calendar.getInstance();
                    // nowCalendar.setTime(new Date()); commented this as Calendar.getInstance() by default gives us calendar for current timezone https://stackoverflow.com/questions/20471185/what-is-the-default-timezone-for-java-util-calendar

                    Calendar timerElapsedDateTimeCalendar = Calendar.getInstance();
                    timerElapsedDateTimeCalendar.add(Calendar.MILLISECOND, (int) timeDurationPickerOneShotCurrentTimer.getDuration());
                    // timerElapsedDateTimeCalendar.setTimeInMillis(System.currentTimeMillis()+timeDurationPickerOneShotCurrentTimer.getDuration()); commented this as Calendar.getInstance() by default gives us calendar for current timezone https://stackoverflow.com/questions/20471185/what-is-the-default-timezone-for-java-util-calendar

                    if(timerElapsedDateTimeCalendar.get(Calendar.DAY_OF_MONTH) == nowCalendar.get(Calendar.DAY_OF_MONTH))
                    {
                        PojoScheduleTimerInfo pojoScheduleTimerInfo = new PojoScheduleTimerInfo(true, PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER, 0, 0, null, 0, 0, false, toggleBtnOneShotCurrentTimerBeforeState.isChecked(), convertMilliSecsToSecs(timeDurationPickerOneShotCurrentTimer.getDuration()), !toggleBtnOneShotCurrentTimerBeforeState.isChecked(), true);
                        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = isOSCTConflictingOthers(pojoScheduleTimerInfo);
                        if (conflictingOtherPojoScheduleTimerInfo == null)
                        {
                            oneShotCurrentTimerDialog.dismiss();
                            new BgTaskSaveNewOSCTEntryToDevice().execute(pojoScheduleTimerInfo);
                        }else
                        {
                            switch (conflictingOtherPojoScheduleTimerInfo.getScheduleTimerType())
                            {
                                case ONE_SHOT_FUTURE_TIMER:
                                    showInfoGreyToast("Conflicts with an existing One Shot Future Timer");
                                    break;

                                case FUTURE_SCHEDULE:
                                    showInfoGreyToast("Conflicts with an existing Future Schedule");
                                    break;

                                case RECURRING_SCHEDULE:
                                    showInfoGreyToast("Conflicts with an existing Recurring Schedule");
                                    break;

                                case RECURRING_TIMER:
                                    showInfoGreyToast("Conflicts with an existing Recurring Timer");
                                    break;
                            }
                        }

                    }else
                        showInfoGreyToast("Timer on expiration must not spill into next day");
                }
            }
        });
    }

    private void showAndValidateOneShotFutureTimerDialog()
    {
        final SimpleDateFormat oneShotFutureTimerDateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

        View oneShotFutureTimerDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_one_shot_future_timer, null);

        final DatePicker datePickerOneShotFutureTimer = oneShotFutureTimerDialogLayout.findViewById(R.id.datePickerOneShotFutureTimer);
        final TimePicker timePickerOneShotFutureTimer = oneShotFutureTimerDialogLayout.findViewById(R.id.timePickerOneShotFutureTimer);
        final ToggleButton toggleBtnOneShotFutureTimerBeforeState = oneShotFutureTimerDialogLayout.findViewById(R.id.toggleBtnOneShotFutureTimerBeforeState);
        final TimeDurationPicker timeDurationPickerOneShotFutureTimer = oneShotFutureTimerDialogLayout.findViewById(R.id.timeDurationPickerOneShotFutureTimer);
        final TextView txtViewOneShotFutureTimerAfterStateMsg = oneShotFutureTimerDialogLayout.findViewById(R.id.txtViewOneShotFutureTimerAfterStateMsg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            datePickerOneShotFutureTimer.setLayoutMode(1);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            datePickerOneShotFutureTimer.setSpinnersShown(true);

        timePickerOneShotFutureTimer.setIs24HourView(false);

        txtViewOneShotFutureTimerAfterStateMsg.setText(String.format(getString(R.string.after_timer_expires_turn_appliance_5), (toggleBtnOneShotFutureTimerBeforeState.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON))));
        toggleBtnOneShotFutureTimerBeforeState.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                txtViewOneShotFutureTimerAfterStateMsg.setText(String.format(getString(R.string.after_timer_expires_turn_appliance_5), (toggleBtnOneShotFutureTimerBeforeState.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON))));
            }
        });

        final AlertDialog oneShotFutureTimerDialog = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("One Shot Future Timer")
                .setView(oneShotFutureTimerDialogLayout)
                .setCancelable(true)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("Set Timer", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                    }
                })
                .create();

        oneShotFutureTimerDialog.show();

        oneShotFutureTimerDialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                try
                {
                    String strFutureScheduleDateTime;

                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                        strFutureScheduleDateTime = String.format(Locale.getDefault(), "%02d.%02d.%04d %02d:%02d", datePickerOneShotFutureTimer.getDayOfMonth(), (datePickerOneShotFutureTimer.getMonth() + 1), datePickerOneShotFutureTimer.getYear(), timePickerOneShotFutureTimer.getCurrentHour(), timePickerOneShotFutureTimer.getCurrentMinute());
                    else
                        strFutureScheduleDateTime = String.format(Locale.getDefault(), "%02d.%02d.%04d %02d:%02d", datePickerOneShotFutureTimer.getDayOfMonth(), (datePickerOneShotFutureTimer.getMonth() + 1), datePickerOneShotFutureTimer.getYear(), timePickerOneShotFutureTimer.getHour(), timePickerOneShotFutureTimer.getMinute());

                    Log.d(LOG_TAG, "1ShotFutureTimerDialog1~"+strFutureScheduleDateTime);

                    Date futureScheduleDateTime = oneShotFutureTimerDateTimeFormat.parse(strFutureScheduleDateTime);

                    if (futureScheduleDateTime.after(new Date()))
                    {
                        if(timeDurationPickerOneShotFutureTimer.getDuration()==0)
                            showInfoGreyToast("Timer duration should be greater than 0");
                        /*else if (timeDurationPickerOneShotFutureTimer.getDuration() > ESP_MAX_TIMER_INTERVAL)
                            showInfoGreyToast("Maximum Timer duration allowed is 1h 54m 30s");*/
                        else
                        {
                            Calendar futureScheduleDateTimeCalendar = Calendar.getInstance();
                            futureScheduleDateTimeCalendar.setTime(futureScheduleDateTime);

                            Calendar timerElapsedDateTimeCalendar = Calendar.getInstance();
                            timerElapsedDateTimeCalendar.setTimeInMillis(futureScheduleDateTime.getTime() + timeDurationPickerOneShotFutureTimer.getDuration());

                            Log.d(LOG_TAG, "1ShotFutureTimerDialog2~"+oneShotFutureTimerDateTimeFormat.format(timerElapsedDateTimeCalendar.getTime()));

                            if (futureScheduleDateTimeCalendar.get(Calendar.DAY_OF_MONTH) == timerElapsedDateTimeCalendar.get(Calendar.DAY_OF_MONTH))
                            {
                                PojoScheduleTimerInfo pojoScheduleTimerInfo = new PojoScheduleTimerInfo(true, PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER, 0, 0, null, 0, convertMilliSecsToSecs(futureScheduleDateTime.getTime()), false, toggleBtnOneShotFutureTimerBeforeState.isChecked(), convertMilliSecsToSecs(timeDurationPickerOneShotFutureTimer.getDuration()), !toggleBtnOneShotFutureTimerBeforeState.isChecked(), false);
                                PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = isOSFTConflictingOthers(pojoScheduleTimerInfo);
                                if(conflictingOtherPojoScheduleTimerInfo == null)
                                {
                                    oneShotFutureTimerDialog.dismiss();
                                    new BgTaskSaveNewEntryToDevice(isFSOrOSFTScheduledLaterForToday(pojoScheduleTimerInfo)).execute(pojoScheduleTimerInfo);
                                }else
                                {
                                    switch (conflictingOtherPojoScheduleTimerInfo.getScheduleTimerType())
                                    {
                                        case ONE_SHOT_FUTURE_TIMER:
                                            showInfoGreyToast("Conflicts with an existing One Shot Future Timer");
                                            break;

                                        case FUTURE_SCHEDULE:
                                            showInfoGreyToast("Conflicts with an existing Future Schedule");
                                            break;

                                        case RECURRING_SCHEDULE:
                                            showInfoGreyToast("Conflicts with an existing Recurring Schedule");
                                            break;

                                        case RECURRING_TIMER:
                                            showInfoGreyToast("Conflicts with an existing Recurring Timer");
                                            break;
                                    }
                                }
                            }else
                                showInfoGreyToast("Timer on expiration must not spill into next day");
                        }
                    }else
                        showInfoGreyToast("Please select a valid future date and time");
                }catch (Exception e)
                {
                    e.printStackTrace();
                    showErrorRedToast("An error occurred. Please try again.");
                }
            }
        });
    }

    private void showAndValidateFutureScheduleDialog()
    {
        View futureScheduleDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_future_schedule, null);

        final DatePicker datePickerFutureSchedule = futureScheduleDialogLayout.findViewById(R.id.datePickerFutureSchedule);
        final TimePicker timePickerFutureSchedule = futureScheduleDialogLayout.findViewById(R.id.timePickerFutureSchedule);
        final ToggleButton toggleBtnFutureScheduleState = futureScheduleDialogLayout.findViewById(R.id.toggleBtnFutureScheduleState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            datePickerFutureSchedule.setLayoutMode(1);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            datePickerFutureSchedule.setSpinnersShown(true);

        final AlertDialog futureScheduleDialog = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Future Schedule")
                .setView(futureScheduleDialogLayout)
                .setCancelable(true)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("Set Schedule", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                    }
                })
                .create();

        futureScheduleDialog.show();

        futureScheduleDialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                try
                {
                    String strFutureScheduleDateTime;
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                        strFutureScheduleDateTime = String.format(Locale.getDefault(), "%02d.%02d.%04d %02d:%02d", datePickerFutureSchedule.getDayOfMonth(), (datePickerFutureSchedule.getMonth() + 1), datePickerFutureSchedule.getYear(), timePickerFutureSchedule.getCurrentHour(), timePickerFutureSchedule.getCurrentMinute());
                    else
                        strFutureScheduleDateTime = String.format(Locale.getDefault(), "%02d.%02d.%04d %02d:%02d", datePickerFutureSchedule.getDayOfMonth(), (datePickerFutureSchedule.getMonth() + 1), datePickerFutureSchedule.getYear(), timePickerFutureSchedule.getHour(), timePickerFutureSchedule.getMinute());

                    Log.d(LOG_TAG, "FutureScheduleDialog1"+strFutureScheduleDateTime);

                    Date futureScheduleDateTime = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).parse(strFutureScheduleDateTime);

                    if (futureScheduleDateTime.after(new Date()))
                    {
                        PojoScheduleTimerInfo pojoScheduleTimerInfo = new PojoScheduleTimerInfo(true, PojoScheduleTimerInfo.ScheduleTimerType.FUTURE_SCHEDULE, 0, 0, null, 0, convertMilliSecsToSecs(futureScheduleDateTime.getTime()), toggleBtnFutureScheduleState.isChecked(), false, 0, false, false);
                        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = isFSConflictingOthers(pojoScheduleTimerInfo);
                        if(conflictingOtherPojoScheduleTimerInfo == null)
                        {
                            futureScheduleDialog.dismiss();
                            new BgTaskSaveNewEntryToDevice(isFSOrOSFTScheduledLaterForToday(pojoScheduleTimerInfo)).execute(pojoScheduleTimerInfo);
                        }else
                        {
                            switch (conflictingOtherPojoScheduleTimerInfo.getScheduleTimerType())
                            {
                                case ONE_SHOT_FUTURE_TIMER:
                                    showInfoGreyToast("Conflicts with an existing One Shot Future Timer");
                                    break;

                                case FUTURE_SCHEDULE:
                                    showInfoGreyToast("Conflicts with an existing Future Schedule");
                                    break;

                                case RECURRING_SCHEDULE:
                                    showInfoGreyToast("Conflicts with an existing Recurring Schedule");
                                    break;

                                case RECURRING_TIMER:
                                    showInfoGreyToast("Conflicts with an existing Recurring Timer");
                                    break;
                            }
                        }
                    }else
                        showInfoGreyToast("Please select a valid future date and time");
                }catch (Exception e)
                {
                    e.printStackTrace();
                    showErrorRedToast("An error occurred. Please try again.");
                }
            }
        });
    }

    boolean isFSOrOSFTScheduledLaterForToday(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        Calendar calendarTodayMax = Calendar.getInstance();
        calendarTodayMax.setTime(new Date());
        calendarTodayMax.set(Calendar.HOUR_OF_DAY, 23);
        calendarTodayMax.set(Calendar.MINUTE, 59);
        calendarTodayMax.set(Calendar.SECOND, 59);

        return pojoScheduleTimerInfo.getFutureDateTime() > convertMilliSecsToSecs(Calendar.getInstance().getTimeInMillis()) && pojoScheduleTimerInfo.getFutureDateTime() < convertMilliSecsToSecs(calendarTodayMax.getTimeInMillis());
    }

    private void showAndValidateRecurringScheduleDialog()
    {
        View recurringScheduleDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_recurring_schedule, null);

        final ToggleButton toggleBtnRecurringScheduleMo = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleMo);
        final ToggleButton toggleBtnRecurringScheduleTu = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleTu);
        final ToggleButton toggleBtnRecurringScheduleWe = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleWe);
        final ToggleButton toggleBtnRecurringScheduleTh = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleTh);
        final ToggleButton toggleBtnRecurringScheduleFr = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleFr);
        final ToggleButton toggleBtnRecurringScheduleSa = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleSa);
        final ToggleButton toggleBtnRecurringScheduleSu = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleSu);
        final ToggleButton toggleBtnRecurringScheduleAllDays = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleAllDays);

        final CheckBox chkBoxApplyRecurrenceScheduleDateRange = recurringScheduleDialogLayout.findViewById(R.id.chkBoxApplyRecurrenceScheduleDateRange);
        final DatePicker datePickerRecurringScheduleStartDate = recurringScheduleDialogLayout.findViewById(R.id.datePickerRecurringScheduleStartDate);
        final TextView txtViewShowRecurrenceScheduleToMsg = recurringScheduleDialogLayout.findViewById(R.id.txtViewShowRecurrenceScheduleToMsg);
        final DatePicker datePickerRecurringScheduleEndDate = recurringScheduleDialogLayout.findViewById(R.id.datePickerRecurringScheduleEndDate);
        final TimePicker timePickerRecurringSchedule = recurringScheduleDialogLayout.findViewById(R.id.timePickerRecurringSchedule);
        final ToggleButton toggleBtnRecurringScheduleState = recurringScheduleDialogLayout.findViewById(R.id.toggleBtnRecurringScheduleState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            datePickerRecurringScheduleStartDate.setLayoutMode(1);
            datePickerRecurringScheduleEndDate.setLayoutMode(1);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        {
            datePickerRecurringScheduleStartDate.setSpinnersShown(true);
            datePickerRecurringScheduleEndDate.setSpinnersShown(true);
        }

        toggleBtnRecurringScheduleMo.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleTu.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleWe.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleTh.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleFr.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleSa.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleSu.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringScheduleAllDays.setChecked(toggleBtnRecurringScheduleMo.isChecked() && toggleBtnRecurringScheduleTu.isChecked() && toggleBtnRecurringScheduleWe.isChecked() && toggleBtnRecurringScheduleTh.isChecked() && toggleBtnRecurringScheduleFr.isChecked() && toggleBtnRecurringScheduleSa.isChecked() && toggleBtnRecurringScheduleSu.isChecked());
            }
        });

        toggleBtnRecurringScheduleAllDays.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                toggleBtnRecurringScheduleMo.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());
                toggleBtnRecurringScheduleTu.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());
                toggleBtnRecurringScheduleWe.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());
                toggleBtnRecurringScheduleTh.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());
                toggleBtnRecurringScheduleFr.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());
                toggleBtnRecurringScheduleSa.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());
                toggleBtnRecurringScheduleSu.setChecked(toggleBtnRecurringScheduleAllDays.isChecked());

            }
        });

        chkBoxApplyRecurrenceScheduleDateRange.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                datePickerRecurringScheduleStartDate.setVisibility(chkBoxApplyRecurrenceScheduleDateRange.isChecked()?View.VISIBLE:View.GONE);
                txtViewShowRecurrenceScheduleToMsg.setVisibility(chkBoxApplyRecurrenceScheduleDateRange.isChecked()?View.VISIBLE:View.GONE);
                datePickerRecurringScheduleEndDate.setVisibility(chkBoxApplyRecurrenceScheduleDateRange.isChecked()?View.VISIBLE:View.GONE);
            }
        });

        final AlertDialog recurringScheduleAlertDialog = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Recurring Schedule")
                .setView(recurringScheduleDialogLayout)
                .setCancelable(true)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("Set Schedule", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                    }
                })
                .create();

        recurringScheduleAlertDialog.show();

        recurringScheduleAlertDialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {

                PojoScheduleTimerInfo pojoScheduleTimerInfo = null;
                if (toggleBtnRecurringScheduleMo.isChecked() || toggleBtnRecurringScheduleTu.isChecked() || toggleBtnRecurringScheduleWe.isChecked() || toggleBtnRecurringScheduleTh.isChecked() || toggleBtnRecurringScheduleFr.isChecked() || toggleBtnRecurringScheduleFr.isChecked() || toggleBtnRecurringScheduleSa.isChecked() || toggleBtnRecurringScheduleSu.isChecked())
                {
                    try
                    {
                        Date recurringStartDate = new Date(0), recurringEndDate = new Date(0);
                        if(chkBoxApplyRecurrenceScheduleDateRange.isChecked())
                        {
                            recurringStartDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(String.format(Locale.getDefault(), "%02d.%02d.%04d", datePickerRecurringScheduleStartDate.getDayOfMonth(), (datePickerRecurringScheduleStartDate.getMonth() + 1), datePickerRecurringScheduleStartDate.getYear()));
                            recurringEndDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(String.format(Locale.getDefault(), "%02d.%02d.%04d", datePickerRecurringScheduleEndDate.getDayOfMonth(), (datePickerRecurringScheduleEndDate.getMonth() + 1), datePickerRecurringScheduleEndDate.getYear()));

                            Log.d(LOG_TAG, "recurringStartDate: "+recurringStartDate);
                            Log.d(LOG_TAG, "recurringStartDate as secs since epoch: "+convertMilliSecsToSecs(recurringStartDate.getTime()));
                            Log.d(LOG_TAG, "recurringEndDate: "+recurringEndDate);
                            Log.d(LOG_TAG, "recurringEndDate as secs since epoch: "+convertMilliSecsToSecs(recurringEndDate.getTime()));

                            if(recurringEndDate.after(recurringStartDate))
                            {
                                Date recurringTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).parse((Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? timePickerRecurringSchedule.getCurrentHour() : timePickerRecurringSchedule.getHour()) + ":" + (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? timePickerRecurringSchedule.getCurrentMinute() : timePickerRecurringSchedule.getMinute()));
                                if (recurringTime.getTime() >= 0 && recurringTime.getTime() < 300000)
                                    showInfoGreyToast("Please select recurring time greater than 12:05 am");
                                else
                                {
                                    Calendar calendarTemp = Calendar.getInstance();
                                    Log.d(LOG_TAG, "recurringStartDate timezone offset: "+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime()));
                                    Log.d(LOG_TAG, "recurringEndDate timezone offset: "+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime()));
                                    Log.d(LOG_TAG, "recurringStartDate param to POJO: "+convertMilliSecsToSecs(recurringStartDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime())));
                                    Log.d(LOG_TAG, "recurringEndDate param to POJO: "+convertMilliSecsToSecs(recurringEndDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime())));
                                    pojoScheduleTimerInfo = new PojoScheduleTimerInfo(true, PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_SCHEDULE, convertMilliSecsToSecs(recurringStartDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime())), convertMilliSecsToSecs(recurringEndDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime())), new boolean[]{toggleBtnRecurringScheduleMo.isChecked(), toggleBtnRecurringScheduleTu.isChecked(), toggleBtnRecurringScheduleWe.isChecked(), toggleBtnRecurringScheduleTh.isChecked(), toggleBtnRecurringScheduleFr.isChecked(), toggleBtnRecurringScheduleSa.isChecked(), toggleBtnRecurringScheduleSu.isChecked()}, convertMilliSecsToSecs(recurringTime.getTime()), 0, toggleBtnRecurringScheduleState.isChecked(), false, 0, false, false);
                                }
                            }else
                                showInfoGreyToast("Recurring End Date must be after Recurring Start Date");
                        }else
                        {
                            Date recurringTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).parse((Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? timePickerRecurringSchedule.getCurrentHour() : timePickerRecurringSchedule.getHour()) + ":" + (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? timePickerRecurringSchedule.getCurrentMinute() : timePickerRecurringSchedule.getMinute()));
                            if (recurringTime.getTime() >= 0 && recurringTime.getTime() < 300000)
                                showInfoGreyToast("Please select recurring time greater than 12:05 am");
                            else
                            {
                                Calendar calendarTemp = Calendar.getInstance();
                                Log.d(LOG_TAG, "recurringStartDate timezone offset: "+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime()));
                                Log.d(LOG_TAG, "recurringEndDate timezone offset: "+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime()));
                                Log.d(LOG_TAG, "recurringStartDate param to POJO: "+(recurringStartDate.getTime() > 0 ? convertMilliSecsToSecs(recurringStartDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime())) : 0));
                                Log.d(LOG_TAG, "recurringEndDate param to POJO: "+(recurringEndDate.getTime() > 0 ? convertMilliSecsToSecs(recurringEndDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime())) : 0));
                                pojoScheduleTimerInfo = new PojoScheduleTimerInfo(true, PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_SCHEDULE, recurringStartDate.getTime() > 0 ? convertMilliSecsToSecs(recurringStartDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime())) : 0, recurringEndDate.getTime() > 0 ? convertMilliSecsToSecs(recurringEndDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime())) : 0, new boolean[]{toggleBtnRecurringScheduleMo.isChecked(), toggleBtnRecurringScheduleTu.isChecked(), toggleBtnRecurringScheduleWe.isChecked(), toggleBtnRecurringScheduleTh.isChecked(), toggleBtnRecurringScheduleFr.isChecked(), toggleBtnRecurringScheduleSa.isChecked(), toggleBtnRecurringScheduleSu.isChecked()}, convertMilliSecsToSecs(recurringTime.getTime()), 0, toggleBtnRecurringScheduleState.isChecked(), false, 0, false, false);
                            }
                        }

                        if(pojoScheduleTimerInfo!=null)
                        {
                            PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = isRSConflictingOthers(pojoScheduleTimerInfo);
                            if (conflictingOtherPojoScheduleTimerInfo == null)
                            {
                                recurringScheduleAlertDialog.dismiss();
                                new BgTaskSaveNewEntryToDevice(isRSOrRTScheduledForLaterToday(pojoScheduleTimerInfo)).execute(pojoScheduleTimerInfo);
                            }else
                            {
                                switch (conflictingOtherPojoScheduleTimerInfo.getScheduleTimerType())
                                {
                                    case ONE_SHOT_FUTURE_TIMER:
                                        showInfoGreyToast("Conflicts with an existing One Shot Future Timer");
                                        break;

                                    case FUTURE_SCHEDULE:
                                        showInfoGreyToast("Conflicts with an existing Future Schedule");
                                        break;

                                    case RECURRING_SCHEDULE:
                                        showInfoGreyToast("Conflicts with an existing Recurring Schedule");
                                        break;

                                    case RECURRING_TIMER:
                                        showInfoGreyToast("Conflicts with an existing Recurring Timer");
                                        break;
                                }
                            }
                        }
                    }catch (Exception e)
                    {
                        showErrorRedToast("Please select proper recurring start date / end date / time");
                    }
                }else
                    showInfoGreyToast("Please select at least 1 day for recurring schedule");
            }
        });
    }

    private void showAndValidateRecurringTimerDialog()
    {
        View recurringTimerDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_recurring_timer, null);

        final ToggleButton toggleBtnRecurringTimerMo = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerMo);
        final ToggleButton toggleBtnRecurringTimerTu = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerTu);
        final ToggleButton toggleBtnRecurringTimerWe = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerWe);
        final ToggleButton toggleBtnRecurringTimerTh = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerTh);
        final ToggleButton toggleBtnRecurringTimerFr = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerFr);
        final ToggleButton toggleBtnRecurringTimerSa = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerSa);
        final ToggleButton toggleBtnRecurringTimerSu = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerSu);
        final ToggleButton toggleBtnRecurringTimerAllDays = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerAllDays);

        final CheckBox chkBoxApplyRecurrenceTimerDateRange = recurringTimerDialogLayout.findViewById(R.id.chkBoxApplyRecurrenceTimerDateRange);
        final DatePicker datePickerRecurringTimerStartDate = recurringTimerDialogLayout.findViewById(R.id.datePickerRecurringTimerStartDate);
        final TextView txtViewShowRecurrenceTimerToMsg = recurringTimerDialogLayout.findViewById(R.id.txtViewShowRecurrenceTimerToMsg);
        final DatePicker datePickerRecurringTimerEndDate = recurringTimerDialogLayout.findViewById(R.id.datePickerRecurringTimerEndDate);
        final TimePicker timePickerRecurringTimer = recurringTimerDialogLayout.findViewById(R.id.timePickerRecurringTimer);
        final ToggleButton toggleBtnRecurringTimerBeforeState = recurringTimerDialogLayout.findViewById(R.id.toggleBtnRecurringTimerBeforeState);
        final TimeDurationPicker timeDurationPickerRecurringTimer = recurringTimerDialogLayout.findViewById(R.id.timeDurationPickerRecurringTimer);
        final TextView txtViewRecurringTimerAfterStateMsg = recurringTimerDialogLayout.findViewById(R.id.txtViewRecurringTimerAfterStateMsg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            datePickerRecurringTimerStartDate.setLayoutMode(1);
            datePickerRecurringTimerEndDate.setLayoutMode(1);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        {
            datePickerRecurringTimerStartDate.setSpinnersShown(true);
            datePickerRecurringTimerEndDate.setSpinnersShown(true);
        }

        // using onCheckChanged instead of onClick because only user will check/un-check the checkbox
        // https://stackoverflow.com/questions/22564113/onCheckedChangeListener-or-onclicklistener-with-if-statement-for-checkbox's-whi
        txtViewRecurringTimerAfterStateMsg.setText(String.format(getString(R.string.after_timer_expires_turn_appliance_6), (toggleBtnRecurringTimerBeforeState.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON))));
        toggleBtnRecurringTimerBeforeState.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                txtViewRecurringTimerAfterStateMsg.setText(String.format(getString(R.string.after_timer_expires_turn_appliance_6), (toggleBtnRecurringTimerBeforeState.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON))));
            }
        });

        toggleBtnRecurringTimerMo.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerTu.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerWe.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerTh.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerFr.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerSa.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerSu.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                toggleBtnRecurringTimerAllDays.setChecked(toggleBtnRecurringTimerMo.isChecked() && toggleBtnRecurringTimerTu.isChecked() && toggleBtnRecurringTimerWe.isChecked() && toggleBtnRecurringTimerTh.isChecked() && toggleBtnRecurringTimerFr.isChecked() && toggleBtnRecurringTimerSa.isChecked() && toggleBtnRecurringTimerSu.isChecked());
            }
        });

        toggleBtnRecurringTimerAllDays.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                toggleBtnRecurringTimerMo.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
                toggleBtnRecurringTimerTu.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
                toggleBtnRecurringTimerWe.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
                toggleBtnRecurringTimerTh.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
                toggleBtnRecurringTimerFr.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
                toggleBtnRecurringTimerSa.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
                toggleBtnRecurringTimerSu.setChecked(toggleBtnRecurringTimerAllDays.isChecked());
            }
        });

        chkBoxApplyRecurrenceTimerDateRange.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                datePickerRecurringTimerStartDate.setVisibility(chkBoxApplyRecurrenceTimerDateRange.isChecked()?View.VISIBLE:View.GONE);
                txtViewShowRecurrenceTimerToMsg.setVisibility(chkBoxApplyRecurrenceTimerDateRange.isChecked()?View.VISIBLE:View.GONE);
                datePickerRecurringTimerEndDate.setVisibility(chkBoxApplyRecurrenceTimerDateRange.isChecked()?View.VISIBLE:View.GONE);
            }
        });

        final AlertDialog recurringTimerAlertDialog = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Recurring Timer")
                .setView(recurringTimerDialogLayout)
                .setCancelable(true)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialogInterface.dismiss();
                    }
                })
                .setPositiveButton("Set Timer", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                    }
                })
                .create();

        recurringTimerAlertDialog.show();

        recurringTimerAlertDialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (toggleBtnRecurringTimerMo.isChecked() || toggleBtnRecurringTimerTu.isChecked() || toggleBtnRecurringTimerWe.isChecked() || toggleBtnRecurringTimerTh.isChecked() || toggleBtnRecurringTimerFr.isChecked() || toggleBtnRecurringTimerFr.isChecked() || toggleBtnRecurringTimerSa.isChecked() || toggleBtnRecurringTimerSu.isChecked())
                {
                    if(timeDurationPickerRecurringTimer.getDuration()==0)
                        showInfoGreyToast("Timer duration should be greater than 0");
                    /*else if (timeDurationPickerRecurringTimer.getDuration() > ESP_MAX_TIMER_INTERVAL)
                        showInfoGreyToast("Maximum Timer duration allowed is 1h 54m 30s");*/
                    else
                    {
                        Date recurringStartDate, recurringEndDate;
                        PojoScheduleTimerInfo pojoScheduleTimerInfo = null;
                        if(chkBoxApplyRecurrenceTimerDateRange.isChecked())
                        {
                            try
                            {
                                recurringStartDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(String.format(Locale.getDefault(), "%02d.%02d.%04d", datePickerRecurringTimerStartDate.getDayOfMonth(), (datePickerRecurringTimerStartDate.getMonth() + 1), datePickerRecurringTimerStartDate.getYear()));
                                recurringEndDate = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(String.format(Locale.getDefault(), "%02d.%02d.%04d", datePickerRecurringTimerEndDate.getDayOfMonth(), (datePickerRecurringTimerEndDate.getMonth() + 1), datePickerRecurringTimerEndDate.getYear()));

                                if(recurringEndDate.after(recurringStartDate))
                                    pojoScheduleTimerInfo = validateRecurringTimerNotSpillingIntoNextDay(timeDurationPickerRecurringTimer, timePickerRecurringTimer, toggleBtnRecurringTimerMo, toggleBtnRecurringTimerTu, toggleBtnRecurringTimerWe, toggleBtnRecurringTimerTh, toggleBtnRecurringTimerFr, toggleBtnRecurringTimerSa, toggleBtnRecurringTimerSu, toggleBtnRecurringTimerBeforeState, recurringStartDate, recurringEndDate);
                                else
                                    showInfoGreyToast("Recurring End Date must be after Recurring Start Date");
                            }catch (Exception e)
                            {
                                showErrorRedToast("Please select proper recurring start date / end date / time");
                            }
                        }else
                            pojoScheduleTimerInfo = validateRecurringTimerNotSpillingIntoNextDay(timeDurationPickerRecurringTimer, timePickerRecurringTimer, toggleBtnRecurringTimerMo, toggleBtnRecurringTimerTu, toggleBtnRecurringTimerWe, toggleBtnRecurringTimerTh, toggleBtnRecurringTimerFr, toggleBtnRecurringTimerSa, toggleBtnRecurringTimerSu, toggleBtnRecurringTimerBeforeState, new Date(0), new Date(0));

                        if(pojoScheduleTimerInfo==null)
                            showInfoGreyToast("Timer on expiration must not spill into next day");
                        else
                        {
                            PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = isRTConflictingOthers(pojoScheduleTimerInfo);
                            if(conflictingOtherPojoScheduleTimerInfo == null)
                            {
                                recurringTimerAlertDialog.dismiss();
                                new BgTaskSaveNewEntryToDevice(isRSOrRTScheduledForLaterToday(pojoScheduleTimerInfo)).execute(pojoScheduleTimerInfo);
                            }else
                            {
                                switch (conflictingOtherPojoScheduleTimerInfo.getScheduleTimerType())
                                {
                                    case ONE_SHOT_FUTURE_TIMER:
                                        showInfoGreyToast("Conflicts with an existing One Shot Future Timer");
                                        break;

                                    case FUTURE_SCHEDULE:
                                        showInfoGreyToast("Conflicts with an existing Future Schedule");
                                        break;

                                    case RECURRING_SCHEDULE:
                                        showInfoGreyToast("Conflicts with an existing Recurring Schedule");
                                        break;

                                    case RECURRING_TIMER:
                                        showInfoGreyToast("Conflicts with an existing Recurring Timer");
                                        break;
                                }
                            }
                        }
                    }
                }else
                    showInfoGreyToast("Please select at least 1 day for recurring timer");
            }
        });
    }

    private PojoScheduleTimerInfo validateRecurringTimerNotSpillingIntoNextDay(TimeDurationPicker timeDurationPickerRecurringTimer, TimePicker timePickerRecurringTimer, ToggleButton toggleBtnRecurringTimerMo, ToggleButton toggleBtnRecurringTimerTu, ToggleButton toggleBtnRecurringTimerWe, ToggleButton toggleBtnRecurringTimerTh, ToggleButton toggleBtnRecurringTimerFr, ToggleButton toggleBtnRecurringTimerSa, ToggleButton toggleBtnRecurringTimerSu, ToggleButton toggleBtnRecurringTimerBeforeState, Date recurringStartDate, Date recurringEndDate)
    {
        try
        {
            Date recurringTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).parse((Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? timePickerRecurringTimer.getCurrentHour() : timePickerRecurringTimer.getHour()) + ":" + (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? timePickerRecurringTimer.getCurrentMinute() : timePickerRecurringTimer.getMinute()));
            if (recurringTime.getTime() >= 0 && recurringTime.getTime() < 300000)
                showInfoGreyToast("Please select recurring time greater than 12:05 am");
            else
            {
                Calendar recurringTimeCalendar = Calendar.getInstance();
                recurringTimeCalendar.setTime(recurringTime);

                Calendar elapsedTimerDateTimeCalendar = Calendar.getInstance();
                elapsedTimerDateTimeCalendar.setTimeInMillis(recurringTime.getTime()+timeDurationPickerRecurringTimer.getDuration());

                if (recurringTimeCalendar.get(Calendar.DAY_OF_MONTH) == elapsedTimerDateTimeCalendar.get(Calendar.DAY_OF_MONTH))
                {
                    Calendar calendarTemp = Calendar.getInstance();
                    Log.d(LOG_TAG, "recurringStartDate timezone offset: "+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime()));
                    Log.d(LOG_TAG, "recurringEndDate timezone offset: "+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime()));
                    Log.d(LOG_TAG, "recurringStartDate param to POJO: "+(recurringStartDate.getTime() > 0 ? convertMilliSecsToSecs(recurringStartDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime())) : 0));
                    Log.d(LOG_TAG, "recurringEndDate param to POJO: "+(recurringEndDate.getTime() > 0 ? convertMilliSecsToSecs(recurringEndDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime())) : 0));
                    return new PojoScheduleTimerInfo(true, PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER, recurringStartDate.getTime() > 0 ? convertMilliSecsToSecs(recurringStartDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringStartDate.getTime())) : 0, recurringEndDate.getTime() > 0 ? convertMilliSecsToSecs(recurringEndDate.getTime()+calendarTemp.getTimeZone().getOffset(recurringEndDate.getTime())) : 0, new boolean[]{toggleBtnRecurringTimerMo.isChecked(), toggleBtnRecurringTimerTu.isChecked(), toggleBtnRecurringTimerWe.isChecked(), toggleBtnRecurringTimerTh.isChecked(), toggleBtnRecurringTimerFr.isChecked(), toggleBtnRecurringTimerSa.isChecked(), toggleBtnRecurringTimerSu.isChecked()}, convertMilliSecsToSecs(recurringTime.getTime()), 0, false, toggleBtnRecurringTimerBeforeState.isChecked(), convertMilliSecsToSecs(timeDurationPickerRecurringTimer.getDuration()), !toggleBtnRecurringTimerBeforeState.isChecked(), false);
                }else
                    showInfoGreyToast("Timer on expiration must not spill into next day");
            }
        } catch (Exception e)
        {
            showErrorRedToast("Please select proper recurring time");
        }

        return null;
    }

    boolean isRSOrRTScheduledForLaterToday(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        boolean liesWithinRecurrenceDateRange = false, isTodayPresentInDaysToRunOn = false;
        if (pojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 || pojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
            liesWithinRecurrenceDateRange = true;

        if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && pojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
        {
            long currentSysTime = convertMilliSecsToSecs(System.currentTimeMillis());
            liesWithinRecurrenceDateRange = (currentSysTime > pojoScheduleTimerInfo.getRecurringRangeStartDate() && currentSysTime < pojoScheduleTimerInfo.getRecurringRangeEndDate());
        }

        if (liesWithinRecurrenceDateRange)
        {
            Calendar calendar = Calendar.getInstance();
            // calendar.setTimeInMillis(System.currentTimeMillis()); commented this as Calendar.getInstance() by default gives us calendar for current timezone https://stackoverflow.com/questions/20471185/what-is-the-default-timezone-for-java-util-calendar

            switch (calendar.get(Calendar.DAY_OF_WEEK))
            {
                case Calendar.MONDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[0];
                    break;

                case Calendar.TUESDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[1];
                    break;

                case Calendar.WEDNESDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[2];
                    break;

                case Calendar.THURSDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[3];
                    break;

                case Calendar.FRIDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[4];
                    break;

                case Calendar.SATURDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[5];
                    break;

                case Calendar.SUNDAY:
                    isTodayPresentInDaysToRunOn = pojoScheduleTimerInfo.getDaysToRunOn()[6];
                    break;
            }

            if (isTodayPresentInDaysToRunOn)
            {
                Calendar calendarTodayMax = Calendar.getInstance();
                calendarTodayMax.setTime(new Date());
                calendarTodayMax.set(Calendar.HOUR_OF_DAY, 23);
                calendarTodayMax.set(Calendar.MINUTE, 59);
                calendarTodayMax.set(Calendar.SECOND, 59);

                int recurringTimeHour = Integer.valueOf(new SimpleDateFormat("HH", Locale.getDefault()).format(new Date(convertSecsToMilliSecs(pojoScheduleTimerInfo.getRecurringTime()))));
                int recurringTimeMin = Integer.valueOf(new SimpleDateFormat("mm", Locale.getDefault()).format(new Date(convertSecsToMilliSecs(pojoScheduleTimerInfo.getRecurringTime()))));

                Calendar calendarTodayRecurringTime = Calendar.getInstance();
                calendarTodayRecurringTime.setTime(new Date());
                calendarTodayRecurringTime.set(Calendar.HOUR_OF_DAY, recurringTimeHour);
                calendarTodayRecurringTime.set(Calendar.MINUTE, recurringTimeMin);
                calendarTodayRecurringTime.set(Calendar.SECOND, 0);

                Calendar calendarNow = Calendar.getInstance();
                calendarNow.setTime(new Date());

                return (calendarTodayRecurringTime.after(calendarNow) && calendarTodayRecurringTime.before(calendarTodayMax));
            }else
                return false;
        }else
            return false;
    }

    private PojoScheduleTimerInfo isOSCTConflictingOthers(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        boolean isConflicting = false;
        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = null;

        // declaring variables used for calculations:
        boolean liesWithinOtherRecurrenceDateRange;
        long startTime, elapsedTime, otherStartTime, otherElapsedTime, otherTriggerTime;
        Calendar calendar = Calendar.getInstance(), otherCalendar = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        startTime = (System.currentTimeMillis()/1000)+60;   // 60 secs for buffer
        elapsedTime = startTime + pojoScheduleTimerInfo.getTimerDurationSecs();
        calendar.setTimeInMillis(convertSecsToMilliSecs(startTime));

        for(PojoScheduleTimerInfo otherPojoScheduleTimerInfo:allScheduleTimerInfoPojos)
        {
            switch (otherPojoScheduleTimerInfo.getScheduleTimerType())
            {
                case ONE_SHOT_FUTURE_TIMER:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(sdfDate.format(calendar.getTime()).equalsIgnoreCase(sdfDate.format(otherCalendar.getTime())))    // both are on same dates
                    {
                        otherStartTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();
                        if(startTime >= otherStartTime && elapsedTime <= otherElapsedTime) // new timer contained within others's timer range
                            isConflicting = true;
                        else if(otherStartTime >= startTime && otherElapsedTime <= elapsedTime) // other timer range contained within new timer range
                            isConflicting = true;
                        else if(startTime >= otherStartTime && startTime <= otherElapsedTime)
                            isConflicting = true;
                        else if(elapsedTime >= otherStartTime && elapsedTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;

                case FUTURE_SCHEDULE:
                    isConflicting = false;
                    otherTriggerTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                    if(otherTriggerTime >= startTime && otherTriggerTime <= elapsedTime)
                        isConflicting = true;
                    break;

                case RECURRING_SCHEDULE:
                    isConflicting = liesWithinOtherRecurrenceDateRange = false;
                    if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
                        liesWithinOtherRecurrenceDateRange = true;
                    else if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                        liesWithinOtherRecurrenceDateRange = (startTime >= otherPojoScheduleTimerInfo.getRecurringRangeStartDate() && startTime <= otherPojoScheduleTimerInfo.getRecurringRangeEndDate());

                    if(liesWithinOtherRecurrenceDateRange && otherPojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(calendar.get(Calendar.DAY_OF_WEEK) - 2)])
                    {
                            Calendar todaysOtherRecurringScheduleTriggerTime = Calendar.getInstance();
                            todaysOtherRecurringScheduleTriggerTime.set(Calendar.HOUR_OF_DAY, 0);
                            todaysOtherRecurringScheduleTriggerTime.set(Calendar.MINUTE, 0);
                            todaysOtherRecurringScheduleTriggerTime.set(Calendar.SECOND, 0);
                            todaysOtherRecurringScheduleTriggerTime.set(Calendar.MILLISECOND, 0);
                            todaysOtherRecurringScheduleTriggerTime.add(Calendar.SECOND, (int) otherPojoScheduleTimerInfo.getRecurringTime());
                            //todaysOtherRecurringScheduleTriggerTime.setTimeInMillis(todaysOtherRecurringScheduleTriggerTime.getTimeInMillis() + convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getRecurringTime()));
                            otherTriggerTime = convertMilliSecsToSecs(todaysOtherRecurringScheduleTriggerTime.getTimeInMillis());

                            if(otherTriggerTime >= startTime && otherTriggerTime <= elapsedTime)
                                isConflicting = true;
                    }
                    break;

                case RECURRING_TIMER:
                    isConflicting = liesWithinOtherRecurrenceDateRange = false;
                    if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
                        liesWithinOtherRecurrenceDateRange = true;
                    else if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                        liesWithinOtherRecurrenceDateRange = (startTime >= otherPojoScheduleTimerInfo.getRecurringRangeStartDate() && startTime <= otherPojoScheduleTimerInfo.getRecurringRangeEndDate());

                    if(liesWithinOtherRecurrenceDateRange && otherPojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(calendar.get(Calendar.DAY_OF_WEEK) - 2)])
                    {
                        Calendar todaysOtherRecurringScheduleStartTime = Calendar.getInstance();
                        todaysOtherRecurringScheduleStartTime.set(Calendar.HOUR_OF_DAY, 0);
                        todaysOtherRecurringScheduleStartTime.set(Calendar.MINUTE, 0);
                        todaysOtherRecurringScheduleStartTime.set(Calendar.SECOND, 0);
                        todaysOtherRecurringScheduleStartTime.set(Calendar.MILLISECOND, 0);
                        // todaysOtherRecurringScheduleStartTime.setTimeInMillis(todaysOtherRecurringScheduleStartTime.getTimeInMillis() + convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getRecurringTime()));
                        todaysOtherRecurringScheduleStartTime.add(Calendar.SECOND, (int) otherPojoScheduleTimerInfo.getRecurringTime());
                        otherStartTime = convertMilliSecsToSecs(todaysOtherRecurringScheduleStartTime.getTimeInMillis());
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                        if(startTime >= otherStartTime && elapsedTime <= otherElapsedTime) // new timer contained within others's timer range
                            isConflicting = true;
                        else if(otherStartTime >= startTime && otherElapsedTime <= elapsedTime) // other timer range contained within new timer range
                            isConflicting = true;
                        else if(startTime >= otherStartTime && startTime <= otherElapsedTime)
                            isConflicting = true;
                        else if(elapsedTime >= otherStartTime && elapsedTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;
            }

            if(isConflicting)
            {
                conflictingOtherPojoScheduleTimerInfo = otherPojoScheduleTimerInfo;
                break;
            }
        }

        return conflictingOtherPojoScheduleTimerInfo;
    }
    private PojoScheduleTimerInfo isOSFTConflictingOthers(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        boolean isConflicting = false;
        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = null;

        // declaring variables used for calculations:
        boolean liesWithinOtherRecurrenceDateRange;
        long startTime, elapsedTime, otherStartTime, otherElapsedTime, otherTriggerTime;
        Calendar calendar = Calendar.getInstance(), otherCalendar = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        startTime = pojoScheduleTimerInfo.getFutureDateTime();
        elapsedTime = startTime + pojoScheduleTimerInfo.getTimerDurationSecs();
        calendar.setTimeInMillis(convertSecsToMilliSecs(startTime));

        for(PojoScheduleTimerInfo otherPojoScheduleTimerInfo:allScheduleTimerInfoPojos)
        {
            switch (otherPojoScheduleTimerInfo.getScheduleTimerType())
            {
                case ONE_SHOT_FUTURE_TIMER:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(sdfDate.format(calendar.getTime()).equalsIgnoreCase(sdfDate.format(otherCalendar.getTime())))    // both are on same dates
                    {
                        otherStartTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();
                        if(startTime >= otherStartTime && elapsedTime <= otherElapsedTime) // new timer contained within others's timer range
                            isConflicting = true;
                        else if(otherStartTime >= startTime && otherElapsedTime <= elapsedTime) // other timer range contained within new timer range
                            isConflicting = true;
                        else if(startTime >= otherStartTime && startTime <= otherElapsedTime)
                            isConflicting = true;
                        else if(elapsedTime >= otherStartTime && elapsedTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;

                case FUTURE_SCHEDULE:
                    isConflicting = false;
                    otherTriggerTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                    if(otherTriggerTime >= startTime && otherTriggerTime <= elapsedTime)
                        isConflicting = true;
                    break;

                case RECURRING_SCHEDULE:
                    isConflicting = liesWithinOtherRecurrenceDateRange = false;
                    if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
                        liesWithinOtherRecurrenceDateRange = true;
                    else if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                        liesWithinOtherRecurrenceDateRange = (startTime >= otherPojoScheduleTimerInfo.getRecurringRangeStartDate() && startTime <= otherPojoScheduleTimerInfo.getRecurringRangeEndDate());

                    if(liesWithinOtherRecurrenceDateRange && otherPojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(calendar.get(Calendar.DAY_OF_WEEK) - 2)])
                    {
                        Calendar otherRecurringScheduleTriggerTime = Calendar.getInstance();
                        otherRecurringScheduleTriggerTime.setTimeInMillis(calendar.getTimeInMillis());
                        otherRecurringScheduleTriggerTime.set(Calendar.HOUR_OF_DAY, 0);
                        otherRecurringScheduleTriggerTime.set(Calendar.MINUTE, 0);
                        otherRecurringScheduleTriggerTime.set(Calendar.SECOND, 0);
                        otherRecurringScheduleTriggerTime.set(Calendar.MILLISECOND, 0);
                        // otherRecurringScheduleTriggerTime.setTimeInMillis(otherRecurringScheduleTriggerTime.getTimeInMillis()+convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getRecurringTime()));
                        otherRecurringScheduleTriggerTime.add(Calendar.SECOND, (int) otherPojoScheduleTimerInfo.getRecurringTime());
                        otherTriggerTime = convertMilliSecsToSecs(otherRecurringScheduleTriggerTime.getTimeInMillis());

                        if(otherTriggerTime >= startTime && otherTriggerTime <= elapsedTime)
                            isConflicting = true;
                    }
                    break;

                case RECURRING_TIMER:
                    isConflicting = liesWithinOtherRecurrenceDateRange = false;
                    if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
                        liesWithinOtherRecurrenceDateRange = true;
                    else if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                        liesWithinOtherRecurrenceDateRange = (startTime >= otherPojoScheduleTimerInfo.getRecurringRangeStartDate() && startTime <= otherPojoScheduleTimerInfo.getRecurringRangeEndDate());

                    if(liesWithinOtherRecurrenceDateRange && otherPojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(calendar.get(Calendar.DAY_OF_WEEK) - 2)])
                    {
                        Calendar otherRecurringTimerStartTime = Calendar.getInstance();
                        otherRecurringTimerStartTime.setTimeInMillis(calendar.getTimeInMillis());
                        otherRecurringTimerStartTime.set(Calendar.HOUR_OF_DAY, 0);
                        otherRecurringTimerStartTime.set(Calendar.MINUTE, 0);
                        otherRecurringTimerStartTime.set(Calendar.SECOND, 0);
                        otherRecurringTimerStartTime.set(Calendar.MILLISECOND, 0);
                        // otherRecurringScheduleTriggerTime.setTimeInMillis(otherRecurringScheduleTriggerTime.getTimeInMillis()+convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getRecurringTime()));
                        otherRecurringTimerStartTime.add(Calendar.SECOND, (int) otherPojoScheduleTimerInfo.getRecurringTime());
                        otherStartTime = convertMilliSecsToSecs(otherRecurringTimerStartTime.getTimeInMillis());
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                        if(startTime >= otherStartTime && elapsedTime <= otherElapsedTime) // new timer contained within others's timer range
                            isConflicting = true;
                        else if(otherStartTime >= startTime && otherElapsedTime <= elapsedTime) // other timer range contained within new timer range
                            isConflicting = true;
                        else if(startTime >= otherStartTime && startTime <= otherElapsedTime)
                            isConflicting = true;
                        else if(elapsedTime >= otherStartTime && elapsedTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;
            }

            if(isConflicting)
            {
                conflictingOtherPojoScheduleTimerInfo = otherPojoScheduleTimerInfo;
                break;
            }
        }

        return conflictingOtherPojoScheduleTimerInfo;
    }
    private PojoScheduleTimerInfo isFSConflictingOthers(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        boolean isConflicting = false;
        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = null;

        // declaring variables used for calculations:
        boolean liesWithinOtherRecurrenceDateRange;
        long startTime = 0, otherStartTime, otherElapsedTime, triggerTime, otherTriggerTime;
        Calendar calendar = Calendar.getInstance(), otherCalendar = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        triggerTime = pojoScheduleTimerInfo.getFutureDateTime();
        calendar.setTimeInMillis(convertSecsToMilliSecs(triggerTime));

        for(PojoScheduleTimerInfo otherPojoScheduleTimerInfo:allScheduleTimerInfoPojos)
        {
            switch (otherPojoScheduleTimerInfo.getScheduleTimerType())
            {
                case ONE_SHOT_FUTURE_TIMER:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(sdfDate.format(calendar.getTime()).equalsIgnoreCase(sdfDate.format(otherCalendar.getTime())))    // both are on same dates
                    {
                        otherStartTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();
                        if (triggerTime >= otherStartTime && triggerTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;

                case FUTURE_SCHEDULE:
                    isConflicting = false;
                    otherTriggerTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                    if (triggerTime == otherTriggerTime)
                        isConflicting = true;
                    break;

                case RECURRING_SCHEDULE:
                    isConflicting = liesWithinOtherRecurrenceDateRange = false;
                    if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
                        liesWithinOtherRecurrenceDateRange = true;
                    else if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                        liesWithinOtherRecurrenceDateRange = (triggerTime >= otherPojoScheduleTimerInfo.getRecurringRangeStartDate() && triggerTime <= otherPojoScheduleTimerInfo.getRecurringRangeEndDate());

                    if(liesWithinOtherRecurrenceDateRange && otherPojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(calendar.get(Calendar.DAY_OF_WEEK) - 2)])
                    {
                        Calendar otherRecurringScheduleTriggerTime = Calendar.getInstance();
                        otherRecurringScheduleTriggerTime.setTimeInMillis(calendar.getTimeInMillis());
                        otherRecurringScheduleTriggerTime.set(Calendar.HOUR_OF_DAY, 0);
                        otherRecurringScheduleTriggerTime.set(Calendar.MINUTE, 0);
                        otherRecurringScheduleTriggerTime.set(Calendar.SECOND, 0);
                        otherRecurringScheduleTriggerTime.set(Calendar.MILLISECOND, 0);
                        otherRecurringScheduleTriggerTime.add(Calendar.SECOND, (int) otherPojoScheduleTimerInfo.getRecurringTime());
                        otherTriggerTime = convertMilliSecsToSecs(otherRecurringScheduleTriggerTime.getTimeInMillis());

                        if (triggerTime == otherTriggerTime)
                            isConflicting = true;
                    }
                    break;

                case RECURRING_TIMER:
                    isConflicting = liesWithinOtherRecurrenceDateRange = false;
                    if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() == 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() == 0)
                        liesWithinOtherRecurrenceDateRange = true;
                    else if(otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                        liesWithinOtherRecurrenceDateRange = (startTime >= otherPojoScheduleTimerInfo.getRecurringRangeStartDate() && startTime <= otherPojoScheduleTimerInfo.getRecurringRangeEndDate());

                    if(liesWithinOtherRecurrenceDateRange && otherPojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(calendar.get(Calendar.DAY_OF_WEEK) - 2)])
                    {
                        Calendar otherRecurringTimerStartTime = Calendar.getInstance();
                        otherRecurringTimerStartTime.setTimeInMillis(calendar.getTimeInMillis());
                        otherRecurringTimerStartTime.set(Calendar.HOUR_OF_DAY, 0);
                        otherRecurringTimerStartTime.set(Calendar.MINUTE, 0);
                        otherRecurringTimerStartTime.set(Calendar.SECOND, 0);
                        otherRecurringTimerStartTime.set(Calendar.MILLISECOND, 0);
                        // otherRecurringScheduleTriggerTime.setTimeInMillis(otherRecurringScheduleTriggerTime.getTimeInMillis()+convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getRecurringTime()));
                        otherRecurringTimerStartTime.add(Calendar.SECOND, (int) otherPojoScheduleTimerInfo.getRecurringTime());
                        otherStartTime = convertMilliSecsToSecs(otherRecurringTimerStartTime.getTimeInMillis());
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                        if (triggerTime >= otherStartTime && triggerTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;
            }

            if(isConflicting)
            {
                conflictingOtherPojoScheduleTimerInfo = otherPojoScheduleTimerInfo;
                break;
            }
        }

        return conflictingOtherPojoScheduleTimerInfo;
    }
    private PojoScheduleTimerInfo isRSConflictingOthers(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        boolean isConflicting = false;
        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = null;

        // declaring variables used for calculations:
        boolean commonDaysToRunOn[], commonDaysPresent, areRecurrenceDateRangesOverlapping;

        long otherStartTime, otherElapsedTime, triggerTime, otherTriggerTime;
        Calendar otherCalendar = Calendar.getInstance(), tempCalendar = Calendar.getInstance();

        for(PojoScheduleTimerInfo otherPojoScheduleTimerInfo:allScheduleTimerInfoPojos)
        {
            switch (otherPojoScheduleTimerInfo.getScheduleTimerType())
            {
                case ONE_SHOT_FUTURE_TIMER:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(pojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(otherCalendar.get(Calendar.DAY_OF_WEEK) - 2)])   // both are on same days
                    {
                        tempCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                        tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                        tempCalendar.set(Calendar.MINUTE, 0);
                        tempCalendar.set(Calendar.SECOND, 0);
                        tempCalendar.set(Calendar.MILLISECOND, 0);
                        tempCalendar.add(Calendar.SECOND, (int) pojoScheduleTimerInfo.getRecurringTime());
                        triggerTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis());

                        otherStartTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();
                        if (triggerTime >= otherStartTime && triggerTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;

                case FUTURE_SCHEDULE:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(pojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(otherCalendar.get(Calendar.DAY_OF_WEEK) - 2)])   // both are on same days
                    {
                        tempCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                        tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                        tempCalendar.set(Calendar.MINUTE, 0);
                        tempCalendar.set(Calendar.SECOND, 0);
                        tempCalendar.set(Calendar.MILLISECOND, 0);
                        tempCalendar.add(Calendar.SECOND, (int) pojoScheduleTimerInfo.getRecurringTime());
                        triggerTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis());

                        otherTriggerTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        if (triggerTime == otherTriggerTime)
                            isConflicting = true;
                    }
                    break;

                case RECURRING_SCHEDULE:
                    areRecurrenceDateRangesOverlapping = true;
                    if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && pojoScheduleTimerInfo.getRecurringRangeEndDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                    {
                        if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > otherPojoScheduleTimerInfo.getRecurringRangeEndDate())
                            areRecurrenceDateRangesOverlapping = false;
                        if (pojoScheduleTimerInfo.getRecurringRangeEndDate() < otherPojoScheduleTimerInfo.getRecurringRangeStartDate())
                            areRecurrenceDateRangesOverlapping = false;
                    }

                    if (areRecurrenceDateRangesOverlapping)
                    {
                        commonDaysToRunOn = new boolean[7];
                        commonDaysPresent = false;
                        for (int i = 0; i < pojoScheduleTimerInfo.getDaysToRunOn().length; i++)
                        {
                            commonDaysToRunOn[i] = pojoScheduleTimerInfo.getDaysToRunOn()[i] && otherPojoScheduleTimerInfo.getDaysToRunOn()[i];
                            if (commonDaysToRunOn[i])
                                commonDaysPresent = true;
                        }

                        if (commonDaysPresent)
                        {
                            tempCalendar = Calendar.getInstance();
                            for (int i = 0; i < commonDaysToRunOn.length; i++, tempCalendar.add(Calendar.DATE, 1))
                                if (commonDaysToRunOn[Math.abs(tempCalendar.get(Calendar.DAY_OF_WEEK))])
                                    break;

                            tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                            tempCalendar.set(Calendar.MINUTE, 0);
                            tempCalendar.set(Calendar.SECOND, 0);
                            tempCalendar.set(Calendar.MILLISECOND, 0);
                            triggerTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + pojoScheduleTimerInfo.getRecurringTime();
                            otherTriggerTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + otherPojoScheduleTimerInfo.getRecurringTime();

                            if (triggerTime == otherTriggerTime)
                                isConflicting = true;
                        }
                    }
                    break;

                case RECURRING_TIMER:
                    areRecurrenceDateRangesOverlapping = true;
                    if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && pojoScheduleTimerInfo.getRecurringRangeEndDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                    {
                        if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > otherPojoScheduleTimerInfo.getRecurringRangeEndDate())
                            areRecurrenceDateRangesOverlapping = false;
                        if (pojoScheduleTimerInfo.getRecurringRangeEndDate() < otherPojoScheduleTimerInfo.getRecurringRangeStartDate())
                            areRecurrenceDateRangesOverlapping = false;
                    }

                    if (areRecurrenceDateRangesOverlapping)
                    {
                        commonDaysToRunOn = new boolean[7];
                        commonDaysPresent = false;
                        for (int i = 0; i < pojoScheduleTimerInfo.getDaysToRunOn().length; i++)
                        {
                            commonDaysToRunOn[i] = pojoScheduleTimerInfo.getDaysToRunOn()[i] && otherPojoScheduleTimerInfo.getDaysToRunOn()[i];
                            if (commonDaysToRunOn[i])
                                commonDaysPresent = true;
                        }

                        if (commonDaysPresent)
                        {
                            tempCalendar = Calendar.getInstance();
                            for (int i = 0; i < commonDaysToRunOn.length; i++, tempCalendar.add(Calendar.DATE, 1))
                                if (commonDaysToRunOn[Math.abs(tempCalendar.get(Calendar.DAY_OF_WEEK))])
                                    break;

                            tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                            tempCalendar.set(Calendar.MINUTE, 0);
                            tempCalendar.set(Calendar.SECOND, 0);
                            tempCalendar.set(Calendar.MILLISECOND, 0);
                            triggerTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + pojoScheduleTimerInfo.getRecurringTime();
                            otherStartTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + otherPojoScheduleTimerInfo.getRecurringTime();
                            otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                            if (triggerTime >= otherStartTime && triggerTime <= otherElapsedTime)
                                isConflicting = true;
                        }
                    }
                    break;
            }

            if(isConflicting)
            {
                conflictingOtherPojoScheduleTimerInfo = otherPojoScheduleTimerInfo;
                break;
            }
        }

        return conflictingOtherPojoScheduleTimerInfo;
    }

    private PojoScheduleTimerInfo isRTConflictingOthers(PojoScheduleTimerInfo pojoScheduleTimerInfo)
    {
        boolean isConflicting = false;
        PojoScheduleTimerInfo conflictingOtherPojoScheduleTimerInfo = null;

        // declaring variables used for calculations:
        boolean commonDaysToRunOn[], commonDaysPresent, areRecurrenceDateRangesOverlapping;

        long startTime, elapsedTime, otherStartTime, otherElapsedTime, otherTriggerTime;
        Calendar otherCalendar = Calendar.getInstance(), tempCalendar = Calendar.getInstance();

        for(PojoScheduleTimerInfo otherPojoScheduleTimerInfo:allScheduleTimerInfoPojos)
        {
            switch (otherPojoScheduleTimerInfo.getScheduleTimerType())
            {
                case ONE_SHOT_FUTURE_TIMER:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(pojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(otherCalendar.get(Calendar.DAY_OF_WEEK) - 2)])   // both are on same days
                    {
                        tempCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                        tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                        tempCalendar.set(Calendar.MINUTE, 0);
                        tempCalendar.set(Calendar.SECOND, 0);
                        tempCalendar.set(Calendar.MILLISECOND, 0);
                        tempCalendar.add(Calendar.SECOND, (int) pojoScheduleTimerInfo.getRecurringTime());
                        startTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis());
                        elapsedTime = startTime + pojoScheduleTimerInfo.getTimerDurationSecs();

                        otherStartTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                        if(startTime >= otherStartTime && elapsedTime <= otherElapsedTime) // new timer contained within others's timer range
                            isConflicting = true;
                        else if(otherStartTime >= startTime && otherElapsedTime <= elapsedTime) // other timer range contained within new timer range
                            isConflicting = true;
                        else if(startTime >= otherStartTime && startTime <= otherElapsedTime)
                            isConflicting = true;
                        else if(elapsedTime >= otherStartTime && elapsedTime <= otherElapsedTime)
                            isConflicting = true;
                    }
                    break;

                case FUTURE_SCHEDULE:
                    isConflicting = false;
                    otherCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                    if(pojoScheduleTimerInfo.getDaysToRunOn()[Math.abs(otherCalendar.get(Calendar.DAY_OF_WEEK) - 2)])   // both are on same days
                    {
                        tempCalendar.setTimeInMillis(convertSecsToMilliSecs(otherPojoScheduleTimerInfo.getFutureDateTime()));
                        tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                        tempCalendar.set(Calendar.MINUTE, 0);
                        tempCalendar.set(Calendar.SECOND, 0);
                        tempCalendar.set(Calendar.MILLISECOND, 0);
                        tempCalendar.add(Calendar.SECOND, (int) pojoScheduleTimerInfo.getRecurringTime());
                        startTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis());
                        elapsedTime = startTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                        otherTriggerTime = otherPojoScheduleTimerInfo.getFutureDateTime();
                        if (otherTriggerTime >= startTime && otherTriggerTime <= elapsedTime)
                            isConflicting = true;
                    }
                    break;

                case RECURRING_SCHEDULE:
                    areRecurrenceDateRangesOverlapping = true;
                    if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && pojoScheduleTimerInfo.getRecurringRangeEndDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                    {
                        if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > otherPojoScheduleTimerInfo.getRecurringRangeEndDate())
                            areRecurrenceDateRangesOverlapping = false;
                        if (pojoScheduleTimerInfo.getRecurringRangeEndDate() < otherPojoScheduleTimerInfo.getRecurringRangeStartDate())
                            areRecurrenceDateRangesOverlapping = false;
                    }

                    if (areRecurrenceDateRangesOverlapping)
                    {
                        commonDaysToRunOn = new boolean[7];
                        commonDaysPresent = false;
                        for (int i = 0; i < pojoScheduleTimerInfo.getDaysToRunOn().length; i++)
                        {
                            commonDaysToRunOn[i] = pojoScheduleTimerInfo.getDaysToRunOn()[i] && otherPojoScheduleTimerInfo.getDaysToRunOn()[i];
                            if (commonDaysToRunOn[i])
                                commonDaysPresent = true;
                        }

                        if (commonDaysPresent)
                        {
                            tempCalendar = Calendar.getInstance();
                            for (int i = 0; i < commonDaysToRunOn.length; i++, tempCalendar.add(Calendar.DATE, 1))
                                if (commonDaysToRunOn[Math.abs(tempCalendar.get(Calendar.DAY_OF_WEEK))])
                                    break;

                            tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                            tempCalendar.set(Calendar.MINUTE, 0);
                            tempCalendar.set(Calendar.SECOND, 0);
                            tempCalendar.set(Calendar.MILLISECOND, 0);

                            startTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + pojoScheduleTimerInfo.getRecurringTime();
                            elapsedTime = startTime + pojoScheduleTimerInfo.getTimerDurationSecs();

                            otherTriggerTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + otherPojoScheduleTimerInfo.getRecurringTime();

                            if (otherTriggerTime >= startTime && otherTriggerTime <= elapsedTime)
                                isConflicting = true;
                        }
                    }
                    break;

                case RECURRING_TIMER:
                    areRecurrenceDateRangesOverlapping = true;
                    if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && pojoScheduleTimerInfo.getRecurringRangeEndDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeStartDate() > 0 && otherPojoScheduleTimerInfo.getRecurringRangeEndDate() > 0)
                    {
                        if (pojoScheduleTimerInfo.getRecurringRangeStartDate() > otherPojoScheduleTimerInfo.getRecurringRangeEndDate())
                            areRecurrenceDateRangesOverlapping = false;
                        if (pojoScheduleTimerInfo.getRecurringRangeEndDate() < otherPojoScheduleTimerInfo.getRecurringRangeStartDate())
                            areRecurrenceDateRangesOverlapping = false;
                    }

                    if (areRecurrenceDateRangesOverlapping)
                    {
                        commonDaysToRunOn = new boolean[7];
                        commonDaysPresent = false;
                        for (int i = 0; i < pojoScheduleTimerInfo.getDaysToRunOn().length; i++)
                        {
                            commonDaysToRunOn[i] = pojoScheduleTimerInfo.getDaysToRunOn()[i] && otherPojoScheduleTimerInfo.getDaysToRunOn()[i];
                            if (commonDaysToRunOn[i])
                                commonDaysPresent = true;
                        }

                        if (commonDaysPresent)
                        {
                            tempCalendar = Calendar.getInstance();
                            for (int i = 0; i < commonDaysToRunOn.length; i++, tempCalendar.add(Calendar.DATE, 1))
                                if (commonDaysToRunOn[Math.abs(tempCalendar.get(Calendar.DAY_OF_WEEK))])
                                    break;

                            tempCalendar.set(Calendar.HOUR_OF_DAY, 0);
                            tempCalendar.set(Calendar.MINUTE, 0);
                            tempCalendar.set(Calendar.SECOND, 0);
                            tempCalendar.set(Calendar.MILLISECOND, 0);

                            startTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + pojoScheduleTimerInfo.getRecurringTime();
                            elapsedTime = startTime + pojoScheduleTimerInfo.getTimerDurationSecs();

                            otherStartTime = convertMilliSecsToSecs(tempCalendar.getTimeInMillis()) + otherPojoScheduleTimerInfo.getRecurringTime();
                            otherElapsedTime = otherStartTime + otherPojoScheduleTimerInfo.getTimerDurationSecs();

                            if (startTime >= otherStartTime && elapsedTime <= otherElapsedTime) // new timer contained within others's timer range
                                isConflicting = true;
                            else if (otherStartTime >= startTime && otherElapsedTime <= elapsedTime) // other timer range contained within new timer range
                                isConflicting = true;
                            else if (startTime >= otherStartTime && startTime <= otherElapsedTime)
                                isConflicting = true;
                            else if (elapsedTime >= otherStartTime && elapsedTime <= otherElapsedTime)
                                isConflicting = true;
                        }
                    }
                    break;
            }

            if(isConflicting)
            {
                conflictingOtherPojoScheduleTimerInfo = otherPojoScheduleTimerInfo;
                break;
            }
        }

        return conflictingOtherPojoScheduleTimerInfo;
    }

    private class BgTaskGetSocketSoftwareVersion extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private String responseBodyString = null;
        private boolean showProgressDialog = false;
        private boolean alsoGetSocketScannedNearbyWiFiNetworks = false;

        BgTaskGetSocketSoftwareVersion(boolean showProgressDialog, boolean alsoGetSocketScannedNearbyWiFiNetworks)
        {
            this.showProgressDialog = showProgressDialog;
            this.alsoGetSocketScannedNearbyWiFiNetworks = alsoGetSocketScannedNearbyWiFiNetworks;
        }

        @Override
        protected void onPreExecute()
        {
            if (showProgressDialog)
            {
                asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Getting Socket version", "Please wait");
                asyncTaskCustomProgressDialog.show();
            }
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                // reference: https://github.com/square/okhttp/wiki/Recipes
                Request request = new Request.Builder()
                        .url("http://"+SETUP_SERVER_IP+"/get_socket_software_version")
                        .build();

                if (request != null)
                {
                    Response response = okHttpWLANClient.newCall(request).execute();

                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    ResponseBody responseBody = response.body();

                    if (responseBody != null)
                    {
                        responseBodyString = responseBody.string();
                        if (responseBodyString != null)
                            responseBodyString = responseBodyString.trim();
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
            if (showProgressDialog && asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if (exceptionOccurred == null)
            {
                if (responseBodyString != null && responseBodyString.length() > 0 && !"error".equalsIgnoreCase(responseBodyString) && !"empty".equalsIgnoreCase(responseBodyString))
                {
                    socketSoftwareVersion = Integer.parseInt(responseBodyString);

                    if (showProgressDialog)
                    {
                        if (alsoGetSocketScannedNearbyWiFiNetworks)
                            new BgTaskGetSocketScannedNearbyWiFiNetworks().execute();
                        else
                            showAndValidatePortableSocketSetup();
                    }else
                        showAndValidateDownloadApplySocketVersion();
                } else
                {
                    showErrorRedToast("Could not get Socket version");
                    removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                }
            }else
            {
                removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);

                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskGetSocketScannedNearbyWiFiNetworks extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private String responseBodyString = null;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Getting nearby WiFi networks from socket", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            socketScannedNearbyWiFiNetworks.clear();

            // reference: https://github.com/square/okhttp/wiki/Recipes
            HttpUrl httpUrl = HttpUrl.parse("http://" + SETUP_SERVER_IP + "/read_file_line_by_line");

            while (true)
            {
                try
                {
                    if (httpUrl != null)
                    {
                        Request request = new Request.Builder()
                                .url(httpUrl.newBuilder().addQueryParameter("fname", "scanned_nearby_wifi_networks").build().toString())
                                .build();

                        if (request != null)
                        {
                            Response response = okHttpWLANClient.newCall(request).execute();

                            if (!response.isSuccessful())
                                throw new IOException("Unexpected code " + response);

                            ResponseBody responseBody = response.body();

                            if (responseBody != null)
                            {
                                responseBodyString = responseBody.string();

                                if (responseBodyString != null && responseBodyString.length() > 0)
                                {
                                    responseBodyString = responseBodyString.trim();

                                    Log.d(LOG_TAG, "scanned_nearby_wifi_networks: "+responseBodyString);

                                    if ("error".equalsIgnoreCase(responseBodyString))
                                        throw new Exception();
                                    else if ("empty".equalsIgnoreCase(responseBodyString))
                                        break;
                                    else
                                        socketScannedNearbyWiFiNetworks.add(responseBodyString.substring(0, responseBodyString.length() - 1) + (responseBodyString.charAt(responseBodyString.length() - 1) == '1' ? " (Strong)" : " (Weak)"));
                                }else
                                    throw new Exception();
                            }
                        }
                    }
                }catch (Exception e)
                {
                    Log.e(LOG_TAG, e.getMessage() == null ? "null" : e.getMessage());
                    e.printStackTrace();
                    return e;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception exceptionOccurred)
        {
            if (asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if (exceptionOccurred == null)
            {
                if (socketScannedNearbyWiFiNetworks.size() == 0)
                {
                    showInfoGreyToast("No nearby external WiFi networks found");
                    removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                } else
                {
                    Set<String> socketScannedNearbyWiFiNetworksSorted = new TreeSet<>(socketScannedNearbyWiFiNetworks);
                    CharSequence[] nearbySSIDs = new CharSequence[socketScannedNearbyWiFiNetworksSorted.size() /*+ 1*/ ];
                    int i = 0;
                    for (String ssid : socketScannedNearbyWiFiNetworksSorted)
                        nearbySSIDs[i++] = ssid;
                    //nearbySSIDs[i] = "~Use Hidden WiFi~";

                    showAndValidateFixedSocketSetup_SelectExternalWiFi(nearbySSIDs);
                }
            }else
            {
                removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);

                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private void showAndValidateFixedSocketSetup_SelectExternalWiFi(final CharSequence[] nearbySSIDs)
    {
        new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Initial Setup\nSelect external WiFi")
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    }
                })
                .setSingleChoiceItems(nearbySSIDs, -1, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        String temp = nearbySSIDs[which].toString();

                        if (temp.equalsIgnoreCase("~Use Hidden WiFi~"))
                        {
                            dialog.dismiss();

                            final EditText editTextGetHiddenWiFiName = new EditText(SmartWiFiSocketActivity.this);
                            editTextGetHiddenWiFiName.setHint("Enter Hidden WiFi Name");

                            final AlertDialog dialogGetHiddenWiFiName = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                    .setTitle("Enter Hidden WiFi name")
                                    .setView(editTextGetHiddenWiFiName)
                                    .setCancelable(true)
                                    .setOnCancelListener(new DialogInterface.OnCancelListener()
                                    {
                                        @Override
                                        public void onCancel(DialogInterface dialog)
                                        {
                                            dialog.dismiss();
                                            removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                                        }
                                    })
                                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {
                                            dialog.dismiss();
                                            removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                                        }
                                    })
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {

                                        }
                                    }).create();

                            dialogGetHiddenWiFiName.show();

                            dialogGetHiddenWiFiName.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
                            {
                                @Override
                                public void onClick(View v)
                                {
                                    if (editTextGetHiddenWiFiName.getText().toString().isEmpty())
                                        showInfoGreyToast("Please enter Hidden WiFi Name");
                                    else if (editTextGetHiddenWiFiName.getText().toString().contains("~"))
                                        showInfoGreyToast("Sorry, character ~ is not allowed");
                                    else
                                    {
                                        dialogGetHiddenWiFiName.dismiss();
                                        scanResults = null;
                                        showAndValidateFixedSocketSetup_NextSteps(editTextGetHiddenWiFiName.getText().toString());
                                    }
                                }
                            });
                        }else
                        {
                            if (temp.contains("~"))
                                showInfoGreyToast("Sorry, character ~ is not allowed");
                            else
                            {
                                if (temp.endsWith(" (Strong)"))
                                    temp = temp.substring(0, temp.length() - " (Strong)".length());
                                else if (temp.endsWith(" (Weak)"))
                                    temp = temp.substring(0, temp.length() - " (Weak)".length());

                                dialog.dismiss();
                                scanResults = null;
                                showAndValidateFixedSocketSetup_NextSteps(temp);
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialogInterface.dismiss();
                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    }
                }).create().show();
    }

    private void showAndValidateFixedSocketSetup_NextSteps(final String initialSetupExternalSocketHostSSID)
    {
        if (TimezoneData.performChecks(SmartWiFiSocketActivity.this))
        {
            ExpandableListView expandableListView = new ExpandableListView(SmartWiFiSocketActivity.this);
            expandableListView.setAdapter(new CustomExpandableListAdapter(SmartWiFiSocketActivity.this, TimezoneData.expandableGroupHeadersText, TimezoneData.expandableListViewData));

            final AlertDialog selectTimezoneDialog = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                    .setTitle("Select Mobile and Socket Timezone Region")
                    .setCancelable(true)
                    .setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                        }
                    })
                    .setView(expandableListView)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                        }
                    })
                    .create();

            expandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener()
            {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
                {
                    String selectedRegion = TimezoneData.expandableListViewData.get(TimezoneData.expandableGroupHeadersText.get(groupPosition)).get(childPosition);
                    final String TZIDToStore = TimezoneData.regionToTZIDs.get(selectedRegion);
                    String StoredTZIDToRegion = TimezoneData.TZIDsToRegion.get(TZIDToStore);
                    String localtimeFile = TimezoneData.regionToLocaltimeContents.get(selectedRegion);
                    String phoneTZ = TimeZone.getDefault().getID();
                    boolean existsInAssetsZoneinfoFolder = AssetsUtil.checkIfFileExistsInAssets(SmartWiFiSocketActivity.this, "zoneinfo/" + localtimeFile);

                    Set<String> localtimeTZFileAndAllSoftLinks = new HashSet<>();
                    for (Map.Entry<String, List<String>> entry:TimezoneData.localtimeContentsToTZIDs.entrySet())
                    {
                        if (entry.getKey().equals(localtimeFile) || entry.getValue().contains(localtimeFile))
                        {
                            localtimeTZFileAndAllSoftLinks.add(entry.getKey());
                            localtimeTZFileAndAllSoftLinks.addAll(entry.getValue());
                            break;
                        }
                    }
                    boolean phoneTZIsSameAsLocaltimeFile = localtimeTZFileAndAllSoftLinks.contains(phoneTZ);

                    Set<String> StoredTZIDTZFileAndAllSoftLinks = new HashSet<>();
                    for (Map.Entry<String, List<String>> entry:TimezoneData.localtimeContentsToTZIDs.entrySet())
                    {
                        if (entry.getKey().equals(TZIDToStore) || entry.getValue().contains(TZIDToStore))
                        {
                            StoredTZIDTZFileAndAllSoftLinks.add(entry.getKey());
                            StoredTZIDTZFileAndAllSoftLinks.addAll(entry.getValue());
                            break;
                        }
                    }
                    boolean phoneTZIsSameAsSocketTimezone = StoredTZIDTZFileAndAllSoftLinks.contains(phoneTZ);

                    Log.d(LOG_TAG, "SelectedRegion: " + selectedRegion);
                    Log.d(LOG_TAG, "TZIDToStore: " + TZIDToStore);
                    Log.d(LOG_TAG, "StoredTZIDToRegion: " + StoredTZIDToRegion);
                    Log.d(LOG_TAG, "localtime: " + localtimeFile);
                    Log.d(LOG_TAG, "phoneTZ: " + phoneTZ);
                    Log.d(LOG_TAG, "localtimeTZFileAndAllSoftLinks: " + localtimeTZFileAndAllSoftLinks.toString());
                    Log.d(LOG_TAG, "phoneTZ Same as selected region?: " + phoneTZIsSameAsLocaltimeFile);
                    Log.d(LOG_TAG, "localtimeFile exists in assets?: " + existsInAssetsZoneinfoFolder);
                    Log.d(LOG_TAG, "StoredTZIDTZFileAndAllSoftLinks: " + StoredTZIDTZFileAndAllSoftLinks.toString());
                    Log.d(LOG_TAG, "phoneTZIsSameAsSocketTimezone?: " + phoneTZIsSameAsSocketTimezone);

                    boolean timezoneSelectionOK = false;
                    if ("NA".equalsIgnoreCase(selectedRegion))
                    {
                        localtimeHexStringToTransfer = null;

                        timezoneSelectionOK = true;
                        selectTimezoneDialog.dismiss();
                    }else
                    {
                        if (phoneTZIsSameAsLocaltimeFile)
                        {
                            if (StoredTZIDToRegion.equals(selectedRegion))
                            {
                                if (phoneTZIsSameAsSocketTimezone)
                                {
                                    String localtimeHexString = AssetsUtil.readAssetFileAsHexString(SmartWiFiSocketActivity.this, "zoneinfo/" + localtimeFile, true);

                                    if (localtimeHexString != null)
                                    {
                                        Log.d(LOG_TAG, "localtimeHexString length: " + localtimeHexString.length());
                                        Log.d(LOG_TAG, localtimeHexString);

                                        localtimeHexStringToTransfer = localtimeHexString;

                                        timezoneSelectionOK = true;
                                        selectTimezoneDialog.dismiss();

                                        // new BgTaskTestLocaltimeTransfer(localtimeHexString).execute();
                                    } else
                                    {
                                        showInfoGreyToast("Something went wrong. Please try again");
                                        Log.e(LOG_TAG, "localtime hexString is null? true");
                                    }
                                } else
                                {
                                    showInfoGreyToast("Something went wrong. Please try again");
                                    Log.e(LOG_TAG, "phoneTZIsSameAsSocketTimezone: " + false);
                                }
                            } else
                            {
                                showInfoGreyToast("Something went wrong. Please try again");
                                Log.e(LOG_TAG, "StoredTZIDToRegion.equals(selectedRegion): " + StoredTZIDToRegion.equals(selectedRegion));
                            }
                        } else
                            showInfoGreyToast("Phone timezone is not same as selected region's timezone");
                    }

                    if (timezoneSelectionOK)
                    {
                        View initialSetupFixedSocketDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_initial_setup_fixed_socket, null);

                        TextView txtViewSocketVersionString =  initialSetupFixedSocketDialogLayout.findViewById(R.id.txtViewSocketVersionString);
                        TextView txtViewSelectedTimezone = initialSetupFixedSocketDialogLayout.findViewById(R.id.txtViewSelectedTimezone);
                        final EditText editTextExternalSocketHostWiFiPassword = initialSetupFixedSocketDialogLayout.findViewById(R.id.editTextExternalSocketHostWiFiPassword);
                        CheckBox chkBoxShowExternalSocketHostWiFiPassword = initialSetupFixedSocketDialogLayout.findViewById(R.id.chkBoxShowExternalSocketHostWiFiPassword);
                        final EditText editTextInitialSetupNewFixedSocketName = initialSetupFixedSocketDialogLayout.findViewById(R.id.editTextInitialSetupNewFixedSocketName);
                        final CheckBox chkBoxRememberPowerCuts = initialSetupFixedSocketDialogLayout.findViewById(R.id.chkBoxRememberPowerCuts);

                        txtViewSocketVersionString.setText(socketSoftwareVersion == 0 ? getString(R.string.NA) : String.format(getString(R.string.txtview_socket_version_num), socketSoftwareVersion));
                        txtViewSelectedTimezone.setText(StoredTZIDToRegion);
                        editTextExternalSocketHostWiFiPassword.setHint(initialSetupExternalSocketHostSSID + " password");

                        chkBoxShowExternalSocketHostWiFiPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
                        {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                            {
                                editTextExternalSocketHostWiFiPassword.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
                            }
                        });

                        final AlertDialog initialSetupDialogFixedSocket = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                                .setTitle("Initial Setup Contd")
                                .setCancelable(true)
                                .setOnCancelListener(new DialogInterface.OnCancelListener()
                                {
                                    @Override
                                    public void onCancel(DialogInterface dialog)
                                    {
                                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                                    }
                                })
                                .setView(initialSetupFixedSocketDialogLayout)
                                .setPositiveButton("Finish", new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        dialog.dismiss();
                                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                                    }
                                }).create();

                        initialSetupDialogFixedSocket.show();

                        initialSetupDialogFixedSocket.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
                        {
                            @Override
                            public void onClick(View v)
                            {

                                String initialSetupExternalSocketHostPassword = editTextExternalSocketHostWiFiPassword.getText().toString();
                                String initialSetupMDNSFixedSocketName = editTextInitialSetupNewFixedSocketName.getText().toString().trim();

                                if (initialSetupExternalSocketHostPassword.isEmpty())
                                    showInfoGreyToast("External WiFi Password cannot be left empty");
                                else if (initialSetupMDNSFixedSocketName.isEmpty())
                                    showInfoGreyToast("External Socket Name cannot be left empty");
                                else if (checkIfSocketNameAlreadyInFixedSockets(initialSetupMDNSFixedSocketName))
                                    showInfoGreyToast("New Socket name already in use");
                                else
                                {
                                    initialSetupDialogFixedSocket.dismiss();
                                    new BgTaskCompleteInitialUserSetup(initialSetupMDNSFixedSocketName, initialSetupExternalSocketHostSSID, initialSetupExternalSocketHostPassword, "NA".equalsIgnoreCase(TZIDToStore) ? "0NA=" : ("1"+TZIDToStore+"="), localtimeHexStringToTransfer, chkBoxRememberPowerCuts.isChecked(), null, null, false).execute();
                                }
                            }
                        });
                    }

                    return true;
                }
            });

            selectTimezoneDialog.show();
        }
    }

    private void showAndValidatePortableSocketSetup()
    {
        View initialSetupPortableSocketDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_initial_setup_portable_socket, null);

        TextView txtViewSocketVersionString = initialSetupPortableSocketDialogLayout.findViewById(R.id.txtViewSocketVersionString);
        final EditText editTextInitialSetupNewPortableSocketName = initialSetupPortableSocketDialogLayout.findViewById(R.id.editTextInitialSetupNewPortableSocketName);
        final EditText editTextPortableSocketPassword = initialSetupPortableSocketDialogLayout.findViewById(R.id.editTextPortableSocketPassword);
        CheckBox chkBoxShowPortableSocketPassword = initialSetupPortableSocketDialogLayout.findViewById(R.id.chkBoxShowPortableSocketPassword);
        final CheckBox chkBoxSetPortableSocketSSIDHidden = initialSetupPortableSocketDialogLayout.findViewById(R.id.chkBoxSetPortableSocketSSIDHidden);
        final CheckBox chkBoxRememberPowerCuts = initialSetupPortableSocketDialogLayout.findViewById(R.id.chkBoxRememberPowerCuts);

        txtViewSocketVersionString.setText(socketSoftwareVersion == 0 ? getString(R.string.NA) : String.format(getString(R.string.txtview_socket_version_num), socketSoftwareVersion));
        chkBoxShowPortableSocketPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                editTextPortableSocketPassword.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
            }
        });

        // AlertDialog setup as Portable Socket
        final AlertDialog initialSetupDialogPortableSocket = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Portable Socket Setup")
                .setCancelable(true)
                .setView(initialSetupPortableSocketDialogLayout)
                .setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        dialog.dismiss();
                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    }
                })
                .setPositiveButton("Finish", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                    }
                }).create();

        initialSetupDialogPortableSocket.show();

        initialSetupDialogPortableSocket.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String newPortableSocketWiFiSSID = editTextInitialSetupNewPortableSocketName.getText().toString();
                String newPortableSocketWiFiPWD = editTextPortableSocketPassword.getText().toString();

                if (newPortableSocketWiFiSSID.isEmpty())
                    showInfoGreyToast("Portable Socket name cannot be left empty");
                else if (newPortableSocketWiFiPWD.isEmpty())
                    showInfoGreyToast("Portable Socket password cannot be left empty");
                else if (newPortableSocketWiFiPWD.length() < 8)
                    showInfoGreyToast("Portable Socket password must be at least 8 characters");
                else if (DEFAULT_SETUP_SSID.equals(newPortableSocketWiFiSSID))
                    showInfoGreyToast("Portable Socket name cannot be \""+DEFAULT_SETUP_SSID+"\"");
                else
                {
                    initialSetupDialogPortableSocket.dismiss();
                    new BgTaskCompleteInitialUserSetup(null, null, null, null, null, chkBoxRememberPowerCuts.isChecked(), newPortableSocketWiFiSSID, newPortableSocketWiFiPWD, chkBoxSetPortableSocketSSIDHidden.isChecked()).execute();
                }
            }
        });
    }

    private void showAndValidateDownloadApplySocketVersion()
    {
        final CharSequence[] availableVersions = new CharSequence[]{"1"};
        new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                .setTitle("Current version: "+(socketSoftwareVersion == 0 ? getString(R.string.NA) : String.format(getString(R.string.txtview_socket_version_num), socketSoftwareVersion))+". Select version to apply")
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    }
                })
                .setSingleChoiceItems(availableVersions, -1, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        if (Integer.parseInt(availableVersions[which].toString()) == socketSoftwareVersion)
                        {
                            showInfoGreyToast("Socket version is already " + (socketSoftwareVersion == 0 ? getString(R.string.NA) : String.format(getString(R.string.txtview_socket_version_num), socketSoftwareVersion)));
                            removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                        }else
                        {
                            Log.d(LOG_TAG, "Async task to download .lc files and check authenticity");
                            // Async task to download .lc files and transfer to Socket (before downloading check authenticity of server/client or do public pvt key encryption so that only this mobile can get the .lc files)
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                    }
                })
                .create().show();
    }

    String convertStringToHexString(String text) throws UnsupportedEncodingException
    {
        return String.format("%x", new BigInteger(1, text.getBytes("UTF-8")));
    }

    private class BgTaskCompleteInitialUserSetup extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean initialSetupDone = false;
        private String initialSetupMDNSFixedSocketName = null, initialSetupExternalSocketHostSSID = null, initialSetupExternalSocketHostPassword = null, initialSetupTimezoneDetails = null;
        private String initialSetupPortableSocketSSID = null, initialSetupPortableSocketPWD = null;
        private boolean initialSetupPortableSocketSetAPAsHidden = false;
        private List<String> localtimeHexString512Chunks = new ArrayList<>();
        private boolean gotAnyErrorWhileFileTransfer = false;
        private boolean initialSetupRememberPowerCuts = false;

        BgTaskCompleteInitialUserSetup(String initialSetupMDNSFixedSocketName, String initialSetupExternalSocketHostSSID, String initialSetupExternalSocketHostPassword, String initialSetupTimezoneDetails, String localtimeHexString, boolean initialSetupRememberPowerCuts, String initialSetupPortableSocketSSID, String initialSetupPortableSocketPWD, boolean initialSetupPortableSocketSetAPAsHidden)
        {
            this.initialSetupMDNSFixedSocketName = initialSetupMDNSFixedSocketName;
            this.initialSetupExternalSocketHostSSID = initialSetupExternalSocketHostSSID;
            this.initialSetupExternalSocketHostPassword = initialSetupExternalSocketHostPassword;
            this.initialSetupTimezoneDetails = initialSetupTimezoneDetails;
            this.localtimeHexString512Chunks = splitEqually(localtimeHexString, 512);
            if (localtimeHexString512Chunks != null)
            {
                Log.d(LOG_TAG, localtimeHexString512Chunks.toString());
                Log.d(LOG_TAG, Integer.toString(localtimeHexString512Chunks.size()));
            }
            this.initialSetupRememberPowerCuts = initialSetupRememberPowerCuts;
            this.initialSetupPortableSocketSSID = initialSetupPortableSocketSSID;
            this.initialSetupPortableSocketPWD = initialSetupPortableSocketPWD;
            this.initialSetupPortableSocketSetAPAsHidden = initialSetupPortableSocketSetAPAsHidden;
        }

        private List<String> splitEqually(String text, int size)
        {
            if (text == null)
                return null;
            else
            {
                List<String> ret = new ArrayList<>((text.length() + size - 1) / size);
                for (int start = 0; start < text.length(); start += size)
                    ret.add(text.substring(start, Math.min(text.length(), start + size)));
                return ret;
            }
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Finishing Initial Setup", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                if (localtimeHexString512Chunks != null && localtimeHexString512Chunks.size() > 0)
                {
                    HttpUrl httpUrl = HttpUrl.parse("http://"+SETUP_SERVER_IP+"/store_file_onto_flash");

                    if (httpUrl != null)
                    {
                        HttpUrl.Builder urlBuilder;
                        Request request;
                        for (int i=0;i<localtimeHexString512Chunks.size();i++)
                        {
                            urlBuilder = httpUrl.newBuilder();
                            urlBuilder.addQueryParameter("store_as_filename", "localtime");

                            request = new Request.Builder()
                                    .url(urlBuilder.build().toString())
                                    .method("POST", RequestBody.create(null, new byte[0]))
                                    .post(RequestBody.create(MediaType.parse("text/plain; charset=ascii"), localtimeHexString512Chunks.get(i)))
                                    .build();

                            String responseBodyStringFor_localtime = null;
                            if (request != null)
                            {
                                Response response = okHttpWLANClient.newCall(request).execute();

                                if (!response.isSuccessful())
                                    throw new IOException("Unexpected code " + response);

                                ResponseBody responseBody = response.body();

                                if (responseBody != null)
                                {
                                    responseBodyStringFor_localtime = responseBody.string();
                                    if (responseBodyStringFor_localtime != null)
                                        responseBodyStringFor_localtime = responseBodyStringFor_localtime.trim();
                                }
                            }

                            gotAnyErrorWhileFileTransfer = !"success".equalsIgnoreCase(responseBodyStringFor_localtime);
                            if (gotAnyErrorWhileFileTransfer)
                            {
                                Log.d(LOG_TAG, "for index: "+i);
                                Log.d(LOG_TAG, "responseBodyStringFor_localtime is null? " + (responseBodyStringFor_localtime == null));

                                if (responseBodyStringFor_localtime != null)
                                    Log.d(LOG_TAG, "responseBodyStringFor_localtime: " + responseBodyStringFor_localtime);
                                break;
                            }
                        }
                    }
                }

                // reference: https://github.com/square/okhttp/wiki/Recipes
                Log.d(LOG_TAG, Boolean.toString(gotAnyErrorWhileFileTransfer));
                if (!gotAnyErrorWhileFileTransfer)
                {
                    HttpUrl httpUrl = HttpUrl.parse("http://" + SETUP_SERVER_IP + "/" + (setupNewSocketAsFixedSocket ? "setup_fixed_socket" : "setup_portable_socket"));

                    if (httpUrl != null)
                    {
                        HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                        urlBuilder.addQueryParameter("remember_power_cuts", initialSetupRememberPowerCuts ? "1" : "0");
                        if (setupNewSocketAsFixedSocket)
                        {
                            urlBuilder.addQueryParameter("new_socket_name", convertStringToHexString(initialSetupMDNSFixedSocketName));
                            urlBuilder.addQueryParameter("external_socket_host_wifi_ssid", convertStringToHexString(initialSetupExternalSocketHostSSID));
                            urlBuilder.addQueryParameter("external_socket_host_wifi_pwd", convertStringToHexString(initialSetupExternalSocketHostPassword));
                            urlBuilder.addQueryParameter("timezone_details", convertStringToHexString(initialSetupTimezoneDetails));
                        } else
                        {
                            urlBuilder.addQueryParameter("new_socket_wifi_ap_ssid", convertStringToHexString(initialSetupPortableSocketSSID));
                            urlBuilder.addQueryParameter("new_socket_wifi_ap_pwd", convertStringToHexString(initialSetupPortableSocketPWD));
                            urlBuilder.addQueryParameter("set_ap_as_hidden", initialSetupPortableSocketSetAPAsHidden ? "1" : "0");
                        }

                        Request request = new Request.Builder()
                                .url(urlBuilder.build().toString())
                                .build();

                        if (request != null)
                        {
                            Response response = okHttpWLANClient.newCall(request).execute();

                            if (!response.isSuccessful())
                                throw new IOException("Unexpected code " + response);

                            ResponseBody responseBody = response.body();

                            if (responseBody != null)
                            {
                                String responseBodyString = responseBody.string();
                                if (responseBodyString != null)
                                    responseBodyString = responseBodyString.trim();
                                initialSetupDone = "received".equalsIgnoreCase(responseBodyString);
                            }
                        }
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
            resetStateMaintainerModel();

            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if(initialSetupDone)
                {
                    removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);

                    if (setupNewSocketAsFixedSocket)
                        configuredFixedSockets.add(new PerFixedSocketConfig(initialSetupMDNSFixedSocketName, initialSetupExternalSocketHostSSID, initialSetupTimezoneDetails));
                    else
                        configuredPortableSockets.add(initialSetupPortableSocketSSID);

                    saveConfiguredSockets();

                    if (setupNewSocketAsFixedSocket)
                        connectToPreConfiguredWiFiNetwork(initialSetupExternalSocketHostSSID);
                    else
                        savePortableSocketWiFiConfiguration(initialSetupPortableSocketSSID, initialSetupPortableSocketPWD);
                }else
                {
                    showErrorRedToast("Could not complete initial setup. Please retry");
                    removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
                }
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
                removePreConfiguredSavedWiFiConfiguration(DEFAULT_SETUP_SSID, false);
            }
        }
    }

    private class BgTaskGetLatestStateViaWLAN extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private String initialSetupDoneContents = null;
        private String responseBodyString;
        private boolean call_BgTaskStateChangeOverWiFi_once_done = false, desiredState = false, call_addNewScheduleTimerEntryMethod_once_done = false, call_BgTaskInternetModeConfiguration_once_done = false, showProgressDialog = false;
        private PojoScheduleTimerInfo.ScheduleTimerType newScheduleTimerEntryTypeToAdd;
        private boolean internetModeReconfiguring = false;

        // reference : https://stackoverflow.com/questions/3075009/android-how-can-i-pass-parameters-to-asynctasks-onPreExecute
        BgTaskGetLatestStateViaWLAN(boolean showProgressDialog, boolean call_BgTaskStateChangeOverWiFi_once_done, boolean desiredState, boolean call_addNewScheduleTimerEntryMethod_once_done, PojoScheduleTimerInfo.ScheduleTimerType newScheduleTimerEntryTypeToAdd, boolean call_BgTaskInternetModeConfiguration_once_done, boolean internetModeReconfiguring)     // desiredState required if call_BgTaskStateChangeOverWiFi_once_done is true, newScheduleTimerEntryTypeToAdd required if call_addNewScheduleTimerEntryMethod_once_done is true
        {
            this.showProgressDialog = showProgressDialog;
            this.call_BgTaskStateChangeOverWiFi_once_done = call_BgTaskStateChangeOverWiFi_once_done;
            this.desiredState = desiredState;
            this.call_addNewScheduleTimerEntryMethod_once_done = call_addNewScheduleTimerEntryMethod_once_done;
            this.newScheduleTimerEntryTypeToAdd = newScheduleTimerEntryTypeToAdd;
            this.call_BgTaskInternetModeConfiguration_once_done = call_BgTaskInternetModeConfiguration_once_done;
            this.internetModeReconfiguring = internetModeReconfiguring;
        }

        @Override
        protected void onPreExecute()
        {
            if (showProgressDialog)
            {
                asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Getting latest status", "Please wait");
                asyncTaskCustomProgressDialog.show();
            }
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = null;

                HttpUrl httpUrl = null;
                if (currentlySelectedFixedSocket != null)
                    httpUrl = HttpUrl.parse("http://"+ fixedSocketResolvedIPAddress +':'+ fixedSocketResolvedPort +"/read_file_line_by_line");
                else if (currentlySelectedPortableSocket != null)
                    httpUrl = HttpUrl.parse("http://"+ PORTABLE_SOCKET_SERVER_IP +':'+ SERVICE_PORT +"/read_file_line_by_line");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                    urlBuilder.addQueryParameter("fname", "initial_setup_done");

                    request = new Request.Builder()
                            .url(urlBuilder.build().toString())
                            .build();

                    if (request != null)
                    {
                        Response response = okHttpWLANClient.newCall(request).execute();

                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        ResponseBody responseBody = response.body();
                        if (responseBody != null)
                        {
                            initialSetupDoneContents = responseBody.string();
                            if (initialSetupDoneContents != null)
                                initialSetupDoneContents = initialSetupDoneContents.trim();

                            Log.d(LOG_TAG, "initialSetupDoneContents: "+initialSetupDoneContents);
                        }
                    }
                }

                // reading one more line again so that global_file_handle in lua becomes nil for garbage collection
                // also we will get "empty" into responseBodyString when we call read_file_line_by_line which is checked in the next "if"
                if (initialSetupDoneContents != null && httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                    urlBuilder.addQueryParameter("fname", "initial_setup_done");

                    request = new Request.Builder()
                            .url(urlBuilder.build().toString())
                            .build();

                    if (request != null)
                    {
                        Response response = okHttpWLANClient.newCall(request).execute();

                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        ResponseBody responseBody = response.body();
                        if (responseBody != null)
                        {
                            responseBodyString = responseBody.string();
                            if (responseBodyString != null)
                                responseBodyString = responseBodyString.trim();

                            Log.d(LOG_TAG, "initialSetupDoneContents: "+responseBodyString);
                        }
                    }
                }

                if ("empty".equalsIgnoreCase(responseBodyString) && initialSetupDoneContents != null)
                {
                    if (currentlySelectedFixedSocket != null)
                        request = new Request.Builder()
                                .url("http://" + fixedSocketResolvedIPAddress + ':' + fixedSocketResolvedPort + "/gpio/relay_status")
                                .build();
                    else if (currentlySelectedPortableSocket != null)
                        request = new Request.Builder()
                                .url("http://" + PORTABLE_SOCKET_SERVER_IP + ':' + SERVICE_PORT + "/gpio/relay_status")
                                .build();

                    if (request != null)
                    {
                        Response response = okHttpWLANClient.newCall(request).execute();

                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        ResponseBody responseBody = response.body();
                        if (responseBody != null)
                        {
                            responseBodyString = responseBody.string();
                            if (responseBodyString != null)
                                responseBodyString = responseBodyString.trim();

                            Log.d(LOG_TAG, "responseBodyString: "+responseBodyString);
                        }
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
                if (responseBodyString != null && responseBodyString.length() > 0)
                {
                    try
                    {
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                        registerReceiver(wiFiInternetDisConnectivityEventsBroadcastReceiver, intentFilter);
                    }catch (IllegalArgumentException iae)
                    {
                        // ignore the exception of already registered broadcast receiver
                    }

                    if (initialSetupDoneContents != null && currentlySelectedFixedSocket != null)
                        currentlySelectedFixedSocket.setRememberPowerCuts(initialSetupDoneContents.charAt(1) == '1');

                    if (responseBodyString.charAt(0) == '0')
                        currentState = States.OFF_WITH_NO_TIMER;
                    else if (responseBodyString.charAt(0) == '1')
                        currentState = States.ON_WITH_NO_TIMER;
                    else if (responseBodyString.charAt(0) == '2')
                        currentState = States.ON_WITH_TIMER;
                    else if (responseBodyString.charAt(0) == '3')
                        currentState = States.OFF_WITH_TIMER;
                    else if (responseBodyString.charAt(0) == '4')
                        currentState = States.ON_DOUBLE_TIMER_TIMER1;
                    else if (responseBodyString.charAt(0) == '5')
                        currentState = States.OFF_DOUBLE_TIMER_TIMER1;
                    else if (responseBodyString.charAt(0) == '6')
                        currentState = States.ON_DOUBLE_TIMER_TIMER2;
                    else if (responseBodyString.charAt(0) == '7')
                        currentState = States.OFF_DOUBLE_TIMER_TIMER2;

                    lastStateUpdatedTimestampMillisecs = System.currentTimeMillis();

                    if (currentlySelectedFixedSocket != null)
                    {
                        // For Fixed Sockets:   [0123][01]internetModeConfiguredBy~externalWiFiSSID~timezoneDetails~osct/ft/rt;runningTimerCronMaskConfigString;runningTimerSecsLeft OR [01][01]internetModeConfiguredBy~externalWiFiSSID~timezoneDetails
                        //                      Relay,InternetModeConfiguredAndByWhichEmail~externalWiFiSSID~timezoneSynchronizedTo~RunningTimerDetails(for Relay status 2 and 3)

                        String[] responseBodyStringComponents = responseBodyString.split("~");

                        socketSoftwareVersion = Integer.parseInt(responseBodyStringComponents[responseBodyStringComponents.length-1]);
                        currentlySelectedFixedSocket.setSocketSoftwareVersion(socketSoftwareVersion);
                        isAnyTimerRunning = (responseBodyStringComponents[0].charAt(0) == '2' || responseBodyStringComponents[0].charAt(0) == '3');

                        currentlySelectedFixedSocket.setInternetModeConfigured(responseBodyStringComponents[0].charAt(1) == '1');
                        if (currentlySelectedFixedSocket.isInternetModeConfigured())
                            currentlySelectedFixedSocket.setInternetModeConfiguredBy(responseBodyStringComponents[0].substring(2));

                        currentlySelectedFixedSocket.setExternalWiFiSSID(responseBodyStringComponents[1]);
                        currentlySelectedFixedSocket.setTimezoneDetails(responseBodyStringComponents[2]);

                        if (isAnyTimerRunning)
                        {
                            stop_handlerGetLatestStateEveryNSeconds();

                            PojoScheduleTimerInfo temp = new PojoScheduleTimerInfo();
                            String[] runningTimerDetails = responseBodyStringComponents[3].trim().split(";");
                            runningTimerType = runningTimerDetails[0].trim().toUpperCase();
                            runningTimerCronMaskConfigString = runningTimerDetails[1].trim();
                            runningTimerSecsLeft = Long.valueOf(runningTimerDetails[2].trim());

                            if ("osct".equalsIgnoreCase(runningTimerType))
                            {
                                runningTimerPojoObject = temp.parseCronMaskConfigString(runningTimerCronMaskConfigString, PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER);
                                boolean OSCTAlreadyInList = false;
                                for (PojoScheduleTimerInfo pojoScheduleTimerInfo : allScheduleTimerInfoPojos)
                                    if (pojoScheduleTimerInfo.getScheduleTimerType().equals(PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER))
                                    {
                                        OSCTAlreadyInList = true;
                                        break;
                                    }

                                if (!OSCTAlreadyInList)
                                    allScheduleTimerInfoPojos.add(runningTimerPojoObject);
                            }else if ("osft".equalsIgnoreCase(runningTimerType))
                            {
                                for (PojoScheduleTimerInfo pojoScheduleTimerInfo : allScheduleTimerInfoPojos)
                                    if (pojoScheduleTimerInfo.getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER && pojoScheduleTimerInfo.toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                    {
                                        pojoScheduleTimerInfo.setIsRunningNow(true);
                                        runningTimerPojoObject = pojoScheduleTimerInfo;
                                        break;
                                    }
                            }else if ("rt".equalsIgnoreCase(runningTimerType))
                            {
                                for (PojoScheduleTimerInfo pojoScheduleTimerInfo : allScheduleTimerInfoPojos)
                                    if (pojoScheduleTimerInfo.getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER && pojoScheduleTimerInfo.toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                    {
                                        pojoScheduleTimerInfo.setIsRunningNow(true);
                                        runningTimerPojoObject = pojoScheduleTimerInfo;
                                        break;
                                    }
                            }

                            // Update UI (Btn state change with countdown timer and also the list in tab2 and info in tab3)
                            if (fragmentStateChange != null)
                                fragmentStateChange.refreshViews(currentState, runningTimerType, runningTimerSecsLeft);

                            if (fragmentScheduleTimers != null)
                                fragmentScheduleTimers.refreshViews();

                            if (fragmentSocketConfiguration != null)
                                fragmentSocketConfiguration.refreshViews();

                            stop_countDownTimerForDisplay(null);

                            countDownTimerForDisplay = new CountDownTimer(runningTimerSecsLeft * 1000, 1000)
                            {
                                @Override
                                public void onTick(long millisUntilFinished)
                                {
                                    if (fragmentStateChange != null)
                                        fragmentStateChange.refreshViews(currentState, runningTimerType, runningTimerSecsLeft);

                                    runningTimerSecsLeft--;

                                    if (runningTimerSecsLeft % 9 == 0)
                                        call_BgTaskGetLatestStateViaWLAN_not_showing_progressDialog();
                                }

                                @Override
                                public void onFinish()
                                {
                                    isAnyTimerRunning = false;

                                    currentState = runningTimerPojoObject.getAfterTimerExpiresState() ? States.ON_WITH_NO_TIMER : States.OFF_WITH_NO_TIMER;
                                    lastStateUpdatedTimestampMillisecs = System.currentTimeMillis();

                                    if (fragmentStateChange != null)
                                        fragmentStateChange.refreshViews(currentState, null, 0);

                                    if ("OSCT".equalsIgnoreCase(runningTimerType))
                                    {
                                        for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                            if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
                                            {
                                                allScheduleTimerInfoPojos.remove(i);
                                                break;
                                            }
                                    }else if ("OSFT".equalsIgnoreCase(runningTimerType))
                                    {
                                        // don't remove from list for One shot Current Timers as it will be handled by removing past entries
                                        for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                            if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER && allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                            {
                                                allScheduleTimerInfoPojos.get(i).setIsRunningNow(false);
                                                break;
                                            }
                                    }else if ("RT".equalsIgnoreCase(runningTimerType))
                                    {
                                        // don't remove from list for Recurring Timers as it will be handled by removing past entries
                                        for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                            if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER && allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                            {
                                                allScheduleTimerInfoPojos.get(i).setIsRunningNow(false);
                                                break;
                                            }
                                    }

                                    if (fragmentScheduleTimers != null)
                                        fragmentScheduleTimers.refreshViews();

                                    // calling this once so that control goes into below else and start_handlerGetLatestStateEveryNSeconds(4000); gets called
                                    call_BgTaskGetLatestStateViaWLAN_not_showing_progressDialog();
                                }
                            };
                            countDownTimerForDisplay.start();
                        }else
                        {
                            if (fragmentStateChange != null)
                                fragmentStateChange.refreshViews(currentState, null, 0);

                            // adding below if else if ladder for scenarios when user stops timer using physical button
                            if ("OSCT".equalsIgnoreCase(runningTimerType))
                            {
                                for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                    if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
                                    {
                                        allScheduleTimerInfoPojos.remove(i);
                                        break;
                                    }
                            }else if ("OSFT".equalsIgnoreCase(runningTimerType))
                            {
                                // don't remove from list for One shot Current Timers as it will be handled by removing past entries
                                for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                    if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER && allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                    {
                                        allScheduleTimerInfoPojos.get(i).setIsRunningNow(false);
                                        break;
                                    }
                            }else if ("RT".equalsIgnoreCase(runningTimerType))
                            {
                                // don't remove from list for Recurring Timers as it will be handled by removing past entries
                                for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                    if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER && allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                    {
                                        allScheduleTimerInfoPojos.get(i).setIsRunningNow(false);
                                        break;
                                    }
                            }

                            if (fragmentScheduleTimers != null)
                                fragmentScheduleTimers.refreshViews();

                            if (fragmentSocketConfiguration != null)
                                fragmentSocketConfiguration.refreshViews();

                            // only start this when there is no timer running, because running timers will be handled above
                            // we put this because imagine a scenario where in user is connected to fixed socket and after say 10 secs future schedule changes state hence we need this
                            start_handlerGetLatestStateEveryNSeconds(4000);
                        }
                    }else if (currentlySelectedPortableSocket != null)
                    {
                        // For Portable Sockets:    [01]~socketSoftwareVersion
                        //                          [4567]~doubleTimerTimer1DurationSecs~doubleTimerTimer2DurationSecs~doubleTimerTimer1State~currentTimer1Timer2Count~totalTimer1Timer2Count~socketSoftwareVersion

                        String[] responseBodyStringComponents = responseBodyString.split("~");

                        socketSoftwareVersion = Integer.parseInt(responseBodyStringComponents[responseBodyStringComponents.length - 1]);

                        doubleTimerRunning = (responseBodyStringComponents[0].charAt(0) == '4' || responseBodyStringComponents[0].charAt(0) == '5' || responseBodyStringComponents[0].charAt(0) == '6' || responseBodyStringComponents[0].charAt(0) == '7');
                        doubleTimerTimer1DurationSecs = doubleTimerRunning ? Long.valueOf(responseBodyStringComponents[1]) : 0;
                        doubleTimerTimer2DurationSecs = doubleTimerRunning ? Long.valueOf(responseBodyStringComponents[2]) : 0;
                        // doubleTimerTimer1State = doubleTimerRunning ? (responseBodyStringComponents[3].charAt(0) == '1') : false;
                        // above and below are one and the same (below one suggested by IntelliJ itself)
                        doubleTimerTimer1State = doubleTimerRunning && (responseBodyStringComponents[3].charAt(0) == '1');
                        currentTimer1Timer2Count = doubleTimerRunning ? Long.valueOf(responseBodyStringComponents[4]) : 0;
                        totalTimer1Timer2Count = doubleTimerRunning ? Long.valueOf(responseBodyStringComponents[5]) : 0;

                        start_handlerGetLatestStateEveryNSeconds(2000);

                        if (fragmentStateChange != null)
                            fragmentStateChange.refreshViews(currentState, null, 0);

                        if (fragmentMisc != null)
                            fragmentMisc.refreshViews();
                    }

                    if(asyncTaskCustomProgressDialog != null)
                        asyncTaskCustomProgressDialog.dismiss();

                    if (call_BgTaskStateChangeOverWiFi_once_done)
                    {
                        if (currentState == States.OFF_WITH_NO_TIMER || currentState == States.ON_WITH_NO_TIMER)
                        {
                            if (fragmentStateChange != null)
                                fragmentStateChange.call_BgTaskStateChangeOverWiFi(desiredState);
                        }else
                            showInfoGreyToast("Please 1st stop the running timer");
                    }

                    if (call_addNewScheduleTimerEntryMethod_once_done)
                    {
                        if (newScheduleTimerEntryTypeToAdd == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
                        {
                            if (isAnyTimerRunning)
                                showInfoGreyToast(getString(R.string.another_timer_already_running));
                            else
                                addNewScheduleTimerEntry(newScheduleTimerEntryTypeToAdd);
                        }else
                        {
                            if (currentState != States.OFF_WITH_NO_TIMER)
                                showInfoGreyToast(getString(R.string.new_schedule_timer_entry_only_when_appliance_off));
                            else
                                addNewScheduleTimerEntry(newScheduleTimerEntryTypeToAdd);
                        }
                    }

                    if (call_BgTaskInternetModeConfiguration_once_done)
                    {
                        if (isAnyTimerRunning)
                            showInfoGreyToast("Internet Mode Configuration can be done only when no timer is running");
                        else
                        {
                            if (fragmentSocketConfiguration != null)
                                fragmentSocketConfiguration.call_BgTaskInternetModeConfiguration(internetModeReconfiguring);
                        }
                    }

                    saveConfiguredSockets();        // internet mode configured, socket software version and others was set so saved that into shared preferences
                }else
                {
                    if(asyncTaskCustomProgressDialog != null)
                        asyncTaskCustomProgressDialog.dismiss();

                    resetStateMaintainerModel();
                    showErrorRedToast("Could not get latest status. Please try again");
                }
            }else
            {
                if(asyncTaskCustomProgressDialog != null)
                    asyncTaskCustomProgressDialog.dismiss();
                exceptionOccurred.printStackTrace();

                resetStateMaintainerModel();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private void start_handlerGetLatestStateEveryNSeconds(final int periodicInterval)
    {
        if (handlerGetLatestStateViaWLANEverySecond == null)
        {
            handlerGetLatestStateViaWLANEverySecond = new Handler();
            handlerGetLatestStateViaWLANEverySecond.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {

                    if (currentlySelectedFixedSocket != null)
                    {
                        if (isInternetModeActivated)
                            call_BgTaskGetLatestStateViaInternet_not_showing_progressDialog();
                        else
                            call_BgTaskGetLatestStateViaWLAN_not_showing_progressDialog();
                    }

                    if (currentlySelectedPortableSocket != null)
                        call_BgTaskGetLatestStateViaWLAN_not_showing_progressDialog();

                    handlerGetLatestStateViaWLANEverySecond.postDelayed(this, periodicInterval);
                }
            }, periodicInterval);
        }
    }

    void stop_handlerGetLatestStateEveryNSeconds()
    {
        if (handlerGetLatestStateViaWLANEverySecond != null)
        {
            handlerGetLatestStateViaWLANEverySecond.removeCallbacksAndMessages(null);
            handlerGetLatestStateViaWLANEverySecond = null;
        }
    }

    void stop_countDownTimerForDisplay(PojoScheduleTimerInfo pojoRunningTimerWhichUserStopped)
    {
        if (countDownTimerForDisplay != null)
        {
            countDownTimerForDisplay.cancel();
            countDownTimerForDisplay = null;
        }

        if (pojoRunningTimerWhichUserStopped != null)
        {
            pojoRunningTimerWhichUserStopped.setIsRunningNow(false);
            if (pojoRunningTimerWhichUserStopped.getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
            {
                for (int i = 0; i < allScheduleTimerInfoPojos.size(); i++)
                    if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
                    {
                        allScheduleTimerInfoPojos.remove(i);
                        break;
                    }
            }

            isAnyTimerRunning = false;
            if (fragmentScheduleTimers != null)
                fragmentScheduleTimers.refreshViews();
        }
    }

    private void addNewScheduleTimerEntry(PojoScheduleTimerInfo.ScheduleTimerType newScheduleTimerEntryType)
    {
        switch (newScheduleTimerEntryType)
        {
            case ONE_SHOT_CURRENT_TIMER:
                showAndValidateOneShotCurrentTimerDialog();
                break;

            case ONE_SHOT_FUTURE_TIMER:
                showAndValidateOneShotFutureTimerDialog();
                break;

            case FUTURE_SCHEDULE:
                showAndValidateFutureScheduleDialog();
                break;

            case RECURRING_SCHEDULE:
                showAndValidateRecurringScheduleDialog();
                break;

            case RECURRING_TIMER:
                showAndValidateRecurringTimerDialog();
                break;
        }
    }

    void call_BgTaskGetLatestStateViaWLAN()
    {
        new BgTaskGetLatestStateViaWLAN(true, false, false, false, null, false, false).execute();
    }

    private void call_BgTaskGetLatestStateViaWLAN_not_showing_progressDialog()
    {
        new BgTaskGetLatestStateViaWLAN(false, false, false, false, null, false, false).execute();
    }

    void call_BgTaskGetLatestStateViaInternet_not_showing_progressDialog()
    {
        new BgTaskGetLatestStateViaInternet(false, false, false).execute();
    }

    void call_BgTaskGetLatestStateViaWLAN_then_BgTaskStateChangeOverWiFi(boolean desiredState)
    {
        new BgTaskGetLatestStateViaWLAN(true, true, desiredState, false, null, false, false).execute();
    }

    void call_BgTaskGetLatestStateViaWLAN_then_BgTaskInternetModeConfiguration(boolean internetModeReconfiguring)
    {
        new BgTaskGetLatestStateViaWLAN(true, false, false, false, null, true, internetModeReconfiguring).execute();
    }

    void call_BgTaskGetLatestStateViaInternet_then_BgTaskStateChangeOverInternet(boolean desiredState)
    {
        new BgTaskGetLatestStateViaInternet(false, true, desiredState).execute();
    }

    void call_BgTaskGetLatestScheduleTimerInfo(boolean getOnlyTodaysScheduleTimers)
    {
        new BgTaskGetLatestScheduleTimerInfo(getOnlyTodaysScheduleTimers).execute();
    }

    // https://stackoverflow.com/questions/34392422/how-to-remember-sign-in-in-google-account-android
    // https://android-developers.googleblog.com/2015/12/api-updates-for-sign-in-with-google.html
    private class BgTaskTryGoogleSilentSignInIfConnectedToInternet extends AsyncTask<Void, Void, Exception>
    {
        @Override
        protected Exception doInBackground(Void... params)
        {
            if (hasActiveInternetConnection() && !calledGoogleSilentSignInOnceForThisSession)
            {
                OptionalPendingResult<GoogleSignInResult> pendingResult = Auth.GoogleSignInApi.silentSignIn(getGoogleApiClient());
                if (pendingResult != null)
                {
                    if (pendingResult.isDone())
                        assignGoogleSignInAccount(pendingResult.get());
                    else
                        pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>()
                        {
                            @Override
                            public void onResult(@NonNull GoogleSignInResult googleSignInResult)
                            {
                                assignGoogleSignInAccount(googleSignInResult);
                            }
                        });
                }
            }

            return null;
        }

        private void assignGoogleSignInAccount(GoogleSignInResult googleSignInResult)
        {
            if (googleSignInResult != null)
            {
                googleSignInAccount = googleSignInResult.getSignInAccount();
                if (googleSignInAccount != null)
                {
                    calledGoogleSilentSignInOnceForThisSession = true;
                    try
                    {
                        unregisterReceiver(internetConnectivityEventsBroadcastReceiver);
                    }catch (IllegalArgumentException iae)
                    {
                        System.err.println("Ignored unregister receiver error receiver not registered for internetConnectivityEventsBroadcastReceiver");
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Exception exceptionOccurred)
        {
            if(exceptionOccurred==null && googleSignInAccount != null)
            {
                // Silent Sign In so no need to show Toast
                // Toast.makeText(SmartWiFiSocketActivity.this, "Signed in as " + googleSignInAccount.getEmail(), Toast.LENGTH_SHORT).show();
                if (fragmentSocketConfiguration != null)
                    fragmentSocketConfiguration.refreshViews();
            }
        }
    }

    private class BgTaskGetLatestScheduleTimerInfo extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private PojoScheduleTimerInfo tempPojoForParsing = new PojoScheduleTimerInfo();
        private String responseBodyString;
        private boolean getOnlyTodaysScheduleTimers = false, gotErrorWhileRetrieving1OfScheduleTimers = false;

        BgTaskGetLatestScheduleTimerInfo(boolean getOnlyTodaysScheduleTimers)
        {
            this.getOnlyTodaysScheduleTimers = getOnlyTodaysScheduleTimers;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, getOnlyTodaysScheduleTimers ? "Getting today's Schedule/Timers .." : "Getting latest list of Schedule/Timers ..", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                allScheduleTimerInfoPojos.clear();
                doneRetrievingLatestScheduleTimerList = false;
                // doesListHaveOSCT = false;

                if (getOnlyTodaysScheduleTimers)
                    retrieveSpecificScheduleTimerEntries(null);
                else
                {
                    retrieveSpecificScheduleTimerEntries(PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER);
                    if (!gotErrorWhileRetrieving1OfScheduleTimers)
                        retrieveSpecificScheduleTimerEntries(PojoScheduleTimerInfo.ScheduleTimerType.FUTURE_SCHEDULE);
                    if (!gotErrorWhileRetrieving1OfScheduleTimers)
                        retrieveSpecificScheduleTimerEntries(PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_SCHEDULE);
                    if (!gotErrorWhileRetrieving1OfScheduleTimers)
                        retrieveSpecificScheduleTimerEntries(PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER);
                }

                if (gotErrorWhileRetrieving1OfScheduleTimers)
                    allScheduleTimerInfoPojos.clear();

                /*

                UPDATE doesListHaveOSCT and isAnyTimerRunning just before checking conflict so that it is checked against latest state

                doesListHaveOSCT and isAnyTimerRunning are already checked in BgTaskGetLatestStateViaWLAN

                for(PojoScheduleTimerInfo pojoScheduleTimerInfo:allScheduleTimerInfoPojos)
                    if(pojoScheduleTimerInfo.getScheduleTimerType()==PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
                    {
                        doesListHaveOSCT = isAnyTimerRunning = true;
                        break;
                    }

                if(!isAnyTimerRunning)
                    for(PojoScheduleTimerInfo pojoScheduleTimerInfo:allScheduleTimerInfoPojos)
                        if(pojoScheduleTimerInfo.isRunningNow())
                        {
                            isAnyTimerRunning = true;
                            break;
                        }*/

                for(int i=0;i<allScheduleTimerInfoPojos.size();i++)
                    Log.d(LOG_TAG, "addToListRefreshDisplay"+Integer.toString(i)+"~"+allScheduleTimerInfoPojos.get(i).toString());

                doneRetrievingLatestScheduleTimerList = !gotErrorWhileRetrieving1OfScheduleTimers;
            }catch(Exception e)
            {
                allScheduleTimerInfoPojos.clear();

                Log.e(LOG_TAG, e.getMessage() == null ? "null" : e.getMessage());
                e.printStackTrace();
                return e;
            }
            return null;
        }

        private void retrieveSpecificScheduleTimerEntries(PojoScheduleTimerInfo.ScheduleTimerType scheduleTimerTypeRetrieveFor) throws Exception
        {
            HttpUrl httpUrl = HttpUrl.parse("http://" + fixedSocketResolvedIPAddress + ":" + fixedSocketResolvedPort + "/read_file_line_by_line");

            if (httpUrl != null)
            {
                HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                if (scheduleTimerTypeRetrieveFor == null)
                {
                    Calendar calendar = Calendar.getInstance();
                    urlBuilder.addQueryParameter("fname", "_" + Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)) + Integer.toString(calendar.get(Calendar.MONTH) + 1) + Integer.toString(calendar.get(Calendar.YEAR)));  // calculate date here
                } else
                {
                    switch (scheduleTimerTypeRetrieveFor)
                    {
                        case ONE_SHOT_FUTURE_TIMER:
                            urlBuilder.addQueryParameter("fname", "OSFT");
                            break;

                        case FUTURE_SCHEDULE:
                            urlBuilder.addQueryParameter("fname", "FS");
                            break;

                        case RECURRING_SCHEDULE:
                            urlBuilder.addQueryParameter("fname", "RS");
                            break;

                        case RECURRING_TIMER:
                            urlBuilder.addQueryParameter("fname", "RT");
                            break;
                    }
                }

                Request request = new Request.Builder()
                        .url(urlBuilder.build().toString())
                        .build();

                if (request != null)
                {
                    while (true)
                    {
                        Response response = okHttpWLANClient.newCall(request).execute();

                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        ResponseBody responseBody = response.body();

                        if (responseBody != null)
                        {
                            responseBodyString = responseBody.string();

                            // if it is empty, we anyways show no schedule/timers entries found in tab 2
                            // error message will come when ESP is not able to read file from Flash Storage
                            if (responseBodyString != null)
                            {
                                responseBodyString = responseBodyString.trim();

                                Log.d(LOG_TAG, "retrieveSpecificScheduleTimerEntries: "+responseBodyString);

                                // if we have not got any error yet, then only check for "error" message in subsequent retrievals
                                if (!gotErrorWhileRetrieving1OfScheduleTimers)
                                    gotErrorWhileRetrieving1OfScheduleTimers = "error".equalsIgnoreCase(responseBodyString);

                                if (gotErrorWhileRetrieving1OfScheduleTimers)
                                    throw new Exception();
                                else if ("empty".equalsIgnoreCase(responseBodyString))
                                    break;
                                else
                                {
                                    if (responseBodyString.length() > 0)
                                    {
                                        PojoScheduleTimerInfo pojoScheduleTimerInfo = null;
                                        if (getOnlyTodaysScheduleTimers)
                                        {
                                            if (responseBodyString.startsWith("1OSFT=") || responseBodyString.startsWith("0OSFT="))
                                                pojoScheduleTimerInfo = tempPojoForParsing.parseCronMaskConfigString(responseBodyString.substring(6), PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER);
                                            else if (responseBodyString.startsWith("1FS=") || responseBodyString.startsWith("0FS="))
                                                pojoScheduleTimerInfo = tempPojoForParsing.parseCronMaskConfigString(responseBodyString.substring(4), PojoScheduleTimerInfo.ScheduleTimerType.FUTURE_SCHEDULE);
                                            else if (responseBodyString.startsWith("1RS=") || responseBodyString.startsWith("0RS="))
                                                pojoScheduleTimerInfo = tempPojoForParsing.parseCronMaskConfigString(responseBodyString.substring(4), PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_SCHEDULE);
                                            else if (responseBodyString.startsWith("1RT=") || responseBodyString.startsWith("0RT="))
                                                pojoScheduleTimerInfo = tempPojoForParsing.parseCronMaskConfigString(responseBodyString.substring(4), PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER);

                                            if (pojoScheduleTimerInfo != null)
                                                pojoScheduleTimerInfo.setScheduleSkippedForToday(responseBodyString.charAt(0) == '0');
                                        } else
                                            pojoScheduleTimerInfo = tempPojoForParsing.parseCronMaskConfigString(responseBodyString, scheduleTimerTypeRetrieveFor);
                                        allScheduleTimerInfoPojos.add(pojoScheduleTimerInfo);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Exception exceptionOccurred)
        {
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (doneRetrievingLatestScheduleTimerList)
                    new BgTaskGetLatestStateViaWLAN(true, false, false, false, null, false, false).execute();
                else
                {
                    if (getOnlyTodaysScheduleTimers)
                    {
                        if (fragmentScheduleTimers != null)
                        {
                            fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(null);
                            fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimers.setChecked(false);
                            fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimersListener);
                        }
                        showErrorRedToast("Couldn't get today's Schedule/Timer list from device. Please try again");

                        new BgTaskGetLatestScheduleTimerInfo(false).execute();
                    }else
                    {
                        resetStateMaintainerModel();
                        showErrorRedToast("Couldn't get latest Schedule/Timer list from device. Please try again");
                    }
                }

                if(fragmentScheduleTimers!=null)
                    fragmentScheduleTimers.refreshViews();
            }else
            {
                exceptionOccurred.printStackTrace();
                if (getOnlyTodaysScheduleTimers)
                {
                    if (fragmentScheduleTimers != null)
                    {
                        fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(null);
                        fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimers.setChecked(false);
                        fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(fragmentScheduleTimers.chkBoxShowOnlyTodaysScheduleTimersListener);
                    }
                    showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));

                    new BgTaskGetLatestScheduleTimerInfo(false).execute();
                }else
                {
                    resetStateMaintainerModel();
                    showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
                }
            }
        }
    }

    private class BgTaskSaveNewOSCTEntryToDevice extends AsyncTask<PojoScheduleTimerInfo, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean saveNewOSCTEntryToDeviceSuccessIndicator = false;
        private String responseBodyString;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Starting new One Shot Current Timer", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(PojoScheduleTimerInfo... params)
        {
            try
            {
                PojoScheduleTimerInfo pojoOSCT = params[0];

                HttpUrl httpUrl = HttpUrl.parse("http://"+fixedSocketResolvedIPAddress+":"+fixedSocketResolvedPort+"/set_new_osct");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                    urlBuilder.addQueryParameter("cron_mask_config_string", convertStringToHexString(pojoOSCT.toCronMaskConfigString()));

                    Request request = new Request.Builder()
                            .url(urlBuilder.build().toString())
                            .build();

                    if (request != null)
                    {
                        Response response = okHttpWLANClient.newCall(request).execute();

                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        ResponseBody responseBody = response.body();

                        if (responseBody != null)
                        {
                            responseBodyString = responseBody.string();
                            if (responseBodyString != null)
                                responseBodyString = responseBodyString.trim();
                            saveNewOSCTEntryToDeviceSuccessIndicator = "success".equalsIgnoreCase(responseBodyString);
                            if (saveNewOSCTEntryToDeviceSuccessIndicator)
                                allScheduleTimerInfoPojos.add(pojoOSCT);
                        }
                    }
                }
            } catch (Exception e)
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
            if (asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if (exceptionOccurred == null)
            {
                if (saveNewOSCTEntryToDeviceSuccessIndicator)
                {
                    showSuccessGreenToast("Successfully saved new entry");

                    if (fragmentScheduleTimers != null)
                        fragmentScheduleTimers.refreshViews();

                    new BgTaskGetLatestStateViaWLAN(true, false, false, false, null, false, false).execute();
                }else
                {
                    if ("error_another_timer_running".equalsIgnoreCase(responseBodyString))
                        showInfoGreyToast(getString(R.string.another_timer_already_running));
                    else if ("error_no_rtc".equalsIgnoreCase(responseBodyString))
                        showInfoGreyToast(getString(R.string.time_not_synced_on_socket));
                    else
                        showErrorRedToast("Couldn't start One Shot Current Timer. Please try again");
                }
            } else
            {
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskSaveNewEntryToDevice extends AsyncTask<PojoScheduleTimerInfo, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean isScheduleTimerEntryScheduledForToday = false;
        private String responseBodyString;

        BgTaskSaveNewEntryToDevice(boolean isScheduleTimerEntryScheduledForToday)
        {
            this.isScheduleTimerEntryScheduledForToday = isScheduleTimerEntryScheduledForToday;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Saving new entry to device", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(PojoScheduleTimerInfo... params)
        {
            try
            {
                PojoScheduleTimerInfo pojoNewScheduleTimerEntry = params[0];

                HttpUrl httpUrl = HttpUrl.parse("http://"+fixedSocketResolvedIPAddress+":"+fixedSocketResolvedPort+"/save_update_enabled_remove_schedule_timer_or_update_run_skip_for_today");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                    urlBuilder.addQueryParameter("call_type", "save_new_schedule_timer");
                    switch (pojoNewScheduleTimerEntry.getScheduleTimerType())
                    {
                        case ONE_SHOT_FUTURE_TIMER:
                            urlBuilder.addQueryParameter("schedule_timer_filename", "OSFT");
                            break;

                        case FUTURE_SCHEDULE:
                            urlBuilder.addQueryParameter("schedule_timer_filename", "FS");
                            break;

                        case RECURRING_SCHEDULE:
                            urlBuilder.addQueryParameter("schedule_timer_filename", "RS");
                            break;

                        case RECURRING_TIMER:
                            urlBuilder.addQueryParameter("schedule_timer_filename", "RT");
                            break;
                    }
                    urlBuilder.addQueryParameter("cron_mask_config_string", convertStringToHexString(pojoNewScheduleTimerEntry.toCronMaskConfigString()));
                    urlBuilder.addQueryParameter("truncate_before_update", "0");
                    urlBuilder.addQueryParameter("last_run_skip_update_or_todays_entry_saved_updated_removed", isScheduleTimerEntryScheduledForToday ? "1" : "0");
                    Calendar calendar = Calendar.getInstance();
                    urlBuilder.addQueryParameter("todays_schedule_timers", "_" + Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)) + Integer.toString(calendar.get(Calendar.MONTH) + 1) + Integer.toString(calendar.get(Calendar.YEAR)));

                    Request request = new Request.Builder()
                            .url(urlBuilder.build().toString())
                            .build();

                    if (request != null)
                    {
                        Response response = okHttpWLANClient.newCall(request).execute();

                        if (!response.isSuccessful())
                            throw new IOException("Unexpected code " + response);

                        ResponseBody responseBody = response.body();

                        if (responseBody != null)
                        {
                            responseBodyString = responseBody.string();
                            if (responseBodyString != null)
                                responseBodyString = responseBodyString.trim();
                            if ("success".equalsIgnoreCase(responseBodyString))
                                allScheduleTimerInfoPojos.add(pojoNewScheduleTimerEntry);
                        }
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
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if("success".equalsIgnoreCase(responseBodyString))
                {
                    showSuccessGreenToast("Successfully saved new entry");

                    if (isScheduleTimerEntryScheduledForToday)
                    {
                        Log.d(LOG_TAG, "Cron rescheduling will happen in 5 seconds in Socket");
                        // resetStateMaintainerModel();
                    }else
                    {
                        // Ideally should call below stuff only but since there is an memory error of already freed causing reboot while reading and scheduling cron entries, we are doing node.restart and disconnecting from here if the schedule/timer entry is for today
                        if (fragmentScheduleTimers != null)
                            fragmentScheduleTimers.refreshViews();

                        new BgTaskGetLatestStateViaWLAN(true, false, false, false, null, false, false).execute();
                    }
                }else
                {
                    if ("error_no_rtc".equalsIgnoreCase(responseBodyString))
                        showInfoGreyToast(getString(R.string.time_not_synced_on_socket));
                    else if ("error_another_timer_running".equalsIgnoreCase(responseBodyString))
                        showInfoGreyToast(getString(R.string.another_timer_already_running));
                    else if ("allowed_only_when_appliance_off".equalsIgnoreCase(responseBodyString))
                        showInfoGreyToast(getString(R.string.new_schedule_timer_entry_only_when_appliance_off));
                    else
                        showErrorRedToast("Couldn't save new entry to device. Please try again");
                }
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskGetLatestStateViaInternet extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean hasActiveInternetConnection = false;
        private String getSocketsPerUserStatusResponseBodyString = "";
        private List<String> internetModeDomainNameForAppAndSocket;
        private boolean call_BgTaskStateChangeOverInternet_once_done = false, desiredState = false;
        private boolean showProgressDialog = false;

        BgTaskGetLatestStateViaInternet(boolean showProgressDialog, boolean call_BgTaskStateChangeOverInternet_once_done, boolean desiredState)
        {
            this.showProgressDialog = showProgressDialog;
            this.call_BgTaskStateChangeOverInternet_once_done = call_BgTaskStateChangeOverInternet_once_done;
            this.desiredState = desiredState;
        }

        @Override
        protected void onPreExecute()
        {
            if (showProgressDialog)
            {
                asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Getting latest status", "Please wait");
                asyncTaskCustomProgressDialog.show();
            }
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                hasActiveInternetConnection = hasActiveInternetConnection();

                if (hasActiveInternetConnection)
                {
                    internetModeDomainNameForAppAndSocket = retrieveInternetModeDomainName(false);

                    if (internetModeDomainNameForAppAndSocket != null && !"offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)) && !"please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                    {
                        if (internetModeDomainNameForAppAndSocket.get(1).equals(currentlySelectedFixedSocket.getInternetModeDomainNameForSocket()))
                        {
                            HttpUrl httpUrl = HttpUrl.parse(internetModeDomainNameForAppAndSocket.get(0) + "/app/api/v" + currentlySelectedFixedSocket.getSocketSoftwareVersion() + ".0/sockets_per_user_status");

                            if (httpUrl != null && googleSignInAccount.getIdToken() != null && googleSignInAccount.getEmail() != null && currentlySelectedFixedSocket != null)
                            {
                                HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                                RequestBody requestBody = new MultipartBody.Builder()
                                        .setType(MultipartBody.FORM)
                                        .addFormDataPart("email", currentlySelectedFixedSocket.getInternetModeConfiguredBy())
                                        .addFormDataPart("socket_name", currentlySelectedFixedSocket.getMDNSHostName())
                                        .addFormDataPart("external_wifi_ssid", currentlySelectedFixedSocket.getExternalWiFiSSID())
                                        .addFormDataPart("socket_software_version", Integer.toString(currentlySelectedFixedSocket.getSocketSoftwareVersion()))
                                        .addFormDataPart("device_source", "app")
                                        .addFormDataPart("google_oauth_id_token", googleSignInAccount.getIdToken())
                                        .addFormDataPart("status_requester_email", googleSignInAccount.getEmail())
                                        .build();

                                Request request = new Request.Builder()
                                        .url(urlBuilder.build().toString())
                                        .method("POST", RequestBody.create(null, new byte[0]))
                                        .post(requestBody)
                                        .build();

                                if (request != null)
                                {
                                    Response response = okHttpInternetClient.newCall(request).execute();

                                    if (!response.isSuccessful())
                                        throw new IOException("Unexpected code " + response);

                                    ResponseBody responseBody = response.body();

                                    if (responseBody != null)
                                    {
                                        getSocketsPerUserStatusResponseBodyString = responseBody.string();
                                        if (getSocketsPerUserStatusResponseBodyString != null)
                                            getSocketsPerUserStatusResponseBodyString = getSocketsPerUserStatusResponseBodyString.trim();
                                    }
                                }
                            }
                        }else
                        {
                            internetModeDomainNameForAppAndSocket = new ArrayList<>();
                            internetModeDomainNameForAppAndSocket.add("please_reconfigure_internet_mode");
                        }
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
                if (hasActiveInternetConnection)
                {
                    if (getSocketsPerUserStatusResponseBodyString != null && getSocketsPerUserStatusResponseBodyString.length() > 0 && !"error".equalsIgnoreCase(getSocketsPerUserStatusResponseBodyString) && !"oauth_validation_failed".equalsIgnoreCase(getSocketsPerUserStatusResponseBodyString))
                    {
                        String[] responseBodyStringComponents = getSocketsPerUserStatusResponseBodyString.split("~");
                        for(int i=0;i<responseBodyStringComponents.length;i++)
                            responseBodyStringComponents[i] = responseBodyStringComponents[i].trim();

                        try
                        {
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                            registerReceiver(wiFiInternetDisConnectivityEventsBroadcastReceiver, intentFilter);
                        } catch (IllegalArgumentException iae)
                        {
                            // ignore the exception of already registered broadcast receiver
                        }

                        if (responseBodyStringComponents[0].charAt(0) == '0')
                            currentState = States.OFF_WITH_NO_TIMER;
                        else if (responseBodyStringComponents[0].charAt(0) == '1')
                            currentState = States.ON_WITH_NO_TIMER;
                        else if (responseBodyStringComponents[0].charAt(0) == '2')
                            currentState = States.ON_WITH_TIMER;
                        else if (responseBodyStringComponents[0].charAt(0) == '3')
                            currentState = States.OFF_WITH_TIMER;

                        currentlySelectedFixedSocket.setTimezoneDetails(responseBodyStringComponents[1]);
                        runningTimerType = responseBodyStringComponents[2];
                        runningTimerCronMaskConfigString = responseBodyStringComponents[3];
                        runningTimerSecsLeft = "None".equalsIgnoreCase(responseBodyStringComponents[4]) ? 0 : Long.valueOf(responseBodyStringComponents[4]);
                        lastStateUpdatedTimestampMillisecs = convertSecsToMilliSecs(Long.valueOf(responseBodyStringComponents[5]));
                        long differenceBetweenSysTimeAndLastUpdateDateTime = convertMilliSecsToSecs(System.currentTimeMillis() - lastStateUpdatedTimestampMillisecs);
                        runningTimerSecsLeft = runningTimerSecsLeft - (differenceBetweenSysTimeAndLastUpdateDateTime > 0 ? differenceBetweenSysTimeAndLastUpdateDateTime : 0);
                        lastUpdatedByEmailOrSocketForInternetMode = "socket".equalsIgnoreCase(responseBodyStringComponents[7]) ? "socket" : responseBodyStringComponents[6];

                        if ("socket".equalsIgnoreCase(lastUpdatedByEmailOrSocketForInternetMode))
                            lastUpdateActionByAppInternetMode =  null;
                        else
                        {
                            lastUpdateActionByAppInternetMode = "None".equalsIgnoreCase(runningTimerType) ? "" : "Stop Timer and ";
                            if (currentState == States.OFF_WITH_NO_TIMER)
                                lastUpdateActionByAppInternetMode += "Turn OFF";
                            else if (currentState == States.ON_WITH_NO_TIMER)
                                lastUpdateActionByAppInternetMode += "Turn ON";
                        }

                        isAnyTimerRunning = (currentState == States.ON_WITH_TIMER || currentState == States.OFF_WITH_TIMER);
                        if (isAnyTimerRunning && runningTimerSecsLeft <= 0)   // timer was running but it already expired in socket but not yet updated in cloud by socket
                        {
                            isAnyTimerRunning = false;
                            if (currentState == States.ON_WITH_TIMER)
                                currentState = States.OFF_WITH_NO_TIMER;
                            if (currentState == States.OFF_WITH_TIMER)
                                currentState = States.ON_WITH_NO_TIMER;
                        }

                        if (isAnyTimerRunning)
                        {
                            stop_handlerGetLatestStateEveryNSeconds();

                            PojoScheduleTimerInfo tempPojoForParsing = new PojoScheduleTimerInfo();

                            if ("osct".equalsIgnoreCase(runningTimerType))
                            {
                                runningTimerPojoObject = tempPojoForParsing.parseCronMaskConfigString(runningTimerCronMaskConfigString, PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER);
                            }else if ("osft".equalsIgnoreCase(runningTimerType))
                            {
                                Log.d(LOG_TAG, "Cant get Running Timer Pojo reference for currently running OSFT in Internet Mode");

                                // wont go into this loop as Schedule/Timers are un available for Internet Mode
                                /*for (PojoScheduleTimerInfo pojoScheduleTimerInfo : allScheduleTimerInfoPojos)
                                    if (pojoScheduleTimerInfo.getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER && pojoScheduleTimerInfo.toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                    {
                                        pojoScheduleTimerInfo.setIsRunningNow(true);
                                        runningTimerPojoObject = pojoScheduleTimerInfo;
                                        break;
                                    }*/
                            }else if ("rt".equalsIgnoreCase(runningTimerType))
                            {
                                Log.d(LOG_TAG, "Cant get Running Timer Pojo reference for currently running RT in Internet Mode");

                                // wont go into this loop as Schedule/Timers are un available for Internet Mode
                                /*for (PojoScheduleTimerInfo pojoScheduleTimerInfo : allScheduleTimerInfoPojos)
                                    if (pojoScheduleTimerInfo.getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER && pojoScheduleTimerInfo.toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                    {
                                        pojoScheduleTimerInfo.setIsRunningNow(true);
                                        runningTimerPojoObject = pojoScheduleTimerInfo;
                                        break;
                                    }*/
                            }

                            // Update UI (Btn state change with countdown timer and also the list in tab2 and info in tab3)
                            if (fragmentStateChange != null)
                                fragmentStateChange.refreshViews(currentState, runningTimerType, runningTimerSecsLeft);

                            if (fragmentSocketConfiguration != null)
                                fragmentSocketConfiguration.refreshViews();

                            stop_countDownTimerForDisplay(null);

                            countDownTimerForDisplay = new CountDownTimer(runningTimerSecsLeft * 1000, 1000)
                            {
                                @Override
                                public void onTick(long millisUntilFinished)
                                {
                                    if (fragmentStateChange != null)
                                        fragmentStateChange.refreshViews(currentState, runningTimerType, runningTimerSecsLeft);

                                    runningTimerSecsLeft--;
                                }

                                @Override
                                public void onFinish()
                                {
                                    isAnyTimerRunning = false;

                                    currentState = runningTimerPojoObject.getAfterTimerExpiresState() ? States.ON_WITH_NO_TIMER : States.OFF_WITH_NO_TIMER;
                                    lastStateUpdatedTimestampMillisecs = System.currentTimeMillis();

                                    if (fragmentStateChange != null)
                                        fragmentStateChange.refreshViews(currentState, null, 0);

                                    if ("OSCT".equalsIgnoreCase(runningTimerType))
                                    {
                                        Log.d(LOG_TAG, "Doing nothing for currently running OSCT in Internet Mode");

                                        // wont go into this loop as Schedule/Timers are un available for Internet Mode
                                        /*for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                            if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER)
                                            {
                                                allScheduleTimerInfoPojos.remove(i);
                                                break;
                                            }*/
                                    }else if ("OSFT".equalsIgnoreCase(runningTimerType))
                                    {
                                        Log.d(LOG_TAG, "Doing nothing for currently running OSFT in Internet Mode");

                                        // wont go into this loop as Schedule/Timers are un available for Internet Mode
                                        // don't remove from list for Recurring Timers as it will be handled by removing past entries
                                        /*for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                            if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER && allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                            {
                                                allScheduleTimerInfoPojos.get(i).setIsRunningNow(false);
                                                break;
                                            }*/
                                    }else if ("RT".equalsIgnoreCase(runningTimerType))
                                    {
                                        Log.d(LOG_TAG, "Doing nothing for currently running RT in Internet Mode");

                                        // wont go into this loop as Schedule/Timers are un available for Internet Mode
                                        // don't remove from list for Recurring Timers as it will be handled by removing past entries
                                        /*for (int i=0;i<allScheduleTimerInfoPojos.size();i++)
                                            if (allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER && allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equalsIgnoreCase(runningTimerCronMaskConfigString))
                                            {
                                                allScheduleTimerInfoPojos.get(i).setIsRunningNow(false);
                                                break;
                                            }*/
                                    }

                                    // calling this once on timer expire so that control goes into below else and start_handlerGetLatestStateEveryNSeconds(5000); is called
                                    call_BgTaskGetLatestStateViaInternet_not_showing_progressDialog();
                                }
                            };
                            countDownTimerForDisplay.start();
                        }else
                        {
                            if (fragmentStateChange != null)
                                fragmentStateChange.refreshViews(currentState, null, 0);

                            if (fragmentSocketConfiguration != null)
                                fragmentSocketConfiguration.refreshViews();

                            // only start this when there is no timer running, because running timers will be handled above
                            // we put this because imagine a scenario where in user is connected to fixed socket and after say 10 secs future schedule changes state hence we need this
                            start_handlerGetLatestStateEveryNSeconds(5000);
                        }

                        if(showProgressDialog && asyncTaskCustomProgressDialog != null)
                            asyncTaskCustomProgressDialog.dismiss();

                        if (call_BgTaskStateChangeOverInternet_once_done)
                        {
                            // do a state change only if previous state change is complete
                            if ("socket".equalsIgnoreCase(SmartWiFiSocketActivity.lastUpdatedByEmailOrSocketForInternetMode))
                            {
                                /*if ((System.currentTimeMillis() - SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeLastUpdated()) > 1920000)   // make sure we wait at least 32 mins (2 tries for ESP) for Internet Mode state change to happen
                                    activityRefToCallMethods.call_BgTaskGetLatestStateViaInternet_then_BgTaskStateChangeOverInternet(desiredState);
                                else
                                    activityRefToCallMethods.showInfoGreyToast("Previous Internet Mode change is pending. Please wait for few more minutes");*/

                                if ((System.currentTimeMillis() - SmartWiFiSocketActivity.lastStateUpdatedTimestampMillisecs) > 1920000)        // last update from socket was more than 32 minutes ago which means 2 tries of socket update has not gone through
                                    showInfoGreyToast("Socket's last update was more than half an hour ago. Please wait.");
                                else
                                {
                                    if (fragmentStateChange != null)
                                        fragmentStateChange.call_BgTaskStateChangeOverInternet(desiredState);
                                }
                            }else
                                showInfoGreyToast("Last Update from "+SmartWiFiSocketActivity.lastUpdatedByEmailOrSocketForInternetMode +" is pending. Please wait");
                        }

                        saveConfiguredSockets();        // internet mode configured, socket software version and others was set so saved that into shared preferences
                    }else
                    {
                        if(showProgressDialog && asyncTaskCustomProgressDialog != null)
                            asyncTaskCustomProgressDialog.dismiss();
                        resetStateMaintainerModel();

                        if (internetModeDomainNameForAppAndSocket == null || "offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            showInfoGreyToast("Cloud Server is Offline for Maintenance");
                        else if ("please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            showInfoGreyToast("Please upgrade your app to latest version");
                        else if ("please_reconfigure_internet_mode".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            showInfoGreyToast("Please reconfigure Internet Mode");
                        else
                            showErrorRedToast("Couldn't get status via Internet. Please try again");
                    }
                }else
                {
                    if(showProgressDialog && asyncTaskCustomProgressDialog != null)
                        asyncTaskCustomProgressDialog.dismiss();
                    resetStateMaintainerModel();

                    showInfoGreyToast("Internet connection un-available");
                }
            }else
            {
                if(showProgressDialog && asyncTaskCustomProgressDialog != null)
                    asyncTaskCustomProgressDialog.dismiss();
                exceptionOccurred.printStackTrace();
                resetStateMaintainerModel();

                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    List<String> retrieveInternetModeDomainName(boolean calledFromInternetModeConfig) throws Exception
    {
        List<String> result = new ArrayList<>();
        if (currentlySelectedFixedSocket.getSocketSoftwareVersion() == 1)
        {
            Request request = new Request.Builder()
                    .url("https://pastebin.com/raw/LwpdxEAm")   // signed in with Google developer.unify@gmail.com, take paste which has domain name having https
                    .build();

            if (request != null)
            {
                Response response = SmartWiFiSocketActivity.okHttpInternetClient.newCall(request).execute();

                if (!response.isSuccessful())
                    throw new IOException("Unexpected code " + response);

                ResponseBody responseBody = response.body();

                if (responseBody != null)
                {
                    String responseBodyString = responseBody.string();
                    if (responseBodyString != null)
                        responseBodyString = responseBodyString.trim();
                    if ("offline".equalsIgnoreCase(responseBodyString))
                    {
                        result.add("offline");
                        return result;
                    }else if ("please_upgrade_app_version".equalsIgnoreCase(responseBodyString))
                    {
                        result.add("please_upgrade_app_version");
                        return result;
                    }else
                    {
                        if (responseBodyString != null)
                        {
                            String[] serverDomainNameForAppAndSocket = responseBodyString.split("\n");
                            result.add(EncryptorDecryptorServerDomainNameForAppAndSocket.decrypt("pW4dOmAINnAmE91!", "IVfORAESCBCyO91^", serverDomainNameForAppAndSocket[0]));
                            result.add(EncryptorDecryptorServerDomainNameForAppAndSocket.decrypt("Pw4DoMAiNnAmE92$", "IVFoRAESCBCYo92&", serverDomainNameForAppAndSocket[1]));
                            if (calledFromInternetModeConfig)
                            {
                                currentlySelectedFixedSocket.setInternetModeDomainNameForSocket(result.get(1));
                                saveConfiguredSockets();
                            }
                            return result;
                        }
                    }
                }
            }
        }else
        {
            result.add("please_upgrade_app_version");
            return result;
        }

        return null;
        // return "smart_wifi_socket_for_app.appspot.com";
    }

    private class BgTaskCheckHasInternetConnectionThenActivateInternetMode extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean showProgressDialog = false;
        private boolean hasActiveInternetConnection = false;

        BgTaskCheckHasInternetConnectionThenActivateInternetMode(boolean showProgressDialog)
        {
            this.showProgressDialog = showProgressDialog;
        }

        @Override
        protected void onPreExecute()
        {
            if (showProgressDialog)
            {
                asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Checking Internet Connectivity", "Please wait");
                asyncTaskCustomProgressDialog.show();
            }
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                hasActiveInternetConnection = hasActiveInternetConnection();
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
            if(showProgressDialog && asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (hasActiveInternetConnection)
                {
                    saveConfiguredSockets();

                    stop_handlerGetLatestStateEveryNSeconds();

                    handleNetworkConnectivityChange();
                }else
                {
                    showInfoGreyToast("Internet connection un-available");
                }
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskShowStorageDetails extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private String responseBodyString = null;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Retrieving Storage Details", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = null;
                if (currentlySelectedFixedSocket != null)
                    request = new Request.Builder()
                            .url("http://"+fixedSocketResolvedIPAddress+":"+fixedSocketResolvedPort+"/get_fsinfo")
                            .build();
                else if (currentlySelectedPortableSocket != null)
                    request = new Request.Builder()
                            .url("http://"+PORTABLE_SOCKET_SERVER_IP+":"+SERVICE_PORT+"/get_fsinfo")
                            .build();

                if (request != null)
                {
                    Response response = okHttpWLANClient.newCall(request).execute();

                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    ResponseBody responseBody = response.body();

                    if (responseBody != null)
                    {
                        responseBodyString = responseBody.string();
                        if (responseBodyString != null)
                            responseBodyString = responseBodyString.trim();
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
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (responseBodyString != null && responseBodyString.length() > 0)
                {
                    Log.d(LOG_TAG, "Storage Details: "+responseBodyString);
                    String[] responseBodyComponents = responseBodyString.split("~");
                    /*for(int i=0;i<responseBodyComponents.length;i++)
                        responseBodyComponents[i] = String.format(Locale.getDefault(), "%,d", Long.valueOf(responseBodyComponents[i]));*/

                    new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                            .setTitle(getString(R.string.menu_item_storage_details)+" for "+(currentlySelectedFixedSocket != null ? currentlySelectedFixedSocket.getMDNSHostName() : currentlySelectedPortableSocket))
                            .setCancelable(true)
                            .setMessage(String.format(getString(R.string.menu_item_storage_details_values), Formatter.formatFileSize(SmartWiFiSocketActivity.this, Long.parseLong(responseBodyComponents[1]) * 1024), Formatter.formatFileSize(SmartWiFiSocketActivity.this, Long.parseLong(responseBodyComponents[2]) * 1024)))
                            .setPositiveButton("OK", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i)
                                {
                                    dialogInterface.dismiss();
                                }
                            }).create().show();
                }else
                    showErrorRedToast("Couldn't retrieve storage details. Please try again");
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskFactoryResetSelectedSocket extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private String responseBodyString = null;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Factory Resetting Selected Socket", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = null;
                if (currentlySelectedFixedSocket != null)
                    request = new Request.Builder()
                            .url("http://"+fixedSocketResolvedIPAddress+":"+fixedSocketResolvedPort+"/factory_reset_redo_initial_setup")
                            .build();
                else if (currentlySelectedPortableSocket != null)
                    request = new Request.Builder()
                            .url("http://"+PORTABLE_SOCKET_SERVER_IP+":"+SERVICE_PORT+"/factory_reset_redo_initial_setup")
                            .build();

                if (request != null)
                {
                    Response response = okHttpWLANClient.newCall(request).execute();

                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    ResponseBody responseBody = response.body();

                    if (responseBody != null)
                    {
                        responseBodyString = responseBody.string();
                        if (responseBodyString != null)
                            responseBodyString = responseBodyString.trim();
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
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (responseBodyString != null && responseBodyString.length() > 0 && "success".equalsIgnoreCase(responseBodyString))
                {
                    if (currentlySelectedFixedSocket != null)
                    {
                        PerFixedSocketConfig forgottenConfiguredSocket = currentlySelectedFixedSocket;
                        for (int i = 0; i < configuredFixedSockets.size(); i++)
                            if (forgottenConfiguredSocket.getMDNSHostName().equals(configuredFixedSockets.get(i).getMDNSHostName()))
                            {
                                configuredFixedSockets.remove(i);
                                showSuccessGreenToast(forgottenConfiguredSocket.getMDNSHostName() + " Forgotten");
                                break;
                            }
                    }

                    if (currentlySelectedPortableSocket != null)
                    {
                        String forgottenConfiguredPortableSocket =  currentlySelectedPortableSocket;
                        if (configuredPortableSockets.remove(forgottenConfiguredPortableSocket))
                        {
                            removePreConfiguredSavedWiFiConfiguration(forgottenConfiguredPortableSocket, true);

                            // DEFAULT_SETUP_SSID is the default value because it is guaranteed that DEFAULT_SETUP_SSID will never be the name of a PortableSocket
                            if (sharedPreferences.getString("MobileChargingAutoTurnOffSocket", DEFAULT_SETUP_SSID).equals(forgottenConfiguredPortableSocket))
                                removeChargingAutoTurnOffFromSharedPreferences();
                        }
                    }

                    saveConfiguredSockets();
                    retrieveConfiguredSockets();

                    resetStateMaintainerModel();
                }else
                    showErrorRedToast("Couldn't factory reset selected Socket. Please try again");
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    /*private class BgTaskTestLocaltimeTransfer extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean gotAnyErrorWhileFileTransfer = false;

        private List<String> localtimeHexString512Chunks = new ArrayList<>();

        BgTaskTestLocaltimeTransfer(String localtimeHexString)
        {
            localtimeHexString512Chunks = splitEqually(localtimeHexString, 512);

            Log.d(LOG_TAG, localtimeHexString512Chunks.toString());
            Log.d(LOG_TAG, Integer.toString(localtimeHexString512Chunks.size()));
        }

        private List<String> splitEqually(String text, int size)
        {
            List<String> ret = new ArrayList<>((text.length() + size - 1) / size);
            for (int start = 0; start < text.length(); start += size)
                ret.add(text.substring(start, Math.min(text.length(), start + size)));
            return ret;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(SmartWiFiSocketActivity.this, "Testing tz32.lua and localtime transfers", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                if (localtimeHexString512Chunks != null && localtimeHexString512Chunks.size() > 0)
                {
                    HttpUrl httpUrl = HttpUrl.parse("http://192.168.10.1/store_file_onto_flash");

                    if (httpUrl != null)
                    {
                        HttpUrl.Builder urlBuilder;
                        Request request;
                        for (int i=0;i<localtimeHexString512Chunks.size();i++)
                        {
                            urlBuilder = httpUrl.newBuilder();
                            urlBuilder.addQueryParameter("store_as_filename", "localtime");

                            request = new Request.Builder()
                                    .url(urlBuilder.build().toString())
                                    .method("POST", RequestBody.create(null, new byte[0]))
                                    .post(RequestBody.create(MediaType.parse("text/plain; charset=ascii"), localtimeHexString512Chunks.get(i)))
                                    .build();

                            String responseBodyStringFor_localtime = null;
                            if (request != null)
                            {
                                Response response = okHttpWLANClient.newCall(request).execute();

                                if (!response.isSuccessful())
                                    throw new IOException("Unexpected code " + response);

                                ResponseBody responseBody = response.body();

                                if (responseBody != null)
                                {
                                    responseBodyStringFor_localtime = responseBody.string();
                                    if (responseBodyStringFor_localtime != null)
                                        responseBodyStringFor_localtime = responseBodyStringFor_localtime.trim();
                                }
                            }

                            gotAnyErrorWhileFileTransfer = !"success".equalsIgnoreCase(responseBodyStringFor_localtime);
                            if (gotAnyErrorWhileFileTransfer)
                            {
                                Log.d(LOG_TAG, "for index: "+i);
                                Log.d(LOG_TAG, "responseBodyStringFor_localtime is null? " + (responseBodyStringFor_localtime == null));

                                if (responseBodyStringFor_localtime != null)
                                    Log.d(LOG_TAG, "responseBodyStringFor_localtime: " + responseBodyStringFor_localtime);
                                break;
                            }
                        }
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
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (gotAnyErrorWhileFileTransfer)
                    showErrorRedToast("Could not send localtime file");
                else
                    showSuccessGreenToast("Successfully sent localtime file");
            }else
            {
                exceptionOccurred.printStackTrace();
                showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }*/

    void showSuccessGreenToast(String message)
    {
        View layout = View.inflate(SmartWiFiSocketActivity.this, R.layout.toast_sucess_green, (ViewGroup) findViewById(R.id.toast_layout_root));

        TextView text = layout.findViewById(R.id.txtViewCustomInfoToast);
        text.setText(message);

        Toast toast = new Toast(SmartWiFiSocketActivity.this);
        toast.setGravity(Gravity.BOTTOM, 0, 255);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }

    void showInfoGreyToast(String message)
    {
        View layout = View.inflate(SmartWiFiSocketActivity.this, R.layout.toast_info, (ViewGroup) findViewById(R.id.toast_layout_root));

        TextView text = layout.findViewById(R.id.txtViewCustomInfoToast);
        text.setText(message);

        Toast toast = new Toast(SmartWiFiSocketActivity.this);
        toast.setGravity(Gravity.BOTTOM, 0, 255);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    void showErrorRedToast(String message)
    {
        View layout = View.inflate(SmartWiFiSocketActivity.this, R.layout.toast_error_red, (ViewGroup) findViewById(R.id.toast_layout_root));

        TextView text = layout.findViewById(R.id.txtViewCustomInfoToast);
        text.setText(message);

        Toast toast = new Toast(SmartWiFiSocketActivity.this);
        toast.setGravity(Gravity.BOTTOM, 0, 255);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    private class CustomProgressDialog
    {
        AlertDialog progressDialog;

        CustomProgressDialog(String title, String message)
        {
            View progressDialogLayout = View.inflate(SmartWiFiSocketActivity.this, R.layout.dialog_custom_progress_dialog, null);
            TextView txtViewProgressBar = progressDialogLayout.findViewById(R.id.txtViewProgressBar);
            txtViewProgressBar.setText(message);

            AlertDialog.Builder progressDialogBuilder = new AlertDialog.Builder(SmartWiFiSocketActivity.this)
                    .setTitle(title)
                    .setCancelable(true)
                    .setView(progressDialogLayout);

            progressDialog = progressDialogBuilder.create();
        }

        void setOnCancelListener(DialogInterface.OnCancelListener onCancelListener)
        {
            progressDialog.setOnCancelListener(onCancelListener);
        }

        void show()
        {
            progressDialog.show();
        }

        void dismiss()
        {
            progressDialog.dismiss();
        }
    }
}