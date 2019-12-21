package in.co.unifytech.socket;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;
import java.util.List;

import in.co.unifytech.R;
import in.co.unifytech.socket.utils.AsyncTaskCustomProgressDialog;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SocketConfigurationFragment extends Fragment
{
    private Activity hostActivityContext;
    private SmartWiFiSocketActivity activityRefToCallMethods;
    private View fragmentRootView;

    private SignInButton btnGoogleSignIn;
    private Button btnAfterGoogleSignIn, btnInternetModeConfig, btnRemoveInternetModeConfig;
    private final int REQUEST_CODE_GOOGLE_SIGN_IN = 1234;

    private final String LOG_TAG = SocketConfigurationFragment.class.getSimpleName();

    public SocketConfigurationFragment()
    {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof Activity)
            hostActivityContext = (SmartWiFiSocketActivity) context;

        activityRefToCallMethods = (SmartWiFiSocketActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        fragmentRootView = inflater.inflate(R.layout.fragment_config, container, false);

        getViews();
        initViews();

        btnGoogleSignIn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                new BgTaskStartGoogleSignInIfConnectedToInternet().execute();
            }
        });

        btnAfterGoogleSignIn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {

                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null && SmartWiFiSocketActivity.isInternetModeActivated)
                    activityRefToCallMethods.showInfoGreyToast("Cannot Sign Out when Socket is in Internet Mode");
                else
                {
                    new AlertDialog.Builder(hostActivityContext)
                            .setTitle("Confirm Sign Out?")
                            .setMessage("Are you sure you want to sign out of " + SmartWiFiSocketActivity.googleSignInAccount.getEmail())
                            .setCancelable(true)
                            .setPositiveButton("Sign Out", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    dialog.dismiss();
                                    Auth.GoogleSignInApi.signOut(activityRefToCallMethods.getGoogleApiClient()).setResultCallback(new ResultCallback<Status>()
                                    {
                                        @Override
                                        public void onResult(@NonNull Status status)
                                        {
                                            if (status.isSuccess())
                                            {
                                                SmartWiFiSocketActivity.googleSignInAccount = null;
                                                refreshViews();
                                            } else
                                                activityRefToCallMethods.showErrorRedToast("Error signing out. Please try again");
                                        }
                                    });
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
            }
        });

        // reference: https://developers.google.com/identity/sign-in/android/sign-in?configured=true
        btnInternetModeConfig.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                    {
                        if (SmartWiFiSocketActivity.isInternetModeActivated)
                            activityRefToCallMethods.showInfoGreyToast(String.format(getString(R.string.btn_internet_mode_configured), SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeConfiguredBy()));
                        else
                        {
                            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket.isInternetModeConfigured())
                                new BgTaskInternetModeReConfigure(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeDomainNameForSocket()).execute();
                            else
                            {
                                if (SmartWiFiSocketActivity.googleSignInAccount == null)
                                    activityRefToCallMethods.showInfoGreyToast("Google Sign In required. Please check Internet Connection");
                                else
                                {
                                    // call to GetLatest state and then to another Async Task to register into tables: users, sockets, sockets_per_user (state taken from previous AsyncTask call)
                                    activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN_then_BgTaskInternetModeConfiguration(false);
                                }
                            }
                        }
                    }else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
                        activityRefToCallMethods.showInfoGreyToast(getString(R.string.btn_internet_mode_unavailable_for_portable_socket));
                }
            }
        });

        btnRemoveInternetModeConfig.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                    {
                        if (SmartWiFiSocketActivity.isInternetModeActivated)
                            activityRefToCallMethods.showInfoGreyToast("Internet Mode Configuration can be removed only when connected over WiFi");
                        else
                        {
                            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket.isInternetModeConfigured())
                            {
                                new AlertDialog.Builder(hostActivityContext)
                                        .setTitle("Confirm Internet Mode Config Removal?")
                                        .setMessage("Are you sure you want to remove Internet Mode configuration?")
                                        .setCancelable(true)
                                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                                dialog.dismiss();
                                                new BgTaskRemoveInternetModeConfiguration().execute();
                                            }
                                        })
                                        .setNegativeButton("No", new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                                dialog.dismiss();
                                            }
                                        }).create().show();
                            } else
                                activityRefToCallMethods.showInfoGreyToast("Internet Mode not configured yet");
                        }
                    }else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
                        activityRefToCallMethods.showInfoGreyToast(getString(R.string.btn_internet_mode_unavailable_for_portable_socket));
                }
            }
        });

        return fragmentRootView;
    }

    public void call_BgTaskInternetModeConfiguration(boolean internetModeReconfiguring)
    {
        new BgTaskInternetModeConfiguration(internetModeReconfiguring).execute();
    }

    private class BgTaskInternetModeReConfigure extends AsyncTask<Void, Void, Exception>
    {
        private boolean hasActiveInternetConnection = false;
        private String alreadyConfiguredInternetModeDomainNameForSocket = "";
        private List<String> internetModeDomainNameForAppAndSocket;
        private boolean socketNameFromCloudIsSameAsAlreadyConfigured = false;

        BgTaskInternetModeReConfigure(String alreadyConfiguredInternetModeDomainNameForSocket)
        {
            this.alreadyConfiguredInternetModeDomainNameForSocket = alreadyConfiguredInternetModeDomainNameForSocket;
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                hasActiveInternetConnection = activityRefToCallMethods.hasActiveInternetConnection();

                if (hasActiveInternetConnection)
                {
                    internetModeDomainNameForAppAndSocket = activityRefToCallMethods.retrieveInternetModeDomainName(true);
                    if (internetModeDomainNameForAppAndSocket != null && !"offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)) && !"please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                        socketNameFromCloudIsSameAsAlreadyConfigured = internetModeDomainNameForAppAndSocket.get(1).equals(alreadyConfiguredInternetModeDomainNameForSocket);
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
            if(exceptionOccurred==null)
            {
                if (hasActiveInternetConnection)
                {
                    if (internetModeDomainNameForAppAndSocket == null || "offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                        activityRefToCallMethods.showInfoGreyToast("Cloud Server is Offline for Maintenance");
                    else if ("please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                        activityRefToCallMethods.showInfoGreyToast("Please upgrade your app to latest version");
                    else
                    {
                        if (socketNameFromCloudIsSameAsAlreadyConfigured)
                            activityRefToCallMethods.showInfoGreyToast(String.format(getString(R.string.btn_internet_mode_configured), SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeConfiguredBy()));
                        else
                        {
                            new AlertDialog.Builder(hostActivityContext)
                                    .setTitle("Confirm Re-configure?")
                                    .setMessage(String.format(getString(R.string.btn_internet_mode_configured), SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeConfiguredBy()) + System.getProperty("line.separator") + System.getProperty("line.separator") + "Are you sure you want to re-configure?")
                                    .setCancelable(true)
                                    .setPositiveButton("Re-Configure", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {
                                            dialog.dismiss();
                                            if (SmartWiFiSocketActivity.googleSignInAccount == null)
                                                activityRefToCallMethods.showInfoGreyToast("Google Sign In required. Please check Internet Connection");
                                            else
                                            {
                                                // call to GetLatest state and then to another Async Task to register into tables: users, sockets, sockets_per_user (state taken from previous AsyncTask call)
                                                activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN_then_BgTaskInternetModeConfiguration(true);
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
                    }
                }else
                    activityRefToCallMethods.showInfoGreyToast("Internet connection un-available");
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case REQUEST_CODE_GOOGLE_SIGN_IN:
                GoogleSignInResult googleSignInResult = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                if (googleSignInResult.isSuccess())
                {
                    SmartWiFiSocketActivity.googleSignInAccount = googleSignInResult.getSignInAccount();
                    SmartWiFiSocketActivity.calledGoogleSilentSignInOnceForThisSession = true;
                    refreshViews();
                }else
                    activityRefToCallMethods.showErrorRedToast("Error signing into Google. Please try again.");
                break;
        }
    }

    private void getViews()
    {
        btnGoogleSignIn = fragmentRootView.findViewById(R.id.btnGoogleSignIn);
        btnAfterGoogleSignIn = fragmentRootView.findViewById(R.id.btnAfterGoogleSignIn);
        btnInternetModeConfig = fragmentRootView.findViewById(R.id.btnInternetModeConfig);
        btnRemoveInternetModeConfig = fragmentRootView.findViewById(R.id.btnRemoveInternetModeConfig);
    }

    private void initViews()
    {
        btnGoogleSignIn.setSize(SignInButton.SIZE_WIDE);
    }

    void refreshViews()
    {
        if (SmartWiFiSocketActivity.googleSignInAccount == null)
        {
            btnGoogleSignIn.setVisibility(View.VISIBLE);
            btnAfterGoogleSignIn.setEnabled(true);

            btnAfterGoogleSignIn.setVisibility(View.GONE);
            btnAfterGoogleSignIn.setEnabled(false);
        }else
        {
            btnGoogleSignIn.setVisibility(View.GONE);
            btnGoogleSignIn.setEnabled(false);

            btnAfterGoogleSignIn.setVisibility(View.VISIBLE);
            btnAfterGoogleSignIn.setEnabled(true);

            btnAfterGoogleSignIn.setText(String.format(getString(R.string.btn_after_google_sign_in), SmartWiFiSocketActivity.googleSignInAccount.getEmail()));
            Log.d(LOG_TAG, "IdToken~"+SmartWiFiSocketActivity.googleSignInAccount.getIdToken());
        }

        if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
        {
            btnInternetModeConfig.setVisibility(View.GONE);
            btnRemoveInternetModeConfig.setVisibility(View.GONE);
        }else
        {
            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
            {
                btnInternetModeConfig.setVisibility(View.VISIBLE);

                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket.isInternetModeConfigured())
                {
                    btnInternetModeConfig.setText(String.format(getString(R.string.btn_internet_mode_configured), SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeConfiguredBy()));
                    btnRemoveInternetModeConfig.setVisibility(View.VISIBLE);
                }else
                {
                    btnInternetModeConfig.setText(getString(R.string.btn_internet_mode_not_configured));
                    btnRemoveInternetModeConfig.setVisibility(View.GONE);
                }
            }else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
            {
                btnInternetModeConfig.setVisibility(View.GONE);
                btnRemoveInternetModeConfig.setVisibility(View.GONE);
            }
        }
    }

    private class BgTaskStartGoogleSignInIfConnectedToInternet extends AsyncTask<Void, Void, Boolean>
    {
        @Override
        protected Boolean doInBackground(Void... params)
        {
            return activityRefToCallMethods.hasActiveInternetConnection() && !SmartWiFiSocketActivity.calledGoogleSilentSignInOnceForThisSession;
        }

        @Override
        protected void onPostExecute(Boolean canStartGoogleSignInActivity)
        {
            if(canStartGoogleSignInActivity)
                startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(activityRefToCallMethods.getGoogleApiClient()), REQUEST_CODE_GOOGLE_SIGN_IN);
            else
                activityRefToCallMethods.showInfoGreyToast("Internet connection un-available");
        }
    }

    private class BgTaskInternetModeConfiguration extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog customProgressDialog = null;
        private boolean hasActiveInternetConnection = false;
        private String socketsPerUserEntryUpdateResponseString = "", internetModeConfiguredInSocket = "";
        private List<String> internetModeDomainNameForAppAndSocket;
        private boolean internetModeReconfiguring = false;

        BgTaskInternetModeConfiguration(boolean internetModeReconfiguring)
        {
            this.internetModeReconfiguring = internetModeReconfiguring;
        }

        @Override
        protected void onPreExecute()
        {
            customProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, (internetModeReconfiguring ? "Re-" : "") + "Configuring Internet Mode", "This may take a while. Please wait!");
            customProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                hasActiveInternetConnection = activityRefToCallMethods.hasActiveInternetConnection();

                if (hasActiveInternetConnection)
                {
                    internetModeDomainNameForAppAndSocket = activityRefToCallMethods.retrieveInternetModeDomainName(true);

                    if (internetModeDomainNameForAppAndSocket != null && !"offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)) && !"please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                    {
                        socketsPerUserEntryUpdateResponseString = socketsPerUserEntryUpdate(true);

                        if ("already_present".equalsIgnoreCase(socketsPerUserEntryUpdateResponseString))
                            socketsPerUserEntryUpdateResponseString = socketsPerUserEntryUpdate(false);

                        if ("success".equalsIgnoreCase(socketsPerUserEntryUpdateResponseString))
                            internetModeConfiguredInSocket = setInternetModeConfiguredByInSocket();
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

        // having PUT in cases when user factory reset and forgets a socket but then later on configures the same name again (as an admin, you have to periodically remove such sockets from the system say older than 1/2 years)
        private String socketsPerUserEntryUpdate(boolean viaPost) throws Exception
        {
            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion() == 1)
            {
                HttpUrl httpUrl = HttpUrl.parse(internetModeDomainNameForAppAndSocket.get(0) + "/app/api/v1.0/sockets_per_user");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                    String email = SmartWiFiSocketActivity.googleSignInAccount.getEmail();
                    String socketName = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getMDNSHostName();
                    String externalWiFiSSID = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getExternalWiFiSSID();
                    int socketSoftwareVersion = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion();
                    String currentDesiredState = getReadableStateForFixedSocket(SmartWiFiSocketActivity.currentState);
                    String timezoneDetails = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getTimezoneDetails();
                    String runningTimerType = SmartWiFiSocketActivity.runningTimerType;
                    String runningTimerCronMaskConfigString = SmartWiFiSocketActivity.runningTimerCronMaskConfigString;
                    long runningTimerSecsLeft = SmartWiFiSocketActivity.runningTimerSecsLeft;
                    String lastUpdatedDateTime = Long.toString(convertMillisecsToSecs(System.currentTimeMillis()));
                    String lastUpdatedBy = SmartWiFiSocketActivity.googleSignInAccount.getEmail();
                    String lastUpdatedByDeviceSource = "app";
                    String googleOAuthIdToken = SmartWiFiSocketActivity.googleSignInAccount.getIdToken();

                    if (email != null && socketName != null && externalWiFiSSID != null && socketSoftwareVersion > 0 && currentDesiredState != null && timezoneDetails != null && lastUpdatedDateTime != null && lastUpdatedBy != null && googleOAuthIdToken != null)
                    {
                        RequestBody requestBody;

                        if (SmartWiFiSocketActivity.isAnyTimerRunning)
                            requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart("email", email)
                                    .addFormDataPart("socket_name", socketName)
                                    .addFormDataPart("external_wifi_ssid", externalWiFiSSID)
                                    .addFormDataPart("socket_software_version", Integer.toString(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion()))
                                    .addFormDataPart("current_desired_state", currentDesiredState)
                                    .addFormDataPart("timezone_details", timezoneDetails)
                                    .addFormDataPart("running_timer_type", runningTimerType)
                                    .addFormDataPart("running_timer_cron_mask_config_string", runningTimerCronMaskConfigString)
                                    .addFormDataPart("running_timer_secs_left", Long.toString(runningTimerSecsLeft))
                                    .addFormDataPart("last_updated_datetime", lastUpdatedDateTime)
                                    .addFormDataPart("last_updated_by", lastUpdatedBy)
                                    .addFormDataPart("last_updated_by_device_source", lastUpdatedByDeviceSource)
                                    .addFormDataPart("google_oauth_id_token", googleOAuthIdToken)
                                    .build();
                        else
                            requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart("email", email)
                                    .addFormDataPart("socket_name", socketName)
                                    .addFormDataPart("external_wifi_ssid", externalWiFiSSID)
                                    .addFormDataPart("socket_software_version", Integer.toString(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion()))
                                    .addFormDataPart("current_desired_state", currentDesiredState)
                                    .addFormDataPart("timezone_details", timezoneDetails)
                                    .addFormDataPart("last_updated_datetime", lastUpdatedDateTime)
                                    .addFormDataPart("last_updated_by", lastUpdatedBy)
                                    .addFormDataPart("last_updated_by_device_source", lastUpdatedByDeviceSource)
                                    .addFormDataPart("google_oauth_id_token", googleOAuthIdToken)
                                    .build();

                        Request request;
                        if (viaPost)
                            request = new Request.Builder()
                                    .url(urlBuilder.build().toString())
                                    .method("POST", RequestBody.create(null, new byte[0]))
                                    .post(requestBody)
                                    .build();
                        else
                            request = new Request.Builder()
                                    .url(urlBuilder.build().toString())
                                    .method("PUT", RequestBody.create(null, new byte[0]))
                                    .put(requestBody)
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
                                return responseBodyString;
                            }
                        }
                    }
                }
            }

            return null;
        }

        private String setInternetModeConfiguredByInSocket() throws Exception
        {
            HttpUrl httpUrl = HttpUrl.parse("http://"+SmartWiFiSocketActivity.fixedSocketResolvedIPAddress +':'+SmartWiFiSocketActivity.fixedSocketResolvedPort +"/internet_mode_config");

            if (httpUrl != null)
            {
                HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                urlBuilder.addQueryParameter("internet_mode_configured_by", activityRefToCallMethods.convertStringToHexString(SmartWiFiSocketActivity.googleSignInAccount.getEmail()));
                urlBuilder.addQueryParameter("internet_mode_domain_name", activityRefToCallMethods.convertStringToHexString(internetModeDomainNameForAppAndSocket.get(1)));

                Request request = new Request.Builder()
                        .url(urlBuilder.build().toString())
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
                        return responseBodyString;
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Exception exceptionOccurred)
        {
            if(customProgressDialog != null)
                customProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (hasActiveInternetConnection)
                {
                    if ("success".equalsIgnoreCase(internetModeConfiguredInSocket))
                    {
                        activityRefToCallMethods.showSuccessGreenToast("Internet Mode "+(internetModeReconfiguring ? "Re" : "")+"Configured");
                        SmartWiFiSocketActivity.currentlySelectedFixedSocket.setInternetModeConfigured(true);

                        activityRefToCallMethods.saveConfiguredSockets();
                    } else
                    {
                        if (internetModeDomainNameForAppAndSocket == null || "offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            activityRefToCallMethods.showInfoGreyToast("Cloud Server is Offline for Maintenance");
                        else if ("please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            activityRefToCallMethods.showInfoGreyToast("Please upgrade your app to latest version");
                        else
                            activityRefToCallMethods.showErrorRedToast("Could not configure Internet Mode. Please try again.");
                    }
                }else
                    activityRefToCallMethods.showInfoGreyToast("Internet connection un-available");
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private String getReadableStateForFixedSocket(SmartWiFiSocketActivity.States state)
    {
        switch (state)
        {
            case OFF_WITH_NO_TIMER:
                return "0";

            case ON_WITH_NO_TIMER:
                return "1";

            case ON_WITH_TIMER:
                return "2";

            case OFF_WITH_TIMER:
                return "3";
        }

        return null;
    }

    /*private long convertSecsToMillisecs(long secs)
    {
        return secs * 1000;
    }*/

    private long convertMillisecsToSecs(long millisecs)
    {
        return millisecs / 1000;
    }

    private class BgTaskRemoveInternetModeConfiguration extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog customProgressDialog = null;
        private String responseBodyString = null;

        @Override
        protected void onPreExecute()
        {
            customProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Removing Internet Mode Configuration", "Please wait");
            customProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = new Request.Builder()
                        .url("http://"+SmartWiFiSocketActivity.fixedSocketResolvedIPAddress +':'+SmartWiFiSocketActivity.fixedSocketResolvedPort +"/remove_internet_mode_config")
                        .build();

                if (request != null)
                {
                    Response response = SmartWiFiSocketActivity.okHttpWLANClient.newCall(request).execute();

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
            if(customProgressDialog != null)
                customProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if ("success".equalsIgnoreCase(responseBodyString))
                {
                    activityRefToCallMethods.showSuccessGreenToast("Internet Mode Configuration Removed");
                    SmartWiFiSocketActivity.currentlySelectedFixedSocket.setInternetModeConfigured(false);

                    activityRefToCallMethods.saveConfiguredSockets();
                }else
                    activityRefToCallMethods.showErrorRedToast("Could not remove Internet Mode configuration. Please try again.");
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }
}
