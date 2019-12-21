package in.co.unifytech.socket.pojos;

import android.content.Context;
import android.support.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import in.co.unifytech.R;
import mobi.upod.timedurationpicker.TimeDurationUtil;

public class PojoScheduleTimerInfo implements Comparable<PojoScheduleTimerInfo>
{
    public enum ScheduleTimerType { ONE_SHOT_CURRENT_TIMER, ONE_SHOT_FUTURE_TIMER, FUTURE_SCHEDULE, RECURRING_SCHEDULE, RECURRING_TIMER }
    public static final SimpleDateFormat sdfDateTimeDisplayFormat = new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault());
    private final SimpleDateFormat sdfRecurringTimeDisplayFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat sdfRecurringDateRangeDisplayFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat sdfYearOnlyFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

    //Delimiters
    private final char cronMaskOtherFieldsSeparator = '=';
    //private final char configStringForParsingAndCronConfigStringSeparator = '~';

    // For Cron Mask
    private final SimpleDateFormat sdfMinuteHourDayMonthFor_FS_OSFTCronMask = new SimpleDateFormat("mm_HH_dd_MM_*", Locale.getDefault());
    private final SimpleDateFormat sdfMinuteHourDayMonthRecurringDaysFor_RS_RTCronMask = new SimpleDateFormat("mm_HH_*_*_", Locale.getDefault());

    private ScheduleTimerType scheduleTimerType;
    private boolean daysToRunOn[] = {false, false, false, false, false, false, false};
    // 0th index = Monday
    private boolean beforeTimerStartState = false;
    private boolean afterTimerExpiresState = false;
    private boolean desiredScheduleState = false;

    private boolean isRunningNow = false;
    private long timerDurationSecs = 0, futureDateTime = 0, recurringTime = 0, recurringRangeStartDate = 0, recurringRangeEndDate = 0;
    // all long/DateTime objects are in seconds since Unix epoch (Jan 1, 1970 12am) with no timezone offsets

    private boolean isEnabled = true;
    private boolean scheduleSkippedForToday = false;

    public PojoScheduleTimerInfo()
    {
        // empty constructor object required for parsing other PojoScheduleTimerInfo
    }

    public PojoScheduleTimerInfo(boolean isEnabled, ScheduleTimerType scheduleTimerType, long recurringRangeStartDate, long recurringRangeEndDate, boolean[] daysToRunOn, long recurringTime, long futureDateTime, boolean desiredScheduleState, boolean beforeTimerStartState, long timerDurationSecs, boolean afterTimerExpiresState, boolean isRunningNow)
    {
        this.isEnabled = isEnabled;
        this.scheduleTimerType = scheduleTimerType;
        this.daysToRunOn = daysToRunOn;
        this.beforeTimerStartState = beforeTimerStartState;
        this.afterTimerExpiresState = afterTimerExpiresState;
        this.desiredScheduleState = desiredScheduleState;
        this.timerDurationSecs = timerDurationSecs;
        this.futureDateTime = futureDateTime;
        this.recurringTime = recurringTime;
        this.recurringRangeStartDate = recurringRangeStartDate;
        this.recurringRangeEndDate = recurringRangeEndDate;
        this.isRunningNow = isRunningNow;
    }

    public ScheduleTimerType getScheduleTimerType()
    {
        return scheduleTimerType;
    }

    public long getRecurringRangeStartDate()
    {
        return recurringRangeStartDate;
    }

    public long getRecurringRangeEndDate()
    {
        return recurringRangeEndDate;
    }

    public boolean[] getDaysToRunOn()
    {
        return daysToRunOn;
    }

    public boolean isRunOnMonday()
    {
        return daysToRunOn[0];
    }

    public boolean isRunOnTuesday()
    {
        return daysToRunOn[1];
    }

    public boolean isRunOnWednesday()
    {
        return daysToRunOn[2];
    }

    public boolean isRunOnFriday()
    {
        return daysToRunOn[3];
    }

    public boolean isRunOnThursday()
    {
        return daysToRunOn[4];
    }

    public boolean isRunOnSaturday()
    {
        return daysToRunOn[5];
    }

    public boolean isRunOnSunday()
    {
        return daysToRunOn[6];
    }

    public boolean getBeforeTimerStartState()
    {
        return beforeTimerStartState;
    }

    public boolean getAfterTimerExpiresState()
    {
        return afterTimerExpiresState;
    }

    public boolean getDesiredScheduleState()
    {
        return desiredScheduleState;
    }

    public long getTimerDurationSecs()
    {
        return timerDurationSecs;
    }

    public long getFutureDateTime()
    {
        return futureDateTime;
    }

    public long getRecurringTime()
    {
        return recurringTime;
    }

    public boolean isEnabled()
    {
        return isEnabled;
    }

    public void setIsEnabled(boolean isEnabled)
    {
        this.isEnabled = isEnabled;
    }

    public boolean isScheduleSkippedForToday()
    {
        return scheduleSkippedForToday;
    }

    public void setScheduleSkippedForToday(boolean scheduleSkippedForToday)
    {
        this.scheduleSkippedForToday = scheduleSkippedForToday;
    }

    public PojoScheduleTimerInfo parseCronMaskConfigString(String cronMaskConfigString, ScheduleTimerType scheduleTimerType)
    {
        String cronMaskConfigStringComponents[] = cronMaskConfigString.split(Character.toString(cronMaskOtherFieldsSeparator));
        for(int i=0;i<cronMaskConfigStringComponents.length;i++)
            cronMaskConfigStringComponents[i] = cronMaskConfigStringComponents[i].trim();
        switch (scheduleTimerType)
        {
            case ONE_SHOT_CURRENT_TIMER:
                // Sample: 1=1000=0
                return new PojoScheduleTimerInfo(true, ScheduleTimerType.ONE_SHOT_CURRENT_TIMER, 0, 0, null, 0, 0, false, getBooleanFrom0or1Char(cronMaskConfigStringComponents[0].charAt(0)), Long.valueOf(cronMaskConfigStringComponents[1]), getBooleanFrom0or1Char(cronMaskConfigStringComponents[2].charAt(0)), true);   // last parameter = running => always true because its is OSCT

            case ONE_SHOT_FUTURE_TIMER:
                // Earlier Sample   : OSFT;1234;1;1000;0
                // New Sample       : 110_22_11_12_*=2017=1=20000=0
                // 1st digit = 1/0 = enabled/disabled
                Calendar calendarFutureDateTime = Calendar.getInstance();
                try
                {
                    // skipping digit of isEnabled because that is handled below in constructor parameters
                    cronMaskConfigStringComponents[0] = cronMaskConfigStringComponents[0].substring(1);
                    calendarFutureDateTime.setTime(sdfMinuteHourDayMonthFor_FS_OSFTCronMask.parse(cronMaskConfigStringComponents[0]));
                    calendarFutureDateTime.set(Calendar.YEAR, Integer.valueOf(cronMaskConfigStringComponents[1]));
                } catch (ParseException e)
                {
                    e.printStackTrace();
                }
                return new PojoScheduleTimerInfo(cronMaskConfigString.charAt(0) == '1', ScheduleTimerType.ONE_SHOT_FUTURE_TIMER, 0, 0, null, 0, convertMilliSecsToSecs(calendarFutureDateTime.getTimeInMillis()), false, getBooleanFrom0or1Char(cronMaskConfigStringComponents[2].charAt(0)), Long.valueOf(cronMaskConfigStringComponents[3]), getBooleanFrom0or1Char(cronMaskConfigStringComponents[4].charAt(0)), false);  // last parameter => running/not running => retrieve this from latest state AsyncTasks

            case FUTURE_SCHEDULE:
                // Earlier Sample   : FS;1234;1
                // New Sample       : 110_22_11_12_*=2017=1
                // 1st digit = 1/0 = enabled/disabled
                calendarFutureDateTime = Calendar.getInstance();
                try
                {
                    // skipping digit of isEnabled because that is handled below in constructor parameters
                    cronMaskConfigStringComponents[0] = cronMaskConfigStringComponents[0].substring(1);
                    calendarFutureDateTime.setTime(sdfMinuteHourDayMonthFor_FS_OSFTCronMask.parse(cronMaskConfigStringComponents[0]));
                    calendarFutureDateTime.set(Calendar.YEAR, Integer.valueOf(cronMaskConfigStringComponents[1]));
                } catch (ParseException e)
                {
                    e.printStackTrace();
                }
                return new PojoScheduleTimerInfo(cronMaskConfigString.charAt(0) == '1', ScheduleTimerType.FUTURE_SCHEDULE, 0, 0, null, 0, convertMilliSecsToSecs(calendarFutureDateTime.getTimeInMillis()), getBooleanFrom0or1Char(cronMaskConfigStringComponents[2].charAt(0)), false, 0, false, false);

            case RECURRING_SCHEDULE:
                // Earlier Sample : RS;1000;2000;0100111;100;1
                // New Sample :     110_22_*_*_0,1,2=123456=21345678=1
                // 1st digit = 1/0 = enabled/disabled
                Calendar calendarRecurringTime = Calendar.getInstance();
                try
                {
                    // skipping digit of isEnabled because that is handled below in constructor parameters
                    cronMaskConfigStringComponents[0] = cronMaskConfigStringComponents[0].substring(1);
                    calendarRecurringTime.setTime(sdfMinuteHourDayMonthRecurringDaysFor_RS_RTCronMask.parse(cronMaskConfigStringComponents[0].substring(0, cronMaskConfigStringComponents[0].lastIndexOf('_')+1)));
                } catch (ParseException e)
                {
                    e.printStackTrace();
                }
                // PojoScheduleTimerInfo(ScheduleTimerType scheduleTimerType, long recurringRangeStartDate, long recurringRangeEndDate, boolean[] daysToRunOn, long recurringTime, long futureDateTime, boolean desiredScheduleState, boolean beforeTimerStartState, long timerDurationSecs, boolean afterTimerExpiresState, boolean isRunningNow)
                return new PojoScheduleTimerInfo(cronMaskConfigString.charAt(0) == '1', ScheduleTimerType.RECURRING_SCHEDULE, Long.valueOf(cronMaskConfigStringComponents[1]), Long.valueOf(cronMaskConfigStringComponents[2]), getDaysToRunOnFromCronString(cronMaskConfigStringComponents[0].substring(cronMaskConfigStringComponents[0].lastIndexOf('_')+1)), convertMilliSecsToSecs(calendarRecurringTime.getTimeInMillis()), 0, getBooleanFrom0or1Char(cronMaskConfigStringComponents[3].charAt(0)), false, 0, false, false);

            case RECURRING_TIMER:
                // Earlier Sample   : RT;1000;2000;0100111;100;1;1000;0
                // New Sample       : 110_22_*_*_0,1,2=123456=21345678=1=20000=0
                // 1st digit = 1/0 = enabled/disabled
                calendarRecurringTime = Calendar.getInstance();
                try
                {
                    // skipping digit of isEnabled because that is handled below in constructor parameters
                    cronMaskConfigStringComponents[0] = cronMaskConfigStringComponents[0].substring(1);
                    calendarRecurringTime.setTime(sdfMinuteHourDayMonthRecurringDaysFor_RS_RTCronMask.parse(cronMaskConfigStringComponents[0].substring(0, cronMaskConfigStringComponents[0].lastIndexOf('_')+1)));
                } catch (ParseException e)
                {
                    e.printStackTrace();
                }
                // PojoScheduleTimerInfo(ScheduleTimerType scheduleTimerType, long recurringRangeStartDate, long recurringRangeEndDate, boolean[] daysToRunOn, long recurringTime, long futureDateTime, boolean desiredScheduleState, boolean beforeTimerStartState, long timerDurationSecs, boolean afterTimerExpiresState, boolean isRunningNow)
                return new PojoScheduleTimerInfo(cronMaskConfigString.charAt(0) == '1', ScheduleTimerType.RECURRING_TIMER, Long.valueOf(cronMaskConfigStringComponents[1]), Long.valueOf(cronMaskConfigStringComponents[2]), getDaysToRunOnFromCronString(cronMaskConfigStringComponents[0].substring(cronMaskConfigStringComponents[0].lastIndexOf('_')+1)), convertMilliSecsToSecs(calendarRecurringTime.getTimeInMillis()), 0, false, getBooleanFrom0or1Char(cronMaskConfigStringComponents[3].charAt(0)), Long.valueOf(cronMaskConfigStringComponents[4]), getBooleanFrom0or1Char(cronMaskConfigStringComponents[5].charAt(0)), false);    // last parameter => running/not running => retrieve this from latest state AsyncTasks
        }

        return null;
    }

    /*public PojoScheduleTimerInfo parseConfigString(String configString)
    {
        String configStringComponents[] = configString.split(Character.toString(displaySeparatorChar));
        if(configStringComponents[0].trim().equalsIgnoreCase("OSCT"))
        {
            // Sample: OSCT;1;1000;0
            return new PojoScheduleTimerInfo(ScheduleTimerType.ONE_SHOT_CURRENT_TIMER, 0, 0, null, 0, 0, false, getBooleanFrom0or1Char(configStringComponents[1].trim().charAt(0)), Long.valueOf(configStringComponents[2]), getBooleanFrom0or1Char(configStringComponents[configStringComponents.length-1].trim().charAt(0)), true);   // last parameter = running => always true because its is OSCT
        }else if(configStringComponents[0].trim().startsWith("OSFT"))
        {
            // last 1/0 indicates running/not running
            // Sample : OSFT;1234;1;1000;0;1
            return new PojoScheduleTimerInfo(ScheduleTimerType.ONE_SHOT_FUTURE_TIMER, 0, 0, null, 0, Long.valueOf(configStringComponents[1]), false, getBooleanFrom0or1Char(configStringComponents[2].trim().charAt(0)), Long.valueOf(configStringComponents[3]), getBooleanFrom0or1Char(configStringComponents[4].trim().charAt(0)), getBooleanFrom0or1Char(configStringComponents[configStringComponents.length-1].trim().charAt(0)));
        }else if(configStringComponents[0].trim().equalsIgnoreCase("FS"))
        {
            // Sample : FS;1234;1
            return new PojoScheduleTimerInfo(ScheduleTimerType.FUTURE_SCHEDULE, 0, 0, null, 0, Long.valueOf(configStringComponents[1]), getBooleanFrom0or1Char(configStringComponents[configStringComponents.length-1].trim().charAt(0)), false, 0, false, false);
        }else if(configStringComponents[0].trim().equalsIgnoreCase("RS"))
        {
            // Sample : RS;1000;2000;0100111;100;1
            return new PojoScheduleTimerInfo(ScheduleTimerType.RECURRING_SCHEDULE, Long.valueOf(configStringComponents[1]), Long.valueOf(configStringComponents[2]), getBooleanArrayFrom0or1String(configStringComponents[3]), Long.valueOf(configStringComponents[4]), 0, getBooleanFrom0or1Char(configStringComponents[configStringComponents.length-1].trim().charAt(0)), false, 0, false, false);
        }else if(configStringComponents[0].trim().equalsIgnoreCase("RT"))
        {
            // last 1/0 indicates running/not running
            // Sample : RT;1000;2000;0100111;100;1;1000;0;0
            return new PojoScheduleTimerInfo(ScheduleTimerType.RECURRING_TIMER, Long.valueOf(configStringComponents[1]), Long.valueOf(configStringComponents[2]), getBooleanArrayFrom0or1String(configStringComponents[3]), Long.valueOf(configStringComponents[4]), 0, false, getBooleanFrom0or1Char(configStringComponents[5].trim().charAt(0)), Long.valueOf(configStringComponents[6]), getBooleanFrom0or1Char(configStringComponents[7].trim().charAt(0)), getBooleanFrom0or1Char(configStringComponents[configStringComponents.length-1].trim().charAt(0)));
        }
        return null;
    }

    public String toConfigString()
    {
        return toConfigStringForParsingBack()+(scheduleTimerType==ScheduleTimerType.ONE_SHOT_CURRENT_TIMER ? "" : (configStringForParsingAndCronConfigStringSeparator+ toCronMaskConfigString()));
    }

    private String toConfigStringForParsingBack()
    {
        StringBuffer strBuffer = new StringBuffer();
        switch (scheduleTimerType)
        {
            case ONE_SHOT_CURRENT_TIMER:
                strBuffer.append("OSCT").append(displaySeparatorChar).append(getOor1FromBoolean(beforeTimerStartState)).append(displaySeparatorChar).append(timerDurationSecs).append(displaySeparatorChar).append(getOor1FromBoolean(afterTimerExpiresState));
                break;

            case ONE_SHOT_FUTURE_TIMER:
                strBuffer.append("OSFT").append(displaySeparatorChar).append(futureDateTime).append(displaySeparatorChar).append(getOor1FromBoolean(beforeTimerStartState)).append(displaySeparatorChar).append(timerDurationSecs).append(displaySeparatorChar).append(getOor1FromBoolean(afterTimerExpiresState));
                break;

            case FUTURE_SCHEDULE:
                strBuffer.append("FS").append(displaySeparatorChar).append(futureDateTime).append(displaySeparatorChar).append(getOor1FromBoolean(desiredScheduleState));
                break;

            case RECURRING_SCHEDULE:
                strBuffer.append("RS").append(displaySeparatorChar).append(recurringRangeStartDate).append(displaySeparatorChar).append(recurringRangeEndDate).append(displaySeparatorChar).append(get0or1StringFromDaysToRunOn(daysToRunOn)).append(displaySeparatorChar).append(recurringTime).append(displaySeparatorChar).append(desiredScheduleState);
                break;

            case RECURRING_TIMER:
                strBuffer.append("RT").append(displaySeparatorChar).append(recurringRangeStartDate).append(displaySeparatorChar).append(recurringRangeEndDate).append(displaySeparatorChar).append(get0or1StringFromDaysToRunOn(daysToRunOn)).append(displaySeparatorChar).append(recurringTime).append(displaySeparatorChar).append(getOor1FromBoolean(beforeTimerStartState)).append(displaySeparatorChar).append(timerDurationSecs).append(displaySeparatorChar).append(getOor1FromBoolean(afterTimerExpiresState));
                break;
        }
        return strBuffer.toString();
    }*/

    public String toCronMaskConfigString()
    {
        StringBuilder strBuffer = new StringBuilder();
        switch (scheduleTimerType)
        {
            case ONE_SHOT_CURRENT_TIMER:
                // 1=1000=0
                strBuffer.append(getOor1FromBoolean(beforeTimerStartState)).append(cronMaskOtherFieldsSeparator).append(timerDurationSecs).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(afterTimerExpiresState));
                break;

            case ONE_SHOT_FUTURE_TIMER:
                // 1st digit = 1/0 = enabled/disabled
                // 110_22_11_12_*=2017=1=20000=0
                strBuffer.append(getOor1FromBoolean(isEnabled)).append(sdfMinuteHourDayMonthFor_FS_OSFTCronMask.format(new Date(convertSecsToMilliSecs(futureDateTime)))).append(cronMaskOtherFieldsSeparator).append(sdfYearOnlyFormat.format(new Date(convertSecsToMilliSecs(futureDateTime)))).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(beforeTimerStartState)).append(cronMaskOtherFieldsSeparator).append(timerDurationSecs).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(afterTimerExpiresState));  // * for any day
                break;

            case FUTURE_SCHEDULE:
                // 1st digit = 1/0 = enabled/disabled
                // 110_22_11_12_*=2017=1
                strBuffer.append(getOor1FromBoolean(isEnabled)).append(sdfMinuteHourDayMonthFor_FS_OSFTCronMask.format(new Date(convertSecsToMilliSecs(futureDateTime)))).append(cronMaskOtherFieldsSeparator).append(sdfYearOnlyFormat.format(new Date(convertSecsToMilliSecs(futureDateTime)))).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(desiredScheduleState));  // * for any day
                break;

            case RECURRING_SCHEDULE:
                // FOR NODEMCU CRON => 0=SUN, 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT
                // 1st digit = 1/0 = enabled/disabled
                // 110_22_*_*_0,1,2=123456=21345678=1
                strBuffer.append(getOor1FromBoolean(isEnabled)).append(sdfMinuteHourDayMonthRecurringDaysFor_RS_RTCronMask.format(new Date(convertSecsToMilliSecs(recurringTime)))).append(getDaysToRunOnCronString(daysToRunOn)).append(cronMaskOtherFieldsSeparator).append(recurringRangeStartDate).append(cronMaskOtherFieldsSeparator).append(recurringRangeEndDate).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(desiredScheduleState));
                break;

            case RECURRING_TIMER:
                // FOR NODEMCU CRON => 0=SUN, 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT
                // 1st digit = 1/0 = enabled/disabled
                // 110_22_*_*_0,1,2=123456=21345678=1=20000=0
                strBuffer.append(getOor1FromBoolean(isEnabled)).append(sdfMinuteHourDayMonthRecurringDaysFor_RS_RTCronMask.format(new Date(convertSecsToMilliSecs(recurringTime)))).append(getDaysToRunOnCronString(daysToRunOn)).append(cronMaskOtherFieldsSeparator).append(recurringRangeStartDate).append(cronMaskOtherFieldsSeparator).append(recurringRangeEndDate).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(beforeTimerStartState)).append(cronMaskOtherFieldsSeparator).append(timerDurationSecs).append(cronMaskOtherFieldsSeparator).append(getOor1FromBoolean(afterTimerExpiresState));
                break;
        }
        return strBuffer.toString();
    }

    private String getDaysToRunOnCronString(boolean[] daysToRunOn)
    {
        boolean allDays = true;

        for(boolean b:daysToRunOn)
            if (!b)
            {
                allDays = false;
                break;
            }

        if (allDays)
            return "*";
        else
        {
            StringBuilder stringBuffer = new StringBuilder();
            if (daysToRunOn[0])
                stringBuffer.append("1,");      // MON
            if (daysToRunOn[1])
                stringBuffer.append("2,");      // TUE
            if (daysToRunOn[2])
                stringBuffer.append("3,");      // WED
            if (daysToRunOn[3])
                stringBuffer.append("4,");      // THU
            if (daysToRunOn[4])
                stringBuffer.append("5,");      // FRI
            if (daysToRunOn[5])
                stringBuffer.append("6,");      // SAT
            if (daysToRunOn[6])
                stringBuffer.append("0");       // SUN
            return stringBuffer.toString().replaceAll("^,+", "").replaceAll(",+$", "");
        }
    }

    private boolean[] getDaysToRunOnFromCronString(String cronString)
    {
        if (cronString.equalsIgnoreCase("*"))
            return new boolean[]{true, true, true, true, true, true, true};
        else
        {
            String[] temp = cronString.split(",");
            boolean[] daysToRunOn = new boolean[7];

            for (String s : temp)
            {
                if (s.equalsIgnoreCase("1"))    // MON
                    daysToRunOn[0] = true;
                if (s.equalsIgnoreCase("2"))    // TUE
                    daysToRunOn[1] = true;
                if (s.equalsIgnoreCase("3"))    // WED
                    daysToRunOn[2] = true;
                if (s.equalsIgnoreCase("4"))    // THU
                    daysToRunOn[3] = true;
                if (s.equalsIgnoreCase("5"))    // FRI
                    daysToRunOn[4] = true;
                if (s.equalsIgnoreCase("6"))    // SAT
                    daysToRunOn[5] = true;
                if (s.equalsIgnoreCase("0"))    // SUN
                    daysToRunOn[6] = true;
            }

            return daysToRunOn;
        }
    }

    private String toDisplayString()
    {
        final char displaySeparatorChar = ';';
        StringBuilder strBuffer = new StringBuilder();
        switch (scheduleTimerType)
        {
            case ONE_SHOT_CURRENT_TIMER:
                strBuffer.append(getReadableBeforeTimerState(null)).append(displaySeparatorChar).append(getReadableTimerDuration()).append(displaySeparatorChar).append(getReadableAfterTimerState(null)).append(displaySeparatorChar).append(getOor1FromBoolean(isRunningNow));
                break;

            case ONE_SHOT_FUTURE_TIMER:
                strBuffer.append(getReadableFutureDateTime()).append(displaySeparatorChar).append(getReadableBeforeTimerState(null)).append(displaySeparatorChar).append(getReadableTimerDuration()).append(displaySeparatorChar).append(getReadableAfterTimerState(null)).append(displaySeparatorChar).append(getOor1FromBoolean(isRunningNow));
                break;

            case FUTURE_SCHEDULE:
                strBuffer.append(getReadableFutureDateTime()).append(displaySeparatorChar).append(getReadableDesiredScheduleState(null));
                break;

            case RECURRING_SCHEDULE:
                strBuffer.append(getReadableRecurringRangeStartDate()).append(displaySeparatorChar).append(getReadableRecurringRangeEndDate()).append(get0or1StringFromDaysToRunOn(daysToRunOn)).append(displaySeparatorChar).append(getReadableRecurringTime()).append(getReadableDesiredScheduleState(null));
                break;

            case RECURRING_TIMER:
                strBuffer.append(getReadableRecurringRangeStartDate()).append(displaySeparatorChar).append(getReadableRecurringRangeEndDate()).append(get0or1StringFromDaysToRunOn(daysToRunOn)).append(displaySeparatorChar).append(getReadableRecurringTime()).append(displaySeparatorChar).append(getReadableBeforeTimerState(null)).append(displaySeparatorChar).append(getReadableTimerDuration()).append(displaySeparatorChar).append(getReadableAfterTimerState(null)).append(displaySeparatorChar).append(getOor1FromBoolean(isRunningNow));
                break;
        }
        return strBuffer.toString();
    }

    public String toString()
    {
        return toDisplayString();
    }

    private char getOor1FromBoolean(boolean b)
    {
        return b?'1':'0';
    }

    private boolean getBooleanFrom0or1Char(char c)
    {
        return c=='1';
    }

    public boolean isRunningNow()
    {
        return isRunningNow;
    }

    public String getReadableScheduleTimerType(Context appContext)
    {
        return appContext.getResources().getStringArray(R.array.all_schedule_timer_types)[scheduleTimerType.ordinal()];
    }

    public String getReadableBeforeTimerState(Context appContext)
    {
        if(appContext==null)
            return beforeTimerStartState?"ON":"OFF";
        else
            return beforeTimerStartState?appContext.getString(R.string.stateON):appContext.getString(R.string.stateOFF);
    }

    public String getReadableAfterTimerState(Context appContext)
    {
        if (appContext == null)
            return afterTimerExpiresState?"ON":"OFF";
        else
            return afterTimerExpiresState?appContext.getString(R.string.stateON):appContext.getString(R.string.stateOFF);
    }

    public String getReadableDesiredScheduleState(Context appContext)
    {
        if(appContext == null)
            return desiredScheduleState?"ON":"OFF";
        else
            return desiredScheduleState?appContext.getString(R.string.stateON):appContext.getString(R.string.stateOFF);
    }

    public String getReadableRecurringRangeStartDate()
    {
        return sdfRecurringDateRangeDisplayFormat.format(new Date(convertSecsToMilliSecs(recurringRangeStartDate)));
    }

    public String getReadableRecurringRangeEndDate()
    {
        return sdfRecurringDateRangeDisplayFormat.format(new Date(convertSecsToMilliSecs(recurringRangeEndDate)));
    }

    public String getReadableRecurringTime()
    {
        return sdfRecurringTimeDisplayFormat.format(new Date(convertSecsToMilliSecs(recurringTime)));
    }

    public String getReadableFutureDateTime()
    {
        return sdfDateTimeDisplayFormat.format(new Date(convertSecsToMilliSecs(futureDateTime)));    // converting secs to milli secs for Date object
    }

    public String getReadableTimerDuration()
    {
        String temp[] = TimeDurationUtil.formatHoursMinutesSeconds(convertSecsToMilliSecs(timerDurationSecs)).split(":"); // converting secs to milli secs for TimeDurationUtil
        return String.format(Locale.getDefault(), "%02dh %02dm %02ds", Integer.valueOf(temp[0]), Integer.valueOf(temp[1]), Integer.valueOf(temp[2]));
    }

    private String get0or1StringFromDaysToRunOn(boolean[] daysToRunOn)
    {
        StringBuilder stringBuffer = new StringBuilder();
        for(Boolean day:daysToRunOn)
            stringBuffer.append(day?'1':'0');
        return stringBuffer.toString();
    }

    /*private boolean[] getBooleanArrayFrom0or1String(String strDaysToRunOn)
    {
        boolean boolArrDaysToRunOn[] = new boolean[7];
        if(strDaysToRunOn.length()==7)
        {
            for(int i=0;i<boolArrDaysToRunOn.length;i++)
                boolArrDaysToRunOn[i] = getBooleanFrom0or1Char(strDaysToRunOn.charAt(i));
        }
        return boolArrDaysToRunOn;
    }*/

    public void setIsRunningNow(boolean runningNow)
    {
        isRunningNow = runningNow;
    }

    private long convertSecsToMilliSecs(long secs)
    {
        return secs * 1000;
    }

    private long convertMilliSecsToSecs(long milliSecs)
    {
        return milliSecs / 1000;
    }

    @Override
    public int compareTo(@NonNull PojoScheduleTimerInfo obj)
    {
        if (getScheduleTimerType().ordinal() == obj.getScheduleTimerType().ordinal())
        {
            switch (getScheduleTimerType())
            {
                case ONE_SHOT_FUTURE_TIMER:
                case FUTURE_SCHEDULE:
                    return (int) (getFutureDateTime() - obj.getFutureDateTime());

                case RECURRING_TIMER:
                case RECURRING_SCHEDULE:
                    if (getRecurringRangeStartDate() == obj.getRecurringRangeStartDate())
                    {
                        if (getRecurringRangeEndDate() == obj.getRecurringRangeEndDate())
                        {
                            return (int) (getRecurringTime() - obj.getRecurringTime());
                        }else
                            return (int) (getRecurringRangeEndDate() - obj.getRecurringRangeEndDate());
                    }else
                        return (int) (getRecurringRangeStartDate() - obj.getRecurringRangeStartDate());
            }
        }else
            return getScheduleTimerType().ordinal()-obj.getScheduleTimerType().ordinal();

        return 0;
    }
}