package in.co.unifytech.socket;


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import in.co.unifytech.R;
import in.co.unifytech.socket.pojos.PojoScheduleTimerInfo;
import in.co.unifytech.socket.utils.AsyncTaskCustomProgressDialog;
import in.co.unifytech.socket.utils.TimezoneData;
import mobi.upod.timedurationpicker.TimeDurationUtil;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class StateChangeFragment extends Fragment
{
    private Activity hostActivityContext;
    private SmartWiFiSocketActivity activityRefToCallMethods;
    private View fragmentRootView;

    private Button btnStateChange;

    private TextView txtViewShowLastUpdatedDetailsHeaders;
    private TextView txtViewShowLastUpdatedDetails;

    private final String LOG_TAG = StateChangeFragment.class.getSimpleName();

    public StateChangeFragment()
    {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof Activity)
            hostActivityContext = (Activity) context;

        activityRefToCallMethods = (SmartWiFiSocketActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        fragmentRootView = inflater.inflate(R.layout.fragment_state_change, container, false);

        getViews();

        btnStateChange.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {

                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    switch (SmartWiFiSocketActivity.currentState)
                    {
                        case ON_WITH_NO_TIMER:
                            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null && SmartWiFiSocketActivity.isInternetModeActivated)
                                activityRefToCallMethods.call_BgTaskGetLatestStateViaInternet_then_BgTaskStateChangeOverInternet(false);
                            else
                                activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN_then_BgTaskStateChangeOverWiFi(false);   // this block is executed for both Home Socket over WiFi and Portable Socket
                            break;

                        case OFF_WITH_NO_TIMER:
                            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null && SmartWiFiSocketActivity.isInternetModeActivated)
                                activityRefToCallMethods.call_BgTaskGetLatestStateViaInternet_then_BgTaskStateChangeOverInternet(true);
                            else
                                activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN_then_BgTaskStateChangeOverWiFi(true);    // this block is executed for both Home Socket over WiFi and Portable Socket
                            break;

                        case ON_WITH_TIMER:
                        case OFF_WITH_TIMER:
                            String runningTimerTypeFullFormStringToDisplay = null;
                            if ("osct".equalsIgnoreCase(SmartWiFiSocketActivity.runningTimerType))
                                runningTimerTypeFullFormStringToDisplay = "One Shot Current Timer";
                            else if ("osft".equalsIgnoreCase(SmartWiFiSocketActivity.runningTimerType))
                                runningTimerTypeFullFormStringToDisplay = "One Shot Future Timer";
                            else if ("rt".equalsIgnoreCase(SmartWiFiSocketActivity.runningTimerType))
                                runningTimerTypeFullFormStringToDisplay = "Recurring Timer";

                            final String runningTimerTypeFullFormStringToDisplayFinalVar = runningTimerTypeFullFormStringToDisplay;

                            new AlertDialog.Builder(hostActivityContext)
                                    .setTitle("Confirm Timer Stop")
                                    .setMessage("Are you sure you want to stop currently running "+runningTimerTypeFullFormStringToDisplay+"?")
                                    .setCancelable(true)
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {
                                            final CharSequence[] afterStoppingRunningTimerStates = new CharSequence[]{getString(R.string.stateON), getString(R.string.stateOFF)};
                                            new AlertDialog.Builder(hostActivityContext)
                                                    .setTitle("After stopping Timer, turn Appliance ..")
                                                    .setCancelable(true)
                                                    .setSingleChoiceItems(afterStoppingRunningTimerStates, -1, new DialogInterface.OnClickListener()
                                                    {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which)
                                                        {
                                                            dialog.dismiss();
                                                            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                                                            {
                                                                boolean afterStoppingRunningTimerState = getString(R.string.stateON).equals(afterStoppingRunningTimerStates[which].toString());
                                                                if (SmartWiFiSocketActivity.isInternetModeActivated)
                                                                    activityRefToCallMethods.call_BgTaskGetLatestStateViaInternet_then_BgTaskStateChangeOverInternet(afterStoppingRunningTimerState);
                                                                else
                                                                    new BgTaskStateChangeStopRunningOSCT_OSFT_RTOverWLAN(afterStoppingRunningTimerState, runningTimerTypeFullFormStringToDisplayFinalVar).execute();
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
                                    })
                                    .setNegativeButton("No", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {
                                            dialog.dismiss();
                                        }
                                    }).create().show();
                            break;

                        case ON_DOUBLE_TIMER_TIMER1:
                        case OFF_DOUBLE_TIMER_TIMER1:
                        case ON_DOUBLE_TIMER_TIMER2:
                        case OFF_DOUBLE_TIMER_TIMER2:
                            if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null && SmartWiFiSocketActivity.doubleTimerRunning)
                            {
                                new AlertDialog.Builder(hostActivityContext)
                                        .setTitle("Stop Double Timer?")
                                        .setMessage("Are you sure you want to stop Double Timer?")
                                        .setCancelable(true)
                                        .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                                dialog.dismiss();
                                                new BgTaskStateChangeStopDoubleTimer().execute();
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
                            }
                            break;
                    }
                }
            }
        });

        return fragmentRootView;
    }

    private void getViews()
    {
        btnStateChange = fragmentRootView.findViewById(R.id.btnChangeState);

        txtViewShowLastUpdatedDetailsHeaders = fragmentRootView.findViewById(R.id.txtViewShowLastUpdatedDetailsHeaders);
        txtViewShowLastUpdatedDetails = fragmentRootView.findViewById(R.id.txtViewShowLastUpdatedDetails);
    }

    void refreshViews(SmartWiFiSocketActivity.States currentState, String runningTimerType, long runningTimerSecsLeft)
    {
        changeAppearanceBtnStateChange(currentState, runningTimerType, runningTimerSecsLeft);

        if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
        {
            txtViewShowLastUpdatedDetailsHeaders.setText(getString(R.string.last_updated_no_socket_headers));
            txtViewShowLastUpdatedDetails.setText(getString(R.string.last_updated_no_socket_details));
        }else
        {
            StringBuilder stringToSet = new StringBuilder();
            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
            {
                stringToSet.append("=> ");
                stringToSet.append(SmartWiFiSocketActivity.lastStateUpdatedTimestampMillisecs == 0 ? getString(R.string.NA) : PojoScheduleTimerInfo.sdfDateTimeDisplayFormat.format(new Date(SmartWiFiSocketActivity.lastStateUpdatedTimestampMillisecs)));
                stringToSet.append("\n=> ");
                if (SmartWiFiSocketActivity.isInternetModeActivated)
                {
                    stringToSet.append(TimezoneData.TZIDsToRegion.get(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getStoredTZID()));
                    stringToSet.append("\n=> ");
                    stringToSet.append(SmartWiFiSocketActivity.lastUpdatedByEmailOrSocketForInternetMode == null ? getString(R.string.NA) : SmartWiFiSocketActivity.lastUpdatedByEmailOrSocketForInternetMode);
                    if (SmartWiFiSocketActivity.lastUpdateActionByAppInternetMode != null)
                    {
                        stringToSet.append("\n=> ");
                        stringToSet.append(SmartWiFiSocketActivity.lastUpdateActionByAppInternetMode);
                    }
                }else
                {
                    stringToSet.append(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getExternalWiFiSSID());
                    stringToSet.append("\n=> ");
                    stringToSet.append(TimezoneData.TZIDsToRegion.get(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getStoredTZID()));
                }
                stringToSet.append("\n=> ");
                stringToSet.append(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion() == 0 ? getString(R.string.NA) : String.format(getString(R.string.txtview_socket_version_num), SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion()));    // adding string ".0" to version as version in Socket is incremental integers not version strings

                if (!SmartWiFiSocketActivity.isInternetModeActivated)
                {
                    stringToSet.append("\n=> ");
                    stringToSet.append(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getRememberPowerCuts() ? "Yes" : "No");
                }

                if (SmartWiFiSocketActivity.isInternetModeActivated)
                    stringToSet.append("\n=> Around every 10 mins");

                String phoneTZ = TimeZone.getDefault().getID();
                Set<String> StoredTZIDTZFileAndAllSoftLinks = new HashSet<>();
                for (Map.Entry<String, List<String>> entry:TimezoneData.localtimeContentsToTZIDs.entrySet())
                {
                    if (entry.getKey().equals(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getStoredTZID()) || entry.getValue().contains(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getStoredTZID()))
                    {
                        StoredTZIDTZFileAndAllSoftLinks.add(entry.getKey());
                        StoredTZIDTZFileAndAllSoftLinks.addAll(entry.getValue());
                        break;
                    }
                }
                boolean phoneTZIsSameAsSocketTimezone = "NA".equalsIgnoreCase(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getStoredTZID()) || StoredTZIDTZFileAndAllSoftLinks.contains(phoneTZ);

                // NA socketTimezone means socket SNTP synchronization not needed
                if (phoneTZIsSameAsSocketTimezone)
                {
                    if (SmartWiFiSocketActivity.isInternetModeActivated)
                    {
                        if (SmartWiFiSocketActivity.lastUpdateActionByAppInternetMode == null)
                            txtViewShowLastUpdatedDetailsHeaders.setText(getString(R.string.last_updated_fixed_socket_internet_mode_details_headers));
                        else
                            txtViewShowLastUpdatedDetailsHeaders.setText(getString(R.string.last_updated_fixed_socket_internet_mode_details_headers_show_last_update_action_too));
                    }else
                        txtViewShowLastUpdatedDetailsHeaders.setText(getString(R.string.last_updated_fixed_socket_details_headers));
                    txtViewShowLastUpdatedDetails.setText(stringToSet.toString());
                } else
                {
                    activityRefToCallMethods.showInfoGreyToast("Mobile timezone is not same as Socket timezone");   // it is necessary to do this so that dates are synchronized when getting todays schedule timers
                    activityRefToCallMethods.resetStateMaintainerModel();
                }
            } else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
            {
                if (SmartWiFiSocketActivity.doubleTimerRunning)
                    txtViewShowLastUpdatedDetailsHeaders.setText(getString(R.string.last_updated_portable_socket_details_headers_double_timer));
                else
                    txtViewShowLastUpdatedDetailsHeaders.setText(getString(R.string.last_updated_portable_socket_details_headers_plain));

                stringToSet.append("=> ");
                stringToSet.append(SmartWiFiSocketActivity.lastStateUpdatedTimestampMillisecs == 0 ? getString(R.string.NA) : PojoScheduleTimerInfo.sdfDateTimeDisplayFormat.format(new Date(SmartWiFiSocketActivity.lastStateUpdatedTimestampMillisecs)));
                stringToSet.append("\n");

                if (SmartWiFiSocketActivity.doubleTimerRunning)
                {
                    stringToSet.append("=> ");
                    stringToSet.append(getString(SmartWiFiSocketActivity.doubleTimerTimer1State ? R.string.stateON : R.string.stateOFF));
                    stringToSet.append(" for ");
                    stringToSet.append(getReadableRunningTimerSecsLeft(SmartWiFiSocketActivity.doubleTimerTimer1DurationSecs));
                    stringToSet.append("\n");

                    stringToSet.append("=> ");
                    stringToSet.append(getString(SmartWiFiSocketActivity.doubleTimerTimer1State ? R.string.stateOFF : R.string.stateON));
                    stringToSet.append(" for ");
                    stringToSet.append(getReadableRunningTimerSecsLeft(SmartWiFiSocketActivity.doubleTimerTimer2DurationSecs));
                    stringToSet.append("\n");

                    stringToSet.append("=> ");
                    if (SmartWiFiSocketActivity.totalTimer1Timer2Count > 0)
                    {
                        stringToSet.append(SmartWiFiSocketActivity.currentTimer1Timer2Count % 2 == 1 ? ((SmartWiFiSocketActivity.currentTimer1Timer2Count / 2) + 1) : (SmartWiFiSocketActivity.currentTimer1Timer2Count / 2));
                        stringToSet.append("/");
                        stringToSet.append(SmartWiFiSocketActivity.totalTimer1Timer2Count % 2 == 1 ? ((SmartWiFiSocketActivity.totalTimer1Timer2Count / 2) + 1) : (SmartWiFiSocketActivity.totalTimer1Timer2Count / 2));
                    }else
                        stringToSet.append("Infinite");
                    stringToSet.append("\n");

                    stringToSet.append("=> ");
                    if (SmartWiFiSocketActivity.totalTimer1Timer2Count > 0)
                    {
                        stringToSet.append(SmartWiFiSocketActivity.currentTimer1Timer2Count / 2);
                        stringToSet.append("/");
                        stringToSet.append(SmartWiFiSocketActivity.totalTimer1Timer2Count / 2);
                    }else
                        stringToSet.append("Infinite");
                    stringToSet.append("\n");
                }

                stringToSet.append("=> ");
                stringToSet.append(SmartWiFiSocketActivity.socketSoftwareVersion == 0 ? getString(R.string.NA) : String.format(getString(R.string.txtview_socket_version_num), SmartWiFiSocketActivity.socketSoftwareVersion));

                txtViewShowLastUpdatedDetails.setText(stringToSet.toString());
            }
        }
    }

    private void changeAppearanceBtnStateChange(SmartWiFiSocketActivity.States currentState, String runningTimerType, long runningTimerSecsLeft)
    {
        switch (currentState)
        {
            case OFF_WITH_NO_TIMER:
                btnStateChange.setText(getString(R.string.stateOFF_WITH_NO_TIMER));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOnOffWithNoTimer_OnWithTimer_OnDoubleTimer_Disconnected_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_off_with_no_timer, null));
                break;

            case ON_WITH_NO_TIMER:
                btnStateChange.setText(getString(R.string.stateON_WITH_NO_TIMER));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOnOffWithNoTimer_OnWithTimer_OnDoubleTimer_Disconnected_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_on_with_no_timer, null));
                break;

            case ON_WITH_TIMER:
                btnStateChange.setText(String.format(getString(R.string.stateON_WITH_TIMER), runningTimerType+": "+getReadableRunningTimerSecsLeft(runningTimerSecsLeft)));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOnOffWithNoTimer_OnWithTimer_OnDoubleTimer_Disconnected_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_on_with_timer_on_double_timer, null));
                break;

            case OFF_WITH_TIMER:
                btnStateChange.setText(String.format(getString(R.string.stateOFF_WITH_TIMER), runningTimerType+": "+getReadableRunningTimerSecsLeft(runningTimerSecsLeft)));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOffWithTimer_OffDoubleTimer_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_off_with_timer_off_double_timer, null));
                break;

            case ON_DOUBLE_TIMER_TIMER2:
            case ON_DOUBLE_TIMER_TIMER1:
                btnStateChange.setText(getString(R.string.stateON_DOUBLE_TIMER));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOnOffWithNoTimer_OnWithTimer_OnDoubleTimer_Disconnected_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_on_with_timer_on_double_timer, null));
                break;

            case OFF_DOUBLE_TIMER_TIMER2:
            case OFF_DOUBLE_TIMER_TIMER1:
                btnStateChange.setText(getString(R.string.stateOFF_DOUBLE_TIMER));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOffWithTimer_OffDoubleTimer_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_off_with_timer_off_double_timer, null));
                break;

            case DISCONNECTED:
                btnStateChange.setText(getString(R.string.stateDisconnected));
                btnStateChange.setTextColor(ResourcesCompat.getColor(getResources(), R.color.stateOnOffWithNoTimer_OnWithTimer_OnDoubleTimer_Disconnected_FgColor, null));
                btnStateChange.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.state_change_btn_state_disconnected, null));
                break;
        }
    }

    private String getReadableRunningTimerSecsLeft(long runningTimerSecsLeft)
    {
        String temp[] = TimeDurationUtil.formatHoursMinutesSeconds(runningTimerSecsLeft * 1000).split(":"); // converting secs to milli secs for TimeDurationUtil
        return String.format(Locale.getDefault(), "%02dh %02dm %02ds", Integer.valueOf(temp[0]), Integer.valueOf(temp[1]), Integer.valueOf(temp[2])).replaceAll("00h", "").replaceAll("00m", "").trim();
    }

    void call_BgTaskStateChangeOverWiFi(boolean desiredState)
    {
        new BgTaskStateChangeOverWiFi(desiredState).execute();
    }

    void call_BgTaskStateChangeOverInternet(boolean desiredState)
    {
        new BgTaskStateChangeOverInternet(desiredState).execute();
    }

    private class BgTaskStateChangeOverWiFi extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean stateChangeSuccessIndicator = false;
        private final boolean desiredState;

        // reference : https://stackoverflow.com/questions/3075009/android-how-can-i-pass-parameters-to-asynctasks-onPreExecute
        BgTaskStateChangeOverWiFi(boolean desiredState)
        {
            this.desiredState = desiredState;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Turning appliance "+(desiredState?getString(R.string.stateON):getString(R.string.stateOFF)), "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = null;
                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                    request = new Request.Builder()
                            .url("http://"+SmartWiFiSocketActivity.fixedSocketResolvedIPAddress +':'+SmartWiFiSocketActivity.fixedSocketResolvedPort +"/gpio/relay/"+(desiredState?'1':'0'))
                            .build();
                else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
                    request = new Request.Builder()
                            .url("http://"+SmartWiFiSocketActivity.PORTABLE_SOCKET_SERVER_IP+':'+SmartWiFiSocketActivity.SERVICE_PORT+"/gpio/relay/"+(desiredState?'1':'0'))
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
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (stateChangeSuccessIndicator)
                {
                    SmartWiFiSocketActivity.currentState = desiredState? SmartWiFiSocketActivity.States.ON_WITH_NO_TIMER : SmartWiFiSocketActivity.States.OFF_WITH_NO_TIMER;
                    SmartWiFiSocketActivity.lastStateUpdatedTimestampMillisecs = System.currentTimeMillis();
                    refreshViews(SmartWiFiSocketActivity.currentState, null, 0);
                }else
                    activityRefToCallMethods.showErrorRedToast("Could not turn appliance "+(desiredState ? getString(R.string.stateON):getString(R.string.stateOFF))+". Please try again");
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskStateChangeOverInternet extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean stateChangeSuccessIndicator = false, hasActiveInternetConnection = false, desiredState = false;
        private String socketsPerUserEntryUpdateResponseString = "";
        private List<String> internetModeDomainNameForAppAndSocket;

        // reference : https://stackoverflow.com/questions/3075009/android-how-can-i-pass-parameters-to-asynctasks-onPreExecute
        BgTaskStateChangeOverInternet(boolean desiredState)
        {
            this.desiredState = desiredState;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Turning appliance "+(desiredState? getString(R.string.stateON) : getString(R.string.stateOFF)), "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {

                hasActiveInternetConnection = activityRefToCallMethods.hasActiveInternetConnection();

                if (hasActiveInternetConnection)
                {
                    internetModeDomainNameForAppAndSocket = activityRefToCallMethods.retrieveInternetModeDomainName(false);

                    if (internetModeDomainNameForAppAndSocket != null && !"offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)) && !"please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                    {
                        if (internetModeDomainNameForAppAndSocket.get(1).equals(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeDomainNameForSocket()))
                        {
                            socketsPerUserEntryUpdateResponseString = socketsPerUserEntryUpdate();

                            if ("success".equalsIgnoreCase(socketsPerUserEntryUpdateResponseString))
                            {
                                // SmartWiFiSocketActivity.currentlySelectedFixedSocket.setInternetModeLastUpdated(System.currentTimeMillis());
                                // activityRefToCallMethods.saveConfiguredSockets();

                                stateChangeSuccessIndicator = true;
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

        // this will be called only when there is no timer running (see case statement above for onclicklistener), also we require only PUT method since registration has already created a record using POST method
        private String socketsPerUserEntryUpdate() throws Exception
        {
            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion() == 1)
            {
                HttpUrl httpUrl = HttpUrl.parse(internetModeDomainNameForAppAndSocket.get(0) + "/app/api/v1.0/sockets_per_user");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                    String email = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getInternetModeConfiguredBy();
                    String socketName = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getMDNSHostName();
                    String externalWiFiSSID = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getExternalWiFiSSID();
                    String socketSoftwareVersion = Integer.toString(SmartWiFiSocketActivity.currentlySelectedFixedSocket.getSocketSoftwareVersion());
                    String currentDesiredState = getReadableStateForFixedSocket(desiredState ? SmartWiFiSocketActivity.States.ON_WITH_NO_TIMER : SmartWiFiSocketActivity.States.OFF_WITH_NO_TIMER);
                    String timezoneDetails = SmartWiFiSocketActivity.currentlySelectedFixedSocket.getTimezoneDetails();
                    String lastUpdatedDateTime = Long.toString(convertMillisecsToSecs(System.currentTimeMillis()));
                    String lastUpdatedBy = SmartWiFiSocketActivity.googleSignInAccount.getEmail();
                    String lastUpdatedByDeviceSource = "app";
                    String googleOAuthIdToken = SmartWiFiSocketActivity.googleSignInAccount.getIdToken();

                    if (email != null && socketName != null && currentDesiredState != null && lastUpdatedDateTime != null && timezoneDetails != null && lastUpdatedBy != null && googleOAuthIdToken != null)
                    {
                        RequestBody requestBody;
                        if (SmartWiFiSocketActivity.isAnyTimerRunning)
                            requestBody = new MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart("email", email)
                                    .addFormDataPart("socket_name", socketName)
                                    .addFormDataPart("external_wifi_ssid", externalWiFiSSID)
                                    .addFormDataPart("socket_software_version", socketSoftwareVersion)
                                    .addFormDataPart("current_desired_state", currentDesiredState)
                                    .addFormDataPart("timezone_details", timezoneDetails)
                                    .addFormDataPart("running_timer_type", SmartWiFiSocketActivity.runningTimerType)
                                    .addFormDataPart("running_timer_cron_mask_config_string", SmartWiFiSocketActivity.runningTimerCronMaskConfigString)
                                    .addFormDataPart("running_timer_secs_left", Long.toString(SmartWiFiSocketActivity.runningTimerSecsLeft))
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
                                    .addFormDataPart("socket_software_version", socketSoftwareVersion)
                                    .addFormDataPart("current_desired_state", currentDesiredState)
                                    .addFormDataPart("timezone_details", timezoneDetails)
                                    .addFormDataPart("last_updated_datetime", lastUpdatedDateTime)
                                    .addFormDataPart("last_updated_by", lastUpdatedBy)
                                    .addFormDataPart("last_updated_by_device_source", lastUpdatedByDeviceSource)
                                    .addFormDataPart("google_oauth_id_token", googleOAuthIdToken)
                                    .build();

                        Request request = new Request.Builder()
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

                default:
                    return null;
            }
        }

        private long convertMillisecsToSecs(long millisecs)
        {
            return millisecs / 1000;
        }

        @Override
        protected void onPostExecute(Exception exceptionOccurred)
        {
            if(asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if (hasActiveInternetConnection)
                {
                    if (stateChangeSuccessIndicator)
                    {
                        activityRefToCallMethods.showSuccessGreenToast("Update successful. Please wait until Last Updated By has value \"socket\"");
                        activityRefToCallMethods.call_BgTaskGetLatestStateViaInternet_not_showing_progressDialog();
                    }else
                    {
                        if (internetModeDomainNameForAppAndSocket == null || "offline".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            activityRefToCallMethods.showInfoGreyToast("Cloud Server is Offline for Maintenance");
                        else if ("please_upgrade_app_version".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            activityRefToCallMethods.showInfoGreyToast("Please upgrade your app to latest version");
                        else if ("please_reconfigure_internet_mode".equalsIgnoreCase(internetModeDomainNameForAppAndSocket.get(0)))
                            activityRefToCallMethods.showInfoGreyToast("Please reconfigure Internet Mode");
                        else
                            activityRefToCallMethods.showErrorRedToast("Could not turn appliance " + (desiredState ? getString(R.string.stateON) : getString(R.string.stateOFF)) + ". Please try again");
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

    private class BgTaskStateChangeStopDoubleTimer extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean stopDoubleTimerSuccessIndicator = false;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Stopping Double Timer", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                Request request = new Request.Builder()
                        .url("http://"+SmartWiFiSocketActivity.PORTABLE_SOCKET_SERVER_IP+':'+SmartWiFiSocketActivity.SERVICE_PORT+"/gpio/relay/stop_double_timer")
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
                        stopDoubleTimerSuccessIndicator = "success".equalsIgnoreCase(responseBodyString);
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
                if (stopDoubleTimerSuccessIndicator)
                {
                    activityRefToCallMethods.showSuccessGreenToast("Successfully stopped Double Timer");
                    activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN();

                    activityRefToCallMethods.stop_handlerGetLatestStateEveryNSeconds();
                }else
                    activityRefToCallMethods.showErrorRedToast("Could not stop Double Timer. Please try again.");
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskStateChangeStopRunningOSCT_OSFT_RTOverWLAN extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean stateAfterStoppingRunningTimer = false;
        private String runningTimerTypeFullFormStringToDisplay = "";
        private String responseBodyString = null;

        BgTaskStateChangeStopRunningOSCT_OSFT_RTOverWLAN(boolean stateAfterStoppingRunningTimer, String runningTimerTypeFullFormStringToDisplay)
        {
            this.runningTimerTypeFullFormStringToDisplay = runningTimerTypeFullFormStringToDisplay;
            this.stateAfterStoppingRunningTimer = stateAfterStoppingRunningTimer;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Stopping "+runningTimerTypeFullFormStringToDisplay, "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                HttpUrl httpUrl = HttpUrl.parse("http://"+SmartWiFiSocketActivity.fixedSocketResolvedIPAddress+':'+SmartWiFiSocketActivity.fixedSocketResolvedPort+"/gpio/relay/stop_running_timer");

                if (httpUrl != null)
                {
                    Request request = new Request.Builder()
                            .url(httpUrl.newBuilder().addQueryParameter("state_after_stopping_running_timer", stateAfterStoppingRunningTimer ? "1" : "0").build().toString())
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
                if ("success".equalsIgnoreCase(responseBodyString) || "no_running_timer".equalsIgnoreCase(responseBodyString))
                {
                    activityRefToCallMethods.showSuccessGreenToast("success".equalsIgnoreCase(responseBodyString) ? ("Successfully stopped "+runningTimerTypeFullFormStringToDisplay) : "No Running Timer Found");
                    activityRefToCallMethods.stop_countDownTimerForDisplay(SmartWiFiSocketActivity.runningTimerPojoObject);

                    activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN();
                }else
                    activityRefToCallMethods.showErrorRedToast("Could not stop "+runningTimerTypeFullFormStringToDisplay+". Please try again.");
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }
}
