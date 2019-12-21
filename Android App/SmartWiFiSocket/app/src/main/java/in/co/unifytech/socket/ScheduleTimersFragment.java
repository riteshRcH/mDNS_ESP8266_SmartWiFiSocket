package in.co.unifytech.socket;


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import in.co.unifytech.socket.pojos.PojoScheduleTimerInfo;
import in.co.unifytech.socket.utils.AsyncTaskCustomProgressDialog;
import in.co.unifytech.R;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ScheduleTimersFragment extends Fragment
{
    private Activity hostActivityContext;
    private SmartWiFiSocketActivity activityRefToCallMethods;
    private View fragmentRootView;
    private TextView txtViewLatestListNotRetrievedOrAdviseMsg;
    private CheckBox chkBoxShowMoreOptions;
    CheckBox chkBoxShowOnlyTodaysScheduleTimers;
    final CompoundButton.OnCheckedChangeListener chkBoxShowOnlyTodaysScheduleTimersListener = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            btnUpdateEnabledScheduleTimers.setEnabled(!isChecked);
            btnUpdateEnabledScheduleTimers.setVisibility(isChecked ? View.GONE : View.VISIBLE);

            btnRemovePastScheduleTimerEntries.setEnabled(!isChecked);
            btnRemovePastScheduleTimerEntries.setVisibility(isChecked ? View.GONE : View.VISIBLE);

            btnUpdateRunSkipForTodaysSchedule.setEnabled(isChecked);
            btnUpdateRunSkipForTodaysSchedule.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                activityRefToCallMethods.call_BgTaskGetLatestScheduleTimerInfo(isChecked);
        }
    };
    private Button btnRemovePastScheduleTimerEntries;
    private Button btnUpdateEnabledScheduleTimers;
    private Button btnUpdateRunSkipForTodaysSchedule;

    private ScheduleTimerInfoCustomAdapter scheduleTimerInfoCustomAdapter;
    private RecyclerView recyclerViewScheduleAndTimers;

    private final String LOG_TAG = ScheduleTimersFragment.class.getSimpleName();

    public ScheduleTimersFragment()
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
        fragmentRootView = inflater.inflate(R.layout.fragment_schedule_timers, container, false);

        getViews();
        initViews();

        chkBoxShowMoreOptions.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                chkBoxShowOnlyTodaysScheduleTimers.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (isChecked)
                    btnUpdateRunSkipForTodaysSchedule.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
                else
                    btnUpdateRunSkipForTodaysSchedule.setVisibility(View.GONE);
                btnUpdateEnabledScheduleTimers.setVisibility(isChecked ? (chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE) : View.GONE);
                btnRemovePastScheduleTimerEntries.setVisibility(isChecked ? (chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE) : View.GONE);
            }
        });
        chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(chkBoxShowOnlyTodaysScheduleTimersListener);

        btnUpdateRunSkipForTodaysSchedule.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null)
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (SmartWiFiSocketActivity.allScheduleTimerInfoPojos != null && SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size() > 0)
                    {
                        if (SmartWiFiSocketActivity.madeAnyChangeToRunSkipForTodaysScheduleTimer)
                            new BgTaskUpdateRunSkipForToday().execute();
                        else
                            activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_changes_detected));
                    }else
                        activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_schedule_timer_entries_found_for_today));
                }
            }
        });

        btnUpdateEnabledScheduleTimers.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
                        activityRefToCallMethods.showInfoGreyToast(getString(R.string.schedule_timer_unavailable_portable_socket));

                    if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                    {
                        if (SmartWiFiSocketActivity.allScheduleTimerInfoPojos != null && SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size() > 0)
                        {
                            if (SmartWiFiSocketActivity.madeAnyChangeToEnabledOSFTScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledFSScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledRSScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledRTScheduleTimers)
                                new BgTaskUpdateEnabledScheduleTimers().execute();
                            else
                                activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_changes_detected));
                        }else
                            activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_schedule_timer_entries_found));
                    }
                }
            }
        });

        btnRemovePastScheduleTimerEntries.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.no_socket_selected));
                else
                {
                    if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
                        activityRefToCallMethods.showInfoGreyToast(getString(R.string.schedule_timer_unavailable_portable_socket));

                    if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
                    {
                        if (SmartWiFiSocketActivity.madeAnyChangeToEnabledOSFTScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledFSScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledRTScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledRSScheduleTimers)
                            activityRefToCallMethods.showInfoGreyToast(getString(R.string.first_perform_update_enabled_schedule_timers));
                        else
                            removePastScheduleTimerEntries(false);
                    }
                }
            }
        });

        return fragmentRootView;
    }

    private void getViews()
    {
        txtViewLatestListNotRetrievedOrAdviseMsg = fragmentRootView.findViewById(R.id.txtViewLatestListNotRetrievedOrAdviseMsg);

        chkBoxShowMoreOptions = fragmentRootView.findViewById(R.id.chkBoxShowMoreOptions);
        chkBoxShowOnlyTodaysScheduleTimers = fragmentRootView.findViewById(R.id.chkBoxShowOnlyTodaysScheduleTimers);
        btnUpdateEnabledScheduleTimers = fragmentRootView.findViewById(R.id.btnUpdateEnabledScheduleTimers);
        btnRemovePastScheduleTimerEntries = fragmentRootView.findViewById(R.id.btnRemovePastScheduleTimerEntries);
        btnUpdateRunSkipForTodaysSchedule = fragmentRootView.findViewById(R.id.btnUpdateRunSkipForTodaysSchedule);

        recyclerViewScheduleAndTimers = fragmentRootView.findViewById(R.id.listViewOtherScheduleAndTimers);
    }

    private void initViews()
    {
        chkBoxShowMoreOptions.setVisibility(View.GONE);
        chkBoxShowMoreOptions.setChecked(false);
        chkBoxShowOnlyTodaysScheduleTimers.setVisibility(chkBoxShowMoreOptions.isChecked() ? View.VISIBLE : View.GONE);
        btnUpdateRunSkipForTodaysSchedule.setVisibility(chkBoxShowMoreOptions.isChecked() ? View.VISIBLE : View.GONE);
        btnUpdateEnabledScheduleTimers.setVisibility(chkBoxShowMoreOptions.isChecked() ? View.VISIBLE : View.GONE);
        btnRemovePastScheduleTimerEntries.setVisibility(chkBoxShowMoreOptions.isChecked() ? View.VISIBLE : View.GONE);

        chkBoxShowOnlyTodaysScheduleTimers.setChecked(false);
        btnUpdateRunSkipForTodaysSchedule.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());
        btnUpdateRunSkipForTodaysSchedule.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);

        scheduleTimerInfoCustomAdapter = new ScheduleTimerInfoCustomAdapter(SmartWiFiSocketActivity.allScheduleTimerInfoPojos);
        recyclerViewScheduleAndTimers.setLayoutManager(new LinearLayoutManager(hostActivityContext));
        recyclerViewScheduleAndTimers.setItemAnimator(null);
        recyclerViewScheduleAndTimers.setHasFixedSize(false);
        recyclerViewScheduleAndTimers.addItemDecoration(new RecyclerView.ItemDecoration()
        {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state)
            {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.bottom = 45;
            }
        });
        recyclerViewScheduleAndTimers.setAdapter(scheduleTimerInfoCustomAdapter);
    }

    private void showHideTxtViewLatestListNotRetrievedOrAdviseMsgAndChkBoxShowOnlyTodaysScheduleTimers(boolean isListEmpty)
    {
        if (SmartWiFiSocketActivity.currentlySelectedFixedSocket == null && SmartWiFiSocketActivity.currentlySelectedPortableSocket == null)
        {
            txtViewLatestListNotRetrievedOrAdviseMsg.setText(getString(R.string.no_socket_selected));

            chkBoxShowMoreOptions.setVisibility(View.GONE);
            chkBoxShowMoreOptions.setChecked(false);

            chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(null);
            chkBoxShowOnlyTodaysScheduleTimers.setChecked(false);
            chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(chkBoxShowOnlyTodaysScheduleTimersListener);

            btnUpdateRunSkipForTodaysSchedule.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());
            btnUpdateRunSkipForTodaysSchedule.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
        }else
        {
            if (SmartWiFiSocketActivity.currentlySelectedFixedSocket != null)
            {
                if (SmartWiFiSocketActivity.isInternetModeActivated)
                {
                    txtViewLatestListNotRetrievedOrAdviseMsg.setText(getString(R.string.schedule_timer_unavailable_internet_mode));

                    chkBoxShowMoreOptions.setVisibility(View.GONE);
                    chkBoxShowMoreOptions.setChecked(false);

                    chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(null);
                    chkBoxShowOnlyTodaysScheduleTimers.setChecked(false);
                    chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(chkBoxShowOnlyTodaysScheduleTimersListener);

                    btnUpdateRunSkipForTodaysSchedule.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());
                    btnUpdateRunSkipForTodaysSchedule.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
                }else
                {
                    txtViewLatestListNotRetrievedOrAdviseMsg.setText( isListEmpty ? getString(R.string.no_schedule_timer_entries_found) : getString(R.string.schedule_timers_advise));

                    // what components are visible/not visible depends on chkBoxShowMoreOptions and chkBoxShowOnlyTodaysScheduleTimers
                    chkBoxShowMoreOptions.setVisibility(View.VISIBLE);
                }
            }else if (SmartWiFiSocketActivity.currentlySelectedPortableSocket != null)
            {
                txtViewLatestListNotRetrievedOrAdviseMsg.setText(getString(R.string.schedule_timer_unavailable_portable_socket));

                chkBoxShowMoreOptions.setVisibility(View.GONE);
                chkBoxShowMoreOptions.setChecked(false);

                chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(null);
                chkBoxShowOnlyTodaysScheduleTimers.setChecked(false);
                chkBoxShowOnlyTodaysScheduleTimers.setOnCheckedChangeListener(chkBoxShowOnlyTodaysScheduleTimersListener);

                btnUpdateRunSkipForTodaysSchedule.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());
                btnUpdateRunSkipForTodaysSchedule.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void showHideListViewScheduleAndTimers(boolean show)
    {
        recyclerViewScheduleAndTimers.setVisibility(show?View.VISIBLE:View.GONE);
    }

    void refreshViews()
    {
        Collections.sort(SmartWiFiSocketActivity.allScheduleTimerInfoPojos);

        showHideTxtViewLatestListNotRetrievedOrAdviseMsgAndChkBoxShowOnlyTodaysScheduleTimers(SmartWiFiSocketActivity.allScheduleTimerInfoPojos.isEmpty());
        showHideListViewScheduleAndTimers(!SmartWiFiSocketActivity.allScheduleTimerInfoPojos.isEmpty());

        scheduleTimerInfoCustomAdapter.notifyDataSetChanged();
    }

    class ScheduleTimerInfoCustomAdapter extends RecyclerView.Adapter<ScheduleTimerInfoCustomAdapter.ViewsHolder>
    {
        private final List<PojoScheduleTimerInfo> allScheduleTimerInfoPojos;

        ScheduleTimerInfoCustomAdapter(List<PojoScheduleTimerInfo> allScheduleTimerInfoPojos)
        {
            this.allScheduleTimerInfoPojos = allScheduleTimerInfoPojos;
        }

        class ViewsHolder extends RecyclerView.ViewHolder
        {
            //View parentView;
            final TextView txtViewScheduleTimerType;
            final Button btnDeleteScheduleTimerEntry;
            final TableRow tableRowRecurrenceDateRange;
            final TextView txtViewRecurrenceStartDate;
            final TextView txtViewRecurrenceToMsg;
            final TextView txtViewRecurrenceEndDate;
            final TableRow tableRowDaysToRunOn;
            final TextView txtViewIsRunOnMonday;
            final TextView txtViewIsRunOnTuesday;
            final TextView txtViewIsRunOnWednesday;
            final TextView txtViewIsRunOnThursday;
            final TextView txtViewIsRunOnFriday;
            final TextView txtViewIsRunOnSaturday;
            final TextView txtViewIsRunOnSunday;
            final TextView txtViewShowFutureScheduledDateTimeRecurringTime;
            final TextView txtViewShowDesiredScheduleState;
            final TableRow tableRowTimerBeforeStateDurationAfterState;
            final TextView txtViewShowTimerBeforeState;
            final TextView txtViewShowTimerDuration;
            final TextView txtViewShowTimerAfterState;
            final CheckBox chkBoxScheduleTimerEnabled;
            final CheckBox chkBoxSkipForToday;

            ViewsHolder(View parentView)
            {
                super(parentView);
                //this.parentView = parentView;
                txtViewScheduleTimerType = parentView.findViewById(R.id.txtViewScheduleTimerType);
                btnDeleteScheduleTimerEntry = parentView.findViewById(R.id.btnDeleteScheduleTimerEntry);
                tableRowRecurrenceDateRange = parentView.findViewById(R.id.tableRowRecurrenceDateRange);
                txtViewRecurrenceStartDate = parentView.findViewById(R.id.txtViewRecurrenceStartDate);
                txtViewRecurrenceToMsg = parentView.findViewById(R.id.txtViewRecurrenceToMsg);
                txtViewRecurrenceEndDate = parentView.findViewById(R.id.txtViewRecurrenceEndDate);
                tableRowDaysToRunOn = parentView.findViewById(R.id.tableRowDaysToRunOn);
                txtViewIsRunOnMonday = parentView.findViewById(R.id.txtViewIsRunOnMonday);
                txtViewIsRunOnTuesday = parentView.findViewById(R.id.txtViewIsRunOnTuesday);
                txtViewIsRunOnWednesday = parentView.findViewById(R.id.txtViewIsRunOnWednesday);
                txtViewIsRunOnThursday = parentView.findViewById(R.id.txtViewIsRunOnThursday);
                txtViewIsRunOnFriday = parentView.findViewById(R.id.txtViewIsRunOnFriday);
                txtViewIsRunOnSaturday = parentView.findViewById(R.id.txtViewIsRunOnSaturday);
                txtViewIsRunOnSunday = parentView.findViewById(R.id.txtViewIsRunOnSunday);
                txtViewShowFutureScheduledDateTimeRecurringTime = parentView.findViewById(R.id.txtViewShowFutureScheduledDateTimeRecurringTime);
                txtViewShowDesiredScheduleState = parentView.findViewById(R.id.txtViewShowDesiredScheduleState);
                tableRowTimerBeforeStateDurationAfterState = parentView.findViewById(R.id.tableRowTimerBeforeStateDurationAfterState);
                txtViewShowTimerBeforeState = parentView.findViewById(R.id.txtViewShowTimerBeforeState);
                txtViewShowTimerDuration = parentView.findViewById(R.id.txtViewShowTimerDuration);
                txtViewShowTimerAfterState = parentView.findViewById(R.id.txtViewShowTimerAfterState);
                chkBoxScheduleTimerEnabled = parentView.findViewById(R.id.chkBoxScheduleTimerEnabled);
                chkBoxSkipForToday = parentView.findViewById(R.id.chkBoxSkipForToday);
            }
        }

        @Override
        public int getItemCount()
        {
            return allScheduleTimerInfoPojos.size();
        }

        @Override
        public ViewsHolder onCreateViewHolder(ViewGroup parent, int viewType)
        {
            return new ViewsHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_schedule_list_item_card_view, parent, false));
        }

        public void onBindViewHolder(final ViewsHolder viewsHolder, final int position)
        {
            PojoScheduleTimerInfo pojoScheduleTimerInfo = allScheduleTimerInfoPojos.get(viewsHolder.getAdapterPosition());
            viewsHolder.txtViewScheduleTimerType.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunningNow()?R.color.schedule_timer_type_running_text_color:R.color.schedule_timer_type_normal_text_color));
            viewsHolder.txtViewScheduleTimerType.setTypeface(null, pojoScheduleTimerInfo.isRunningNow()?Typeface.BOLD:Typeface.NORMAL);

            switch (pojoScheduleTimerInfo.getScheduleTimerType())
            {
                case ONE_SHOT_CURRENT_TIMER:
                    makeOrRefreshOneShotCurrentTimer(viewsHolder, pojoScheduleTimerInfo);
                    break;

                case ONE_SHOT_FUTURE_TIMER:
                    makeOrRefreshOneShotFutureTimerListItem(viewsHolder, pojoScheduleTimerInfo);
                    break;

                case FUTURE_SCHEDULE:
                    makeOrRefreshFutureScheduleListItem(viewsHolder, pojoScheduleTimerInfo);
                    break;

                case RECURRING_SCHEDULE:
                    makeOrRefreshRecurringScheduleListItem(viewsHolder, pojoScheduleTimerInfo);
                    break;

                case RECURRING_TIMER:
                    makeOrRefreshRecurringTimerListItem(viewsHolder, pojoScheduleTimerInfo);
                    break;
            }

            viewsHolder.btnDeleteScheduleTimerEntry.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(final View view)
                {

                    PojoScheduleTimerInfo scheduleTimerSelectedForDeletion = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(viewsHolder.getAdapterPosition());
                    if (scheduleTimerSelectedForDeletion.getScheduleTimerType().equals(PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_CURRENT_TIMER))
                        activityRefToCallMethods.showInfoGreyToast("Please stop the timer to delete the entry");
                    else if (scheduleTimerSelectedForDeletion.isRunningNow())
                        activityRefToCallMethods.showInfoGreyToast("Please stop the timer and then proceed for deletion");
                    else
                    {
                        if (SmartWiFiSocketActivity.madeAnyChangeToEnabledOSFTScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledFSScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledRTScheduleTimers || SmartWiFiSocketActivity.madeAnyChangeToEnabledRSScheduleTimers)
                            activityRefToCallMethods.showInfoGreyToast(getString(R.string.first_perform_update_enabled_schedule_timers));
                        else
                        {
                            new AlertDialog.Builder(hostActivityContext)
                                    .setTitle("Confirm delete")
                                    .setMessage("Are you sure you want to delete?")
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i)
                                        {
                                            dialogInterface.dismiss();

                                            int index = viewsHolder.getAdapterPosition();
                                            PojoScheduleTimerInfo pojoSavedScheduleTimerEntryToRemove = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(index);
                                            switch (pojoSavedScheduleTimerEntryToRemove.getScheduleTimerType())
                                            {
                                                case FUTURE_SCHEDULE:
                                                case ONE_SHOT_FUTURE_TIMER:
                                                    new BgTaskRemoveSavedEntryFromDevice(true, activityRefToCallMethods.isFSOrOSFTScheduledLaterForToday(pojoSavedScheduleTimerEntryToRemove)).execute(index);
                                                    break;

                                                case RECURRING_SCHEDULE:
                                                case RECURRING_TIMER:
                                                    new BgTaskRemoveSavedEntryFromDevice(true, activityRefToCallMethods.isRSOrRTScheduledForLaterToday(pojoSavedScheduleTimerEntryToRemove)).execute(index);
                                                    break;
                                            }
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
                    }
                }
            });

            viewsHolder.chkBoxScheduleTimerEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    PojoScheduleTimerInfo pojoScheduleTimerInfoForWhichEnabledChanged = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(viewsHolder.getAdapterPosition());
                    switch (pojoScheduleTimerInfoForWhichEnabledChanged.getScheduleTimerType())
                    {
                        case ONE_SHOT_FUTURE_TIMER:
                            if (activityRefToCallMethods.isFSOrOSFTScheduledLaterForToday(pojoScheduleTimerInfoForWhichEnabledChanged))
                            {
                                pojoScheduleTimerInfoForWhichEnabledChanged.setIsEnabled(isChecked);
                                SmartWiFiSocketActivity.madeAnyChangeToEnabledOSFTScheduleTimers = true;
                            }else
                                activityRefToCallMethods.showInfoGreyToast("Selected entry occurs in past. Enabling/Disabling is irrelevant");
                            break;

                        case FUTURE_SCHEDULE:
                            if (activityRefToCallMethods.isFSOrOSFTScheduledLaterForToday(pojoScheduleTimerInfoForWhichEnabledChanged))
                            {
                                pojoScheduleTimerInfoForWhichEnabledChanged.setIsEnabled(isChecked);
                                SmartWiFiSocketActivity.madeAnyChangeToEnabledFSScheduleTimers = true;
                            }else
                                activityRefToCallMethods.showInfoGreyToast("Selected entry occurs in past. Enabling/Disabling is irrelevant");
                            break;

                        case RECURRING_SCHEDULE:
                            pojoScheduleTimerInfoForWhichEnabledChanged.setIsEnabled(isChecked);
                            SmartWiFiSocketActivity.madeAnyChangeToEnabledRSScheduleTimers = true;
                            break;

                        case RECURRING_TIMER:
                            pojoScheduleTimerInfoForWhichEnabledChanged.setIsEnabled(isChecked);
                            SmartWiFiSocketActivity.madeAnyChangeToEnabledRTScheduleTimers = true;
                            break;
                    }
                }
            });

            viewsHolder.chkBoxSkipForToday.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    PojoScheduleTimerInfo pojoScheduleTimerInfoChangedRunSkip = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(viewsHolder.getAdapterPosition());
                    switch (pojoScheduleTimerInfoChangedRunSkip.getScheduleTimerType())
                    {
                        case ONE_SHOT_FUTURE_TIMER:
                        case FUTURE_SCHEDULE:
                            if (activityRefToCallMethods.isFSOrOSFTScheduledLaterForToday(pojoScheduleTimerInfoChangedRunSkip))
                            {
                                SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(viewsHolder.getAdapterPosition()).setScheduleSkippedForToday(isChecked);
                                SmartWiFiSocketActivity.madeAnyChangeToRunSkipForTodaysScheduleTimer = true;
                            }else
                                activityRefToCallMethods.showInfoGreyToast("Selected entry occurs in past. Changing Skip for Today is irrelevant");
                            break;

                        case RECURRING_SCHEDULE:
                        case RECURRING_TIMER:
                            if (activityRefToCallMethods.isRSOrRTScheduledForLaterToday(pojoScheduleTimerInfoChangedRunSkip))
                            {
                                SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(viewsHolder.getAdapterPosition()).setScheduleSkippedForToday(isChecked);
                                SmartWiFiSocketActivity.madeAnyChangeToRunSkipForTodaysScheduleTimer = true;
                            }else
                                activityRefToCallMethods.showInfoGreyToast("Selected entry occurs in past. Changing Skip for Today is irrelevant");
                            break;
                    }
                }
            });
        }

        void makeOrRefreshOneShotCurrentTimer(ViewsHolder viewsHolder, PojoScheduleTimerInfo pojoScheduleTimerInfo)
        {
            viewsHolder.txtViewScheduleTimerType.setVisibility(View.VISIBLE);
            viewsHolder.btnDeleteScheduleTimerEntry.setVisibility(View.VISIBLE);
            viewsHolder.tableRowRecurrenceDateRange.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceStartDate.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceToMsg.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceEndDate.setVisibility(View.GONE);
            viewsHolder.tableRowDaysToRunOn.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnMonday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnTuesday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnWednesday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnThursday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnFriday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnSaturday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnSunday.setVisibility(View.GONE);
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setVisibility(View.GONE);
            viewsHolder.txtViewShowDesiredScheduleState.setVisibility(View.GONE);
            viewsHolder.tableRowTimerBeforeStateDurationAfterState.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerBeforeState.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerDuration.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerAfterState.setVisibility(View.VISIBLE);
            viewsHolder.chkBoxScheduleTimerEnabled.setVisibility(View.VISIBLE);
            viewsHolder.chkBoxSkipForToday.setVisibility(View.GONE);
            viewsHolder.chkBoxSkipForToday.setEnabled(false);

            viewsHolder.chkBoxScheduleTimerEnabled.setChecked(pojoScheduleTimerInfo.isEnabled());
            viewsHolder.chkBoxScheduleTimerEnabled.setEnabled(false);

            viewsHolder.txtViewScheduleTimerType.setText(pojoScheduleTimerInfo.getReadableScheduleTimerType(hostActivityContext));
            viewsHolder.txtViewShowTimerBeforeState.setText(pojoScheduleTimerInfo.getReadableBeforeTimerState(hostActivityContext));
            viewsHolder.txtViewShowTimerDuration.setText(pojoScheduleTimerInfo.getReadableTimerDuration().replaceAll("00[hms]", "").trim());
            viewsHolder.txtViewShowTimerAfterState.setText(pojoScheduleTimerInfo.getReadableAfterTimerState(hostActivityContext));

            viewsHolder.txtViewShowTimerBeforeState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getBeforeTimerStartState()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewShowTimerAfterState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getAfterTimerExpiresState()?R.color.green_fg:R.color.red_fg));
        }

        void makeOrRefreshOneShotFutureTimerListItem(ViewsHolder viewsHolder, PojoScheduleTimerInfo pojoScheduleTimerInfo)
        {
            viewsHolder.txtViewScheduleTimerType.setVisibility(View.VISIBLE);
            viewsHolder.btnDeleteScheduleTimerEntry.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.tableRowRecurrenceDateRange.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceStartDate.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceToMsg.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceEndDate.setVisibility(View.GONE);
            viewsHolder.tableRowDaysToRunOn.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnMonday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnTuesday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnWednesday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnThursday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnFriday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnSaturday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnSunday.setVisibility(View.GONE);
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowDesiredScheduleState.setVisibility(View.GONE);
            viewsHolder.tableRowTimerBeforeStateDurationAfterState.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerBeforeState.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerDuration.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerAfterState.setVisibility(View.VISIBLE);
            viewsHolder.chkBoxScheduleTimerEnabled.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.chkBoxSkipForToday.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
            viewsHolder.chkBoxSkipForToday.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());

            viewsHolder.chkBoxScheduleTimerEnabled.setChecked(pojoScheduleTimerInfo.isEnabled());
            viewsHolder.chkBoxScheduleTimerEnabled.setEnabled(true);

            if (viewsHolder.chkBoxSkipForToday.isEnabled())
                viewsHolder.chkBoxSkipForToday.setChecked(pojoScheduleTimerInfo.isScheduleSkippedForToday());

            viewsHolder.txtViewScheduleTimerType.setText(pojoScheduleTimerInfo.getReadableScheduleTimerType(hostActivityContext));
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setText(pojoScheduleTimerInfo.getReadableFutureDateTime());
            viewsHolder.txtViewShowTimerBeforeState.setText(pojoScheduleTimerInfo.getReadableBeforeTimerState(hostActivityContext));
            viewsHolder.txtViewShowTimerDuration.setText(pojoScheduleTimerInfo.getReadableTimerDuration().replaceAll("00[hms]", "").trim());
            viewsHolder.txtViewShowTimerAfterState.setText(pojoScheduleTimerInfo.getReadableAfterTimerState(hostActivityContext));

            viewsHolder.txtViewShowTimerBeforeState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getBeforeTimerStartState()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewShowTimerAfterState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getAfterTimerExpiresState()?R.color.green_fg:R.color.red_fg));
        }
        void makeOrRefreshFutureScheduleListItem(ViewsHolder viewsHolder, PojoScheduleTimerInfo pojoScheduleTimerInfo)
        {
            viewsHolder.txtViewScheduleTimerType.setVisibility(View.VISIBLE);
            viewsHolder.btnDeleteScheduleTimerEntry.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.tableRowRecurrenceDateRange.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceStartDate.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceToMsg.setVisibility(View.GONE);
            viewsHolder.txtViewRecurrenceEndDate.setVisibility(View.GONE);
            viewsHolder.tableRowDaysToRunOn.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnMonday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnTuesday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnWednesday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnThursday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnFriday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnSaturday.setVisibility(View.GONE);
            viewsHolder.txtViewIsRunOnSunday.setVisibility(View.GONE);
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowDesiredScheduleState.setVisibility(View.VISIBLE);
            viewsHolder.tableRowTimerBeforeStateDurationAfterState.setVisibility(View.GONE);
            viewsHolder.txtViewShowTimerBeforeState.setVisibility(View.GONE);
            viewsHolder.txtViewShowTimerDuration.setVisibility(View.GONE);
            viewsHolder.txtViewShowTimerAfterState.setVisibility(View.GONE);
            viewsHolder.chkBoxScheduleTimerEnabled.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.chkBoxSkipForToday.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
            viewsHolder.chkBoxSkipForToday.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());

            viewsHolder.chkBoxScheduleTimerEnabled.setChecked(pojoScheduleTimerInfo.isEnabled());
            viewsHolder.chkBoxScheduleTimerEnabled.setEnabled(true);

            if (viewsHolder.chkBoxSkipForToday.isEnabled())
                viewsHolder.chkBoxSkipForToday.setChecked(pojoScheduleTimerInfo.isScheduleSkippedForToday());

            viewsHolder.txtViewScheduleTimerType.setText(pojoScheduleTimerInfo.getReadableScheduleTimerType(hostActivityContext));
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setText(pojoScheduleTimerInfo.getReadableFutureDateTime());
            viewsHolder.txtViewShowDesiredScheduleState.setText(pojoScheduleTimerInfo.getReadableDesiredScheduleState(hostActivityContext));

            viewsHolder.txtViewShowDesiredScheduleState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getDesiredScheduleState()?R.color.green_fg:R.color.red_fg));
        }
        void makeOrRefreshRecurringScheduleListItem(ViewsHolder viewsHolder, PojoScheduleTimerInfo pojoScheduleTimerInfo)
        {
            viewsHolder.txtViewScheduleTimerType.setVisibility(View.VISIBLE);
            viewsHolder.btnDeleteScheduleTimerEntry.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.tableRowRecurrenceDateRange.setVisibility(pojoScheduleTimerInfo.getRecurringRangeStartDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.txtViewRecurrenceStartDate.setVisibility(pojoScheduleTimerInfo.getRecurringRangeStartDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.txtViewRecurrenceToMsg.setVisibility(pojoScheduleTimerInfo.getRecurringRangeStartDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.txtViewRecurrenceEndDate.setVisibility(pojoScheduleTimerInfo.getRecurringRangeEndDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.tableRowDaysToRunOn.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnMonday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnTuesday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnWednesday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnThursday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnFriday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnSaturday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnSunday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowDesiredScheduleState.setVisibility(View.VISIBLE);
            viewsHolder.tableRowTimerBeforeStateDurationAfterState.setVisibility(View.GONE);
            viewsHolder.txtViewShowTimerBeforeState.setVisibility(View.GONE);
            viewsHolder.txtViewShowTimerDuration.setVisibility(View.GONE);
            viewsHolder.txtViewShowTimerAfterState.setVisibility(View.GONE);
            viewsHolder.chkBoxScheduleTimerEnabled.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.chkBoxSkipForToday.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
            viewsHolder.chkBoxSkipForToday.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());

            viewsHolder.chkBoxScheduleTimerEnabled.setChecked(pojoScheduleTimerInfo.isEnabled());
            viewsHolder.chkBoxScheduleTimerEnabled.setEnabled(true);

            if (viewsHolder.chkBoxSkipForToday.isEnabled())
                viewsHolder.chkBoxSkipForToday.setChecked(pojoScheduleTimerInfo.isScheduleSkippedForToday());

            viewsHolder.txtViewScheduleTimerType.setText(pojoScheduleTimerInfo.getReadableScheduleTimerType(hostActivityContext));
            viewsHolder.txtViewRecurrenceStartDate.setText(pojoScheduleTimerInfo.getReadableRecurringRangeStartDate());
            viewsHolder.txtViewRecurrenceEndDate.setText(pojoScheduleTimerInfo.getReadableRecurringRangeEndDate());
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setText(pojoScheduleTimerInfo.getReadableRecurringTime());
            viewsHolder.txtViewShowDesiredScheduleState.setText(pojoScheduleTimerInfo.getReadableDesiredScheduleState(hostActivityContext));

            viewsHolder.txtViewIsRunOnMonday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnMonday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnTuesday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnTuesday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnWednesday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnWednesday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnThursday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnThursday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnFriday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnFriday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnSaturday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnSaturday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnSunday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnSunday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewShowDesiredScheduleState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getDesiredScheduleState()?R.color.green_fg:R.color.red_fg));
        }
        void makeOrRefreshRecurringTimerListItem(ViewsHolder viewsHolder, PojoScheduleTimerInfo pojoScheduleTimerInfo)
        {
            viewsHolder.txtViewScheduleTimerType.setVisibility(View.VISIBLE);
            viewsHolder.btnDeleteScheduleTimerEntry.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.tableRowRecurrenceDateRange.setVisibility(pojoScheduleTimerInfo.getRecurringRangeStartDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.txtViewRecurrenceStartDate.setVisibility(pojoScheduleTimerInfo.getRecurringRangeStartDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.txtViewRecurrenceToMsg.setVisibility(pojoScheduleTimerInfo.getRecurringRangeStartDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.txtViewRecurrenceEndDate.setVisibility(pojoScheduleTimerInfo.getRecurringRangeEndDate()>0?View.VISIBLE:View.GONE);
            viewsHolder.tableRowDaysToRunOn.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnMonday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnTuesday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnWednesday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnThursday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnFriday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnSaturday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewIsRunOnSunday.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowDesiredScheduleState.setVisibility(View.GONE);
            viewsHolder.tableRowTimerBeforeStateDurationAfterState.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerBeforeState.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerDuration.setVisibility(View.VISIBLE);
            viewsHolder.txtViewShowTimerAfterState.setVisibility(View.VISIBLE);
            viewsHolder.chkBoxScheduleTimerEnabled.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.GONE : View.VISIBLE);
            viewsHolder.chkBoxSkipForToday.setVisibility(chkBoxShowOnlyTodaysScheduleTimers.isChecked() ? View.VISIBLE : View.GONE);
            viewsHolder.chkBoxSkipForToday.setEnabled(chkBoxShowOnlyTodaysScheduleTimers.isChecked());

            viewsHolder.chkBoxScheduleTimerEnabled.setChecked(pojoScheduleTimerInfo.isEnabled());
            viewsHolder.chkBoxScheduleTimerEnabled.setEnabled(true);

            if (viewsHolder.chkBoxSkipForToday.isEnabled())
                viewsHolder.chkBoxSkipForToday.setChecked(pojoScheduleTimerInfo.isScheduleSkippedForToday());

            viewsHolder.txtViewScheduleTimerType.setText(pojoScheduleTimerInfo.getReadableScheduleTimerType(hostActivityContext));
            viewsHolder.txtViewRecurrenceStartDate.setText(pojoScheduleTimerInfo.getReadableRecurringRangeStartDate());
            viewsHolder.txtViewRecurrenceEndDate.setText(pojoScheduleTimerInfo.getReadableRecurringRangeEndDate());
            viewsHolder.txtViewShowFutureScheduledDateTimeRecurringTime.setText(pojoScheduleTimerInfo.getReadableRecurringTime());
            viewsHolder.txtViewShowTimerBeforeState.setText(pojoScheduleTimerInfo.getReadableBeforeTimerState(hostActivityContext));
            viewsHolder.txtViewShowTimerDuration.setText(pojoScheduleTimerInfo.getReadableTimerDuration().replaceAll("00[hms]", "").trim());
            viewsHolder.txtViewShowTimerAfterState.setText(pojoScheduleTimerInfo.getReadableAfterTimerState(hostActivityContext));

            viewsHolder.txtViewIsRunOnMonday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnMonday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnTuesday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnTuesday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnWednesday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnWednesday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnThursday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnThursday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnFriday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnFriday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnSaturday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnSaturday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewIsRunOnSunday.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.isRunOnSunday()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewShowTimerBeforeState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getBeforeTimerStartState()?R.color.green_fg:R.color.red_fg));
            viewsHolder.txtViewShowTimerAfterState.setTextColor(ContextCompat.getColor(hostActivityContext, pojoScheduleTimerInfo.getAfterTimerExpiresState()?R.color.green_fg:R.color.red_fg));
        }
    }

    void removePastScheduleTimerEntries(boolean calledFromTabSelect)
    {
        int pastScheduleTimerEntryCount = 0;
        for (int i = 0; i < SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size(); i++)
        {
            PojoScheduleTimerInfo pojoScheduleTimerInfo = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i);
            switch (pojoScheduleTimerInfo.getScheduleTimerType())
            {
                case FUTURE_SCHEDULE:
                case ONE_SHOT_FUTURE_TIMER:
                    if (pojoScheduleTimerInfo.getFutureDateTime() < (System.currentTimeMillis() / 1000))
                    {
                        pastScheduleTimerEntryCount++;
                        new BgTaskRemoveSavedEntryFromDevice(false, false).execute(i);
                    }
                    break;

                case RECURRING_SCHEDULE:
                case RECURRING_TIMER:
                    if (pojoScheduleTimerInfo.getRecurringRangeEndDate() != 0 && pojoScheduleTimerInfo.getRecurringRangeEndDate() < (System.currentTimeMillis() / 1000))
                    {
                        pastScheduleTimerEntryCount++;
                        new BgTaskRemoveSavedEntryFromDevice(false, false).execute(i);
                    }
                    break;
            }
        }

        if (pastScheduleTimerEntryCount > 0)
            activityRefToCallMethods.showSuccessGreenToast("Successfully removed " + pastScheduleTimerEntryCount + " Past Entries");
        else
        {
            if (!calledFromTabSelect)
                activityRefToCallMethods.showInfoGreyToast("No Past Entries found");
        }
    }

    private class BgTaskRemoveSavedEntryFromDevice extends AsyncTask<Integer, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private Integer indexToRemove;
        private boolean isScheduleTimerEntryScheduledForToday = false;
        private boolean removingEntryByUserAndShowProgressDialog = false;
        private boolean gotErrorWhileSaving1OfScheduleTimers = false;
        private final List<PojoScheduleTimerInfo> copyOfSpecificScheduleTimerTypeExceptEntryToRemove = new ArrayList<>();
        private String responseBodyString;

        BgTaskRemoveSavedEntryFromDevice(boolean removingEntryByUserAndShowProgressDialog, boolean isScheduleTimerEntryScheduledForToday)
        {
            this.removingEntryByUserAndShowProgressDialog = removingEntryByUserAndShowProgressDialog;
            this.isScheduleTimerEntryScheduledForToday = isScheduleTimerEntryScheduledForToday;
        }

        @Override
        protected void onPreExecute()
        {
            if (removingEntryByUserAndShowProgressDialog)
            {
                asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Removing entry from device", "Please wait");
                asyncTaskCustomProgressDialog.show();
            }
        }

        @Override
        protected Exception doInBackground(Integer... params)
        {
            try
            {
                indexToRemove = params[0];
                PojoScheduleTimerInfo pojoSavedScheduleTimerEntryToRemove = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(indexToRemove);

                copyOfSpecificScheduleTimerTypeExceptEntryToRemove.clear();
                for (int i=0;i<SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size();i++)
                    if (SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == pojoSavedScheduleTimerEntryToRemove.getScheduleTimerType() && !SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i).toCronMaskConfigString().equals(pojoSavedScheduleTimerEntryToRemove.toCronMaskConfigString()))
                        copyOfSpecificScheduleTimerTypeExceptEntryToRemove.add(SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i));
                Collections.sort(copyOfSpecificScheduleTimerTypeExceptEntryToRemove);

                HttpUrl httpUrl = HttpUrl.parse("http://"+SmartWiFiSocketActivity.fixedSocketResolvedIPAddress+":"+SmartWiFiSocketActivity.fixedSocketResolvedPort+"/save_update_enabled_remove_schedule_timer_or_update_run_skip_for_today");

                if (httpUrl != null)
                {
                    HttpUrl.Builder urlBuilder = httpUrl.newBuilder();
                    urlBuilder.addQueryParameter("call_type", removingEntryByUserAndShowProgressDialog ? "remove_saved_schedule_timer" : "removing_past_entries");
                    switch (pojoSavedScheduleTimerEntryToRemove.getScheduleTimerType())
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
                    Calendar calendar = Calendar.getInstance();
                    urlBuilder.addQueryParameter("todays_schedule_timers", "_" + Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)) + Integer.toString(calendar.get(Calendar.MONTH) + 1) + Integer.toString(calendar.get(Calendar.YEAR)));

                    if (copyOfSpecificScheduleTimerTypeExceptEntryToRemove.size() == 0)
                    {
                        // save nothing to the file as the last entry is being deleted, nil check added in ESP
                        // urlBuilder.addQueryParameter("cron_mask_config_string", "");
                        urlBuilder.addQueryParameter("truncate_before_update", "1");
                        urlBuilder.addQueryParameter("last_run_skip_update_or_todays_entry_saved_updated_removed", isScheduleTimerEntryScheduledForToday ? "1" : "0");

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
                                responseBodyString = responseBody.string();
                                if (responseBodyString != null)
                                    responseBodyString = responseBodyString.trim();
                                gotErrorWhileSaving1OfScheduleTimers = !"success".equalsIgnoreCase(responseBodyString);
                            }
                        }
                    }else
                    {
                        for (int i = 0; i < copyOfSpecificScheduleTimerTypeExceptEntryToRemove.size(); i++)
                        {
                            // save rest pojos apart from the one which has to be removed (identified by cron mask config string, the same way earlier method removed the cron mask from file in ESP)
                            if (!copyOfSpecificScheduleTimerTypeExceptEntryToRemove.get(i).toCronMaskConfigString().equals(pojoSavedScheduleTimerEntryToRemove.toCronMaskConfigString()))
                            {
                                urlBuilder.addQueryParameter("cron_mask_config_string", activityRefToCallMethods.convertStringToHexString(copyOfSpecificScheduleTimerTypeExceptEntryToRemove.get(i).toCronMaskConfigString()));
                                urlBuilder.addQueryParameter("truncate_before_update", i == 0 ? "1" : "0");
                                // reboot ESP only when last entry appended to file and if the removed entry was scheduled for today
                                urlBuilder.addQueryParameter("last_run_skip_update_or_todays_entry_saved_updated_removed", ((i == (copyOfSpecificScheduleTimerTypeExceptEntryToRemove.size() - 1)) && isScheduleTimerEntryScheduledForToday) ? "1" : "0");

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
                                        responseBodyString = responseBody.string();
                                        if (responseBodyString != null)
                                            responseBodyString = responseBodyString.trim();
                                        if (!"success".equalsIgnoreCase(responseBodyString))
                                        {
                                            gotErrorWhileSaving1OfScheduleTimers = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!gotErrorWhileSaving1OfScheduleTimers)
                    SmartWiFiSocketActivity.allScheduleTimerInfoPojos.remove(indexToRemove.intValue());
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
            if(removingEntryByUserAndShowProgressDialog && asyncTaskCustomProgressDialog != null)
                asyncTaskCustomProgressDialog.dismiss();

            if(exceptionOccurred==null)
            {
                if ("error_no_rtc".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.time_not_synced_on_socket));
                else if ("error_another_timer_running".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.another_timer_already_running));
                else if ("allowed_only_when_appliance_off".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast("Removal can be performed only when appliance is OFF");
                else
                {
                    if (!gotErrorWhileSaving1OfScheduleTimers)
                    {
                        scheduleTimerInfoCustomAdapter.notifyItemRemoved(indexToRemove);
                        if (removingEntryByUserAndShowProgressDialog)
                            activityRefToCallMethods.showSuccessGreenToast("Successfully removed saved entry");

                        if (isScheduleTimerEntryScheduledForToday)
                        {
                            Log.d(LOG_TAG, "Cron rescheduling will happen in 5 seconds in Socket");
                            //activityRefToCallMethods.resetStateMaintainerModel();
                        }else
                            refreshViews();
                    }else
                        activityRefToCallMethods.showErrorRedToast("Couldn't remove saved entry from device. Please try again");
                }
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskUpdateRunSkipForToday extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean gotErrorWhileUpdating1OfScheduleTimers = false;
        private String responseBodyString;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Updating Todays Schedule/Timers", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                HttpUrl httpUrl = HttpUrl.parse("http://" + SmartWiFiSocketActivity.fixedSocketResolvedIPAddress + ":" + SmartWiFiSocketActivity.fixedSocketResolvedPort + "/save_update_enabled_remove_schedule_timer_or_update_run_skip_for_today");
                if (httpUrl != null)
                {
                    Calendar calendar = Calendar.getInstance();
                    String todays_schedule_timers = "_" + Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)) + Integer.toString(calendar.get(Calendar.MONTH) + 1) + Integer.toString(calendar.get(Calendar.YEAR));
                    for (int i = 0; i < SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size(); i++)
                    {
                        PojoScheduleTimerInfo pojoScheduleTimerInfo = SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i);
                        HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                        urlBuilder.addQueryParameter("call_type", "update_run_skip_for_today");
                        urlBuilder.addQueryParameter("schedule_timer_filename", todays_schedule_timers);
                        urlBuilder.addQueryParameter("todays_schedule_timers", todays_schedule_timers);
                        switch (pojoScheduleTimerInfo.getScheduleTimerType())
                        {
                            case ONE_SHOT_FUTURE_TIMER:
                                urlBuilder.addQueryParameter("cron_mask_config_string", activityRefToCallMethods.convertStringToHexString((pojoScheduleTimerInfo.isScheduleSkippedForToday() ? "0" : "1")+"OSFT="+pojoScheduleTimerInfo.toCronMaskConfigString()));
                                break;

                            case FUTURE_SCHEDULE:
                                urlBuilder.addQueryParameter("cron_mask_config_string", activityRefToCallMethods.convertStringToHexString((pojoScheduleTimerInfo.isScheduleSkippedForToday() ? "0" : "1")+"FS="+pojoScheduleTimerInfo.toCronMaskConfigString()));
                                break;

                            case RECURRING_SCHEDULE:
                                urlBuilder.addQueryParameter("cron_mask_config_string", activityRefToCallMethods.convertStringToHexString((pojoScheduleTimerInfo.isScheduleSkippedForToday() ? "0" : "1")+"RS="+pojoScheduleTimerInfo.toCronMaskConfigString()));
                                break;

                            case RECURRING_TIMER:
                                urlBuilder.addQueryParameter("cron_mask_config_string", activityRefToCallMethods.convertStringToHexString((pojoScheduleTimerInfo.isScheduleSkippedForToday() ? "0" : "1")+"RT="+pojoScheduleTimerInfo.toCronMaskConfigString()));
                                break;
                        }
                        urlBuilder.addQueryParameter("truncate_before_update", i == 0 ? "1" : "0");
                        urlBuilder.addQueryParameter("last_run_skip_update_or_todays_entry_saved_updated_removed", (i == (SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size() - 1)) ? "1" : "0");

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
                                responseBodyString = responseBody.string();
                                if (responseBodyString != null)
                                    responseBodyString = responseBodyString.trim();
                                if (!"success".equalsIgnoreCase(responseBodyString))
                                {
                                    gotErrorWhileUpdating1OfScheduleTimers = true;
                                    break;
                                }
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
                if ("error_no_rtc".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.time_not_synced_on_socket));
                else if ("error_another_timer_running".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.another_timer_already_running));
                else if ("allowed_only_when_appliance_off".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast("Update can be performed only when appliance is OFF");
                else
                {
                    if (!gotErrorWhileUpdating1OfScheduleTimers)
                    {
                        activityRefToCallMethods.showSuccessGreenToast("Successfully updated Todays Schedule/Timers");

                        // activityRefToCallMethods.resetStateMaintainerModel();                    // makes madeAnyChangeToRunSkipForTodaysScheduleTimer to false anyway
                        SmartWiFiSocketActivity.madeAnyChangeToRunSkipForTodaysScheduleTimer = false;
                    }else
                        activityRefToCallMethods.showErrorRedToast("Couldn't update todays schedule/timers. Please try again");
                }
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }

    private class BgTaskUpdateEnabledScheduleTimers extends AsyncTask<Void, Void, Exception>
    {
        private AsyncTaskCustomProgressDialog asyncTaskCustomProgressDialog = null;
        private boolean gotErrorWhileUpdating1OfScheduleTimers = false;
        private String responseBodyString;

        @Override
        protected void onPreExecute()
        {
            asyncTaskCustomProgressDialog = new AsyncTaskCustomProgressDialog(hostActivityContext, "Updating Enabled Schedule/Timers", "Please wait");
            asyncTaskCustomProgressDialog.show();
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            try
            {
                if (SmartWiFiSocketActivity.madeAnyChangeToEnabledOSFTScheduleTimers)
                    updateEnabledScheduleTimers(PojoScheduleTimerInfo.ScheduleTimerType.ONE_SHOT_FUTURE_TIMER);
                if (!gotErrorWhileUpdating1OfScheduleTimers && SmartWiFiSocketActivity.madeAnyChangeToEnabledFSScheduleTimers)
                    updateEnabledScheduleTimers(PojoScheduleTimerInfo.ScheduleTimerType.FUTURE_SCHEDULE);
                if (!gotErrorWhileUpdating1OfScheduleTimers && SmartWiFiSocketActivity.madeAnyChangeToEnabledRSScheduleTimers)
                    updateEnabledScheduleTimers(PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_SCHEDULE);
                if (!gotErrorWhileUpdating1OfScheduleTimers && SmartWiFiSocketActivity.madeAnyChangeToEnabledRTScheduleTimers)
                    updateEnabledScheduleTimers(PojoScheduleTimerInfo.ScheduleTimerType.RECURRING_TIMER);

                if (!gotErrorWhileUpdating1OfScheduleTimers)
                    updateEnabledScheduleTimers(null);          // just remove todays schedule timer so that we can perform a rescheduling of cron entries
            }catch(Exception e)
            {
                Log.e(LOG_TAG, e.getMessage() == null ? "null" : e.getMessage());
                e.printStackTrace();
                return e;
            }
            return null;
        }

        private void updateEnabledScheduleTimers(PojoScheduleTimerInfo.ScheduleTimerType scheduleTimerTypeToUpdateFor) throws Exception
        {
            List<PojoScheduleTimerInfo> specificScheduleTimerTypeSubset = new ArrayList<>();
            for (int i=0;i<SmartWiFiSocketActivity.allScheduleTimerInfoPojos.size();i++)
                if (SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i).getScheduleTimerType() == scheduleTimerTypeToUpdateFor)
                    specificScheduleTimerTypeSubset.add(SmartWiFiSocketActivity.allScheduleTimerInfoPojos.get(i));
            Collections.sort(specificScheduleTimerTypeSubset);

            HttpUrl httpUrl = HttpUrl.parse("http://" + SmartWiFiSocketActivity.fixedSocketResolvedIPAddress + ":" + SmartWiFiSocketActivity.fixedSocketResolvedPort + "/save_update_enabled_remove_schedule_timer_or_update_run_skip_for_today");
            if (httpUrl != null)
            {
                HttpUrl.Builder urlBuilder = httpUrl.newBuilder();

                Calendar calendar = Calendar.getInstance();
                String todays_schedule_timers = "_" + Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)) + Integer.toString(calendar.get(Calendar.MONTH) + 1) + Integer.toString(calendar.get(Calendar.YEAR));

                urlBuilder.addQueryParameter("call_type", "update_enabled_schedule_timers");
                urlBuilder.addQueryParameter("todays_schedule_timers", todays_schedule_timers);

                if (scheduleTimerTypeToUpdateFor == null)
                {
                    urlBuilder.addQueryParameter("truncate_before_update", "0");
                    urlBuilder.addQueryParameter("schedule_timer_filename", todays_schedule_timers);
                    urlBuilder.addQueryParameter("last_run_skip_update_or_todays_entry_saved_updated_removed", "1");

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
                            responseBodyString = responseBody.string();
                            if (responseBodyString != null)
                                responseBodyString = responseBodyString.trim();
                            if (!"success".equalsIgnoreCase(responseBodyString))
                                gotErrorWhileUpdating1OfScheduleTimers = true;
                        }
                    }
                }else
                {
                    urlBuilder.addQueryParameter("last_run_skip_update_or_todays_entry_saved_updated_removed", "0");
                    switch (scheduleTimerTypeToUpdateFor)
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

                    for (int i = 0; i < specificScheduleTimerTypeSubset.size(); i++)
                    {
                        urlBuilder.addQueryParameter("cron_mask_config_string", activityRefToCallMethods.convertStringToHexString(specificScheduleTimerTypeSubset.get(i).toCronMaskConfigString()));
                        urlBuilder.addQueryParameter("truncate_before_update", i == 0 ? "1" : "0");

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
                                responseBodyString = responseBody.string();
                                if (responseBodyString != null)
                                    responseBodyString = responseBodyString.trim();
                                if (!"success".equalsIgnoreCase(responseBodyString))
                                {
                                    gotErrorWhileUpdating1OfScheduleTimers = true;
                                    break;
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
                if ("error_no_rtc".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.time_not_synced_on_socket));
                else if ("error_another_timer_running".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast(getString(R.string.another_timer_already_running));
                else if ("allowed_only_when_appliance_off".equalsIgnoreCase(responseBodyString))
                    activityRefToCallMethods.showInfoGreyToast("Update can be performed only when appliance is OFF");
                else
                {
                    if (!gotErrorWhileUpdating1OfScheduleTimers)
                    {
                        activityRefToCallMethods.showSuccessGreenToast("Successfully updated Enabled Schedule/Timers");

                        // activityRefToCallMethods.resetStateMaintainerModel();           // makes all madeAnyChanges flags to false anyway
                        SmartWiFiSocketActivity.madeAnyChangeToEnabledOSFTScheduleTimers = false;
                        SmartWiFiSocketActivity.madeAnyChangeToEnabledFSScheduleTimers = false;
                        SmartWiFiSocketActivity.madeAnyChangeToEnabledRSScheduleTimers = false;
                        SmartWiFiSocketActivity.madeAnyChangeToEnabledRTScheduleTimers = false;
                    } else
                        activityRefToCallMethods.showErrorRedToast("Couldn't update Enabled schedule/timers. Please try again");
                }
            }else
            {
                exceptionOccurred.printStackTrace();
                activityRefToCallMethods.showErrorRedToast("Error occurred: " + (exceptionOccurred.getMessage() == null ? "No error description found!" : exceptionOccurred.getMessage()));
            }
        }
    }
}
