package in.co.unifytech.socket;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatSeekBar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.IOException;
import java.util.Locale;

import in.co.unifytech.socket.utils.AsyncTaskCustomProgressDialog;
import in.co.unifytech.R;
import mobi.upod.timedurationpicker.TimeDurationPicker;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// https://developer.android.com/guide/components/fragments.html
// https://stackoverflow.com/questions/24188050/how-to-access-fragments-child-views-inside-fragments-parent-activity/24458324
public class MiscFragment extends Fragment
{
    private Activity hostActivityContext;
    private SmartWiFiSocketActivity activityRefToCallMethods;
    private View fragmentRootView;

    private TextView txtViewChargingAutoTurnOffUnavailable, txtViewDoubleTimerUnavailable;
    private CheckBox chkboxChargingAutoTurnOff, chkboxDisableWiFiOnceCharged;
    private AppCompatSeekBar seekbarSetAutoTurnBatteryPercent;
    private Button btnActivateStopDoubleTimer;
    private CompoundButton.OnCheckedChangeListener chkboxChargingAutoTurnOffCheckedChangeListener;

    private final String LOG_TAG = MiscFragment.class.getSimpleName();

    public MiscFragment()
    {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof Activity)
            hostActivityContext = (Activity) context;

        activityRefToCallMethods = ((SmartWiFiSocketActivity) getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState)
    {
        fragmentRootView = inflater.inflate(R.layout.fragment_misc, container, false);

        getViews();
        initViews();

        chkboxChargingAutoTurnOff.setOnCheckedChangeListener(chkboxChargingAutoTurnOffCheckedChangeListener);

        chkboxDisableWiFiOnceCharged.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (chkboxChargingAutoTurnOff.isChecked())
                    saveChargingAutoTurnOffToSharedPreferences(isChecked);
            }
        });

        seekbarSetAutoTurnBatteryPercent.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b)
            {
                chkboxChargingAutoTurnOff.setText(String.format(getString(R.string.charging_auto_turn_off_msg), Integer.toString(50+progress) + "%"));

                saveChargingAutoTurnOffToSharedPreferences(chkboxDisableWiFiOnceCharged.isChecked());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {

            }
        });

        btnActivateStopDoubleTimer.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (SmartWiFiSocketActivity.doubleTimerRunning)
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
                }else
                {
                    View doubleTimerDialogLayout = View.inflate(getActivity(), R.layout.dialog_double_timer, null);

                    final TimeDurationPicker timeDurationPickerDoubleTimerTimer1 = doubleTimerDialogLayout.findViewById(R.id.timeDurationPickerDoubleTimerTimer1);
                    final ToggleButton toggleBtnDoubleTimerTimer1State = doubleTimerDialogLayout.findViewById(R.id.toggleBtnDoubleTimerTimer1State);
                    final TimeDurationPicker timeDurationPickerDoubleTimerTimer2 = doubleTimerDialogLayout.findViewById(R.id.timeDurationPickerDoubleTimerTimer2);
                    final TextView txtViewDoubleTimerTimer2State = doubleTimerDialogLayout.findViewById(R.id.txtViewDoubleTimerTimer2State);
                    final EditText editTextCombinedTimer1Timer2Count = doubleTimerDialogLayout.findViewById(R.id.editTextCombinedTimer1Timer2Count);
                    final TextView txtViewDoubleTimerTimer1Count = doubleTimerDialogLayout.findViewById(R.id.txtViewDoubleTimerTimer1Count);
                    final TextView txtViewDoubleTimerTimer2Count = doubleTimerDialogLayout.findViewById(R.id.txtViewDoubleTimerTimer2Count);

                    txtViewDoubleTimerTimer2State.setText(String.format(Locale.getDefault(), getString(R.string.show_timer2_duration_state), toggleBtnDoubleTimerTimer1State.isChecked() ? getString(R.string.stateOFF) : getString(R.string.stateON)));

                    toggleBtnDoubleTimerTimer1State.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
                    {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                        {
                            txtViewDoubleTimerTimer2State.setText(String.format(Locale.getDefault(), getString(R.string.show_timer2_duration_state), isChecked ? getString(R.string.stateOFF) : getString(R.string.stateON)));
                        }
                    });

                    txtViewDoubleTimerTimer1Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer1Count), "NA times"));
                    txtViewDoubleTimerTimer2Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer2Count),  "NA times"));

                    editTextCombinedTimer1Timer2Count.addTextChangedListener(new TextWatcher()
                    {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after)
                        {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count)
                        {
                            if (editTextCombinedTimer1Timer2Count.getText().toString().isEmpty())
                            {
                                txtViewDoubleTimerTimer1Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer1Count), "NA times"));
                                txtViewDoubleTimerTimer2Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer2Count),  "NA times"));
                            }else
                            {
                                long combinedTimer1Timer2Count = Long.parseLong(editTextCombinedTimer1Timer2Count.getText().toString());

                                Log.d(LOG_TAG, "combinedTimer1Timer2Count: "+Long.toString(combinedTimer1Timer2Count));

                                if (combinedTimer1Timer2Count == 0)
                                {
                                    txtViewDoubleTimerTimer1Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer1Count), "infinitely"));
                                    txtViewDoubleTimerTimer2Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer2Count), "infinitely"));
                                }else
                                {
                                    txtViewDoubleTimerTimer1Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer1Count), Long.toString(combinedTimer1Timer2Count % 2 == 1 ? ((combinedTimer1Timer2Count / 2) + 1) : (combinedTimer1Timer2Count / 2)) + " times"));
                                    txtViewDoubleTimerTimer2Count.setText(String.format(Locale.getDefault(), getString(R.string.txtViewDoubleTimerTimer2Count), Long.toString(combinedTimer1Timer2Count / 2) + " times"));
                                }
                            }
                        }

                        @Override
                        public void afterTextChanged(Editable s)
                        {

                        }
                    });

                    final AlertDialog doubleTimerDialog = new AlertDialog.Builder(hostActivityContext)
                            .setTitle("Double Timer")
                            .setView(doubleTimerDialogLayout)
                            .setCancelable(true)
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i)
                                {
                                    dialogInterface.dismiss();
                                }
                            })
                            .setPositiveButton("Set Double Timer", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i)
                                {
                                    // overridden the method below after showing dialog because we don't want dismissal of dialog if validation fails
                                }
                            })
                            .create();

                    doubleTimerDialog.show();

                    doubleTimerDialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                        {
                            if(timeDurationPickerDoubleTimerTimer1.getDuration() <= 4000 )
                                activityRefToCallMethods.showInfoGreyToast("Timer1 Duration should be at least 5 secs");
                            else if (timeDurationPickerDoubleTimerTimer2.getDuration() <= 4000)
                                activityRefToCallMethods.showInfoGreyToast("Timer2 Duration should be at least 5 secs");
                            else if (editTextCombinedTimer1Timer2Count.getText().toString().isEmpty())
                                activityRefToCallMethods.showInfoGreyToast("Enter Timer1 + Timer2 Count");
                            /*

                            No need of below condition as timeDurationPickerDoubleTimerTimer[12].getDuration() > 6870947 is handled in socket code

                            else if (timeDurationPickerDoubleTimerTimer1.getDuration() > 6870947)
                                activityRefToCallMethods.showInfoGreyToast("Maximum Timer duration allowed is 1h 54m 30s");
                            else if (timeDurationPickerDoubleTimerTimer2.getDuration() > 6870947)
                                activityRefToCallMethods.showInfoGreyToast("Maximum Timer duration allowed is 1h 54m 30s");*/
                            else
                            {
                                doubleTimerDialog.dismiss();
                                new BgTaskActivateDoubleTimer(toggleBtnDoubleTimerTimer1State.isChecked(), timeDurationPickerDoubleTimerTimer1.getDuration() / 1000, timeDurationPickerDoubleTimerTimer2.getDuration() / 1000, Long.parseLong(editTextCombinedTimer1Timer2Count.getText().toString())).execute();
                            }
                        }
                    });
                }
            }
        });

        return fragmentRootView;
    }

    private void getViews()
    {
        txtViewChargingAutoTurnOffUnavailable = fragmentRootView.findViewById(R.id.txtViewChargingAutoTurnOffUnavailable);
        chkboxChargingAutoTurnOff = fragmentRootView.findViewById(R.id.chkboxChargingAutoTurnOff);
        seekbarSetAutoTurnBatteryPercent = fragmentRootView.findViewById(R.id.seekbarSetAutoTurnBatteryPercent);
        chkboxDisableWiFiOnceCharged = fragmentRootView.findViewById(R.id.chkboxDisableWiFiOnceCharged);

        txtViewDoubleTimerUnavailable = fragmentRootView.findViewById(R.id.txtViewDoubleTimerUnavailable);
        btnActivateStopDoubleTimer = fragmentRootView.findViewById(R.id.btnActivateStopDoubleTimer);
    }

    private void initViews()
    {
        refreshChargingAutoTurnOff();
        chkboxChargingAutoTurnOffCheckedChangeListener = new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked)
            {
                seekbarSetAutoTurnBatteryPercent.setEnabled(checked);
                chkboxDisableWiFiOnceCharged.setEnabled(checked);

                String mobileChargingAutoTurnOffSocket = activityRefToCallMethods.sharedPreferences.getString("MobileChargingAutoTurnOffSocket", null);

                if (checked)
                {
                    if (mobileChargingAutoTurnOffSocket == null)
                    {
                        seekbarSetAutoTurnBatteryPercent.setProgress(activityRefToCallMethods.sharedPreferences.getInt("MobileChargingAutoTurnOffPercent", seekbarSetAutoTurnBatteryPercent.getProgress()));
                        chkboxChargingAutoTurnOff.setText(String.format(getString(R.string.charging_auto_turn_off_msg), Integer.toString(seekbarSetAutoTurnBatteryPercent.getProgress() + 50) + "%"));

                        saveChargingAutoTurnOffToSharedPreferences(chkboxDisableWiFiOnceCharged.isChecked());
                    }else
                    {
                        if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null && !mobileChargingAutoTurnOffSocket.equals(SmartWiFiSocketActivity.currentlySelectedPortableSocket))
                        {
                            new AlertDialog.Builder(hostActivityContext)
                                    .setTitle("Confirm Charging Socket change")
                                    .setMessage("Are you sure you want to change Charging Socket from \"" + mobileChargingAutoTurnOffSocket + "\" to \"" + SmartWiFiSocketActivity.currentlySelectedPortableSocket + "\"?")
                                    .setCancelable(true)
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {
                                            seekbarSetAutoTurnBatteryPercent.setProgress(activityRefToCallMethods.sharedPreferences.getInt("MobileChargingAutoTurnOffPercent", seekbarSetAutoTurnBatteryPercent.getProgress()));
                                            chkboxChargingAutoTurnOff.setText(String.format(getString(R.string.charging_auto_turn_off_msg), Integer.toString(seekbarSetAutoTurnBatteryPercent.getProgress() + 50)));

                                            saveChargingAutoTurnOffToSharedPreferences(chkboxDisableWiFiOnceCharged.isChecked());
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton("No", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which)
                                        {
                                            chkboxChargingAutoTurnOff.setOnCheckedChangeListener(null);
                                            chkboxChargingAutoTurnOff.setChecked(false);
                                            chkboxChargingAutoTurnOff.setOnCheckedChangeListener(chkboxChargingAutoTurnOffCheckedChangeListener);

                                            dialog.dismiss();
                                        }
                                    }).create().show();
                        }
                    }
                }else
                {
                    chkboxChargingAutoTurnOff.setText(String.format(getString(R.string.charging_auto_turn_off_msg), getString(R.string.NA)));
                    activityRefToCallMethods.removeChargingAutoTurnOffFromSharedPreferences();
                }
            }
        };
    }

    private void refreshChargingAutoTurnOff()
    {
        if (SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
        {
            chkboxChargingAutoTurnOff.setEnabled(false);
            seekbarSetAutoTurnBatteryPercent.setEnabled(false);
            chkboxDisableWiFiOnceCharged.setEnabled(false);
        }else
        {
            chkboxChargingAutoTurnOff.setEnabled(true);
            seekbarSetAutoTurnBatteryPercent.setProgress(activityRefToCallMethods.sharedPreferences.getInt("MobileChargingAutoTurnOffPercent", 50));

            // DEFAULT_SETUP_SSID is the default value because it is guaranteed that DEFAULT_SETUP_SSID will never be the name of a PortableSocket
            if (activityRefToCallMethods.sharedPreferences.getString("MobileChargingAutoTurnOffSocket", SmartWiFiSocketActivity.DEFAULT_SETUP_SSID).equals(SmartWiFiSocketActivity.currentlySelectedPortableSocket))
            {
                chkboxChargingAutoTurnOff.setText(String.format(getString(R.string.charging_auto_turn_off_msg), Integer.toString(50 + seekbarSetAutoTurnBatteryPercent.getProgress()) + "%"));
                chkboxChargingAutoTurnOff.setChecked(true);
                seekbarSetAutoTurnBatteryPercent.setEnabled(true);
                chkboxDisableWiFiOnceCharged.setEnabled(true);
                chkboxDisableWiFiOnceCharged.setChecked(activityRefToCallMethods.sharedPreferences.getBoolean("MobileChargingAutoTurnOffDisableWiFiOnceCharged", false));
            }else
            {
                chkboxChargingAutoTurnOff.setText(String.format(getString(R.string.charging_auto_turn_off_msg), getString(R.string.NA)));
                chkboxChargingAutoTurnOff.setChecked(false);
                seekbarSetAutoTurnBatteryPercent.setEnabled(false);
                chkboxDisableWiFiOnceCharged.setEnabled(false);
            }
        }
    }

    private void saveChargingAutoTurnOffToSharedPreferences(boolean turnOffWiFiOnceCharged)
    {
        SharedPreferences.Editor sharedPreferencesEditor = activityRefToCallMethods.sharedPreferences.edit();
        sharedPreferencesEditor.putString("MobileChargingAutoTurnOffSocket", SmartWiFiSocketActivity.currentlySelectedPortableSocket);
        sharedPreferencesEditor.putInt("MobileChargingAutoTurnOffPercent", seekbarSetAutoTurnBatteryPercent.getProgress());
        sharedPreferencesEditor.putBoolean("MobileChargingAutoTurnOffDisableWiFiOnceCharged", turnOffWiFiOnceCharged);
        while (!sharedPreferencesEditor.commit())
            Log.d(LOG_TAG, "Retrying commit for Shared Preferences");
    }

    void refreshViews()
    {
        if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
        {
            txtViewChargingAutoTurnOffUnavailable.setVisibility(View.VISIBLE);
            chkboxChargingAutoTurnOff.setVisibility(View.GONE);
            seekbarSetAutoTurnBatteryPercent.setVisibility(View.GONE);
            chkboxDisableWiFiOnceCharged.setVisibility(View.GONE);

            txtViewChargingAutoTurnOffUnavailable.setText(getString(R.string.no_socket_selected_for_charging_auto_turn_off));

            txtViewDoubleTimerUnavailable.setVisibility(View.VISIBLE);
            btnActivateStopDoubleTimer.setVisibility(View.GONE);

            txtViewDoubleTimerUnavailable.setText(getString(R.string.no_socket_selected_for_double_timer));
        }else
        {
            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
            {
                txtViewDoubleTimerUnavailable.setVisibility(View.VISIBLE);
                btnActivateStopDoubleTimer.setVisibility(View.GONE);

                txtViewDoubleTimerUnavailable.setText(getString(R.string.double_timer_unavailable_fixed_sockets));

                txtViewChargingAutoTurnOffUnavailable.setVisibility(View.VISIBLE);
                chkboxChargingAutoTurnOff.setVisibility(View.GONE);
                seekbarSetAutoTurnBatteryPercent.setVisibility(View.GONE);
                chkboxDisableWiFiOnceCharged.setVisibility(View.GONE);

                txtViewChargingAutoTurnOffUnavailable.setText(R.string.charging_auto_turn_off_unavailable_for_fixed_sockets);
            }else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
            {
                txtViewChargingAutoTurnOffUnavailable.setVisibility(View.GONE);
                chkboxChargingAutoTurnOff.setVisibility(View.VISIBLE);
                seekbarSetAutoTurnBatteryPercent.setVisibility(View.VISIBLE);
                chkboxChargingAutoTurnOff.setVisibility(View.VISIBLE);

                refreshChargingAutoTurnOff();

                txtViewDoubleTimerUnavailable.setVisibility(View.GONE);
                btnActivateStopDoubleTimer.setVisibility(View.VISIBLE);

                btnActivateStopDoubleTimer.setText(SmartWiFiSocketActivity.doubleTimerRunning ? getString(R.string.btn_activate_stop_double_timer_stop) : getString(R.string.btn_activate_stop_double_timer_activate));
            }
        }
    }

    private class BgTaskActivateDoubleTimer extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean activateDoubleTimerSuccessIndicator = false;

        private final long doubleTimerTimer1DurationSecs, doubleTimerTimer2DurationSecs, total_timer1_timer2_count;
        private final boolean doubleTimerTimer1State;

        BgTaskActivateDoubleTimer(boolean doubleTimerTimer1State, long doubleTimerTimer1DurationSecs, long doubleTimerTimer2DurationSecs, long total_timer1_timer2_count)
        {
            this.doubleTimerTimer1DurationSecs = doubleTimerTimer1DurationSecs;
            this.doubleTimerTimer1State = doubleTimerTimer1State;
            this.doubleTimerTimer2DurationSecs = doubleTimerTimer2DurationSecs;
            this.total_timer1_timer2_count = total_timer1_timer2_count;
        }

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Activating Double Timer", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                HttpUrl httpUrl = HttpUrl.parse("http://"+SmartWiFiSocketActivity.PORTABLE_SOCKET_SERVER_IP+':'+SmartWiFiSocketActivity.SERVICE_PORT+"/gpio/relay/double_timer");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                    urlBuilder.addQueryParameter("double_timer_timer1_duration_secs", Long.toString(doubleTimerTimer1DurationSecs));
                    urlBuilder.addQueryParameter("double_timer_timer1_state", doubleTimerTimer1State ? "1" : "0");
                    urlBuilder.addQueryParameter("double_timer_timer2_duration_secs", Long.toString(doubleTimerTimer2DurationSecs));
                    urlBuilder.addQueryParameter("total_timer1_timer2_count", Long.toString(total_timer1_timer2_count));

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
                            activateDoubleTimerSuccessIndicator = "success".equalsIgnoreCase(responseBodyString);
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
                if (activateDoubleTimerSuccessIndicator)
                {
                    activityRefToCallMethods.showSuccessGreenToast("Successfully activated Double Timer");
                    activityRefToCallMethods.call_BgTaskGetLatestStateViaWLAN();
                }else
                    activityRefToCallMethods.showErrorRedToast("Could not activate Double Timer. Please try again.");
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
}