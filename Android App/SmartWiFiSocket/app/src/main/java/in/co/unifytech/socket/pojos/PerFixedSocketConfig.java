package in.co.unifytech.socket.pojos;

import android.support.annotation.NonNull;

public class PerFixedSocketConfig implements Comparable<PerFixedSocketConfig>
{
    private final String MDNSHostName;
    private String externalWiFiSSID;
    private String internetModeConfiguredBy = "";
    private String timezoneDetails;
    private String internetModeDomainNameForSocket;
    private boolean isInternetModeConfigured;
    // private long internetModeLastUpdated = 0;
    private int socketSoftwareVersion = 0;
    private boolean rememberPowerCuts = false;

    public PerFixedSocketConfig(String MDNSHostName, String externalWiFiSSID, String timezoneDetails)
    {
        this.MDNSHostName = MDNSHostName;
        this.externalWiFiSSID = externalWiFiSSID;
        this.timezoneDetails = timezoneDetails;
        // inlining value as false because we add new PerFixedSocketConfig objects only when we add new Sockets or we search existing Fixed Sockets
        this.isInternetModeConfigured = false;
    }

    public String getMDNSHostName()
    {
        return MDNSHostName;
    }

    /*public void setMDNSHostName(String MDNSHostName)
    {
        this.MDNSHostName = MDNSHostName;
    }*/

    public String getExternalWiFiSSID()
    {
        return externalWiFiSSID;
    }

    public void setExternalWiFiSSID(String externalWiFiSSID)
    {
        this.externalWiFiSSID = externalWiFiSSID;
    }

    public String getInternetModeConfiguredBy()
    {
        return internetModeConfiguredBy;
    }

    public void setInternetModeConfiguredBy(String internetModeConfiguredBy)
    {
        this.internetModeConfiguredBy = internetModeConfiguredBy;
    }

    public String getTimezoneDetails()
    {
        return timezoneDetails;
    }

    public String getStoredTZID()
    {
        return timezoneDetails.substring(1, timezoneDetails.indexOf('='));
    }

    public void setTimezoneDetails(String timezoneDetails)
    {
        this.timezoneDetails = timezoneDetails;
    }

    public void setInternetModeConfigured(boolean internetModeConfigured)
    {
        isInternetModeConfigured = internetModeConfigured;
    }

    public boolean isInternetModeConfigured()
    {
        return isInternetModeConfigured;
    }

    @Override
    public int compareTo(@NonNull PerFixedSocketConfig obj)
    {
        return getMDNSHostName().compareTo(obj.getMDNSHostName());
    }

    public String toString()
    {
        return MDNSHostName;
    }

    /*public long getInternetModeLastUpdated()
    {
        return internetModeLastUpdated;
    }

    public void setInternetModeLastUpdated(long internetModeLastUpdated)
    {
        this.internetModeLastUpdated = internetModeLastUpdated;
    }*/

    public int getSocketSoftwareVersion()
    {
        return socketSoftwareVersion;
    }

    public void setSocketSoftwareVersion(int socketSoftwareVersion)
    {
        this.socketSoftwareVersion = socketSoftwareVersion;
    }

    public String getInternetModeDomainNameForSocket()
    {
        return internetModeDomainNameForSocket;
    }

    public void setInternetModeDomainNameForSocket(String internetModeDomainNameForSocket)
    {
        this.internetModeDomainNameForSocket = internetModeDomainNameForSocket;
    }

    public boolean getRememberPowerCuts()
    {
        return rememberPowerCuts;
    }

    public void setRememberPowerCuts(boolean rememberPowerCuts)
    {
        this.rememberPowerCuts = rememberPowerCuts;
    }
}
