-- this function creates a meta table over the given parameter table making it read only by overloading method __newindex
function protect(tbl)
    return setmetatable({}, {
            __index = tbl,
            __newindex = function(t, key, value)
                error("attempting to change constant " .. tostring(key) .. " to " .. tostring(value), 2)
            end
        })
end

FILENAMES = protect({
        -- Action/State Indicator Files
        INITIAL_SETUP_DONE = "initial_setup_done",
        IS_INTERNET_MODE_CONFIGURED = "is_internet_mode_configured",
        
        -- On Boot Action/State Indicator Files
        SNTP_SYNC_DONE_ONCE_ON_BOOT = "sntp_sync_done_once_on_boot",
        
        -- Config Persistence
        MDNS_HOSTNAME = "mdns_hostname"
    })

AP_NETWORK_SETTINGS = protect({        
        DEFAULT_AP_SELF_IP = "192.168.10.1",
        DEFAULT_AP_NETMASK = "255.255.255.0",
        DEFAULT_AP_GATEWAY = "192.168.10.1",
        
        DEFAULT_AP_DHCP_START_IP = "192.168.10.2"
    })

GPIO_PINS = protect({        
        TRI_COLOR_SETUP_LED_RED_OPIN = 0,
        TRI_COLOR_SETUP_LED_GREEN_OPIN = 1,
        TRI_COLOR_SETUP_LED_BLUE_OPIN = 2,
--        TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN = 10,
--        TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN = 11,
--        TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN = 12,
        FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN = 6,
        RELAY_OPIN = 3,
    })

NETWORK_SERVICE_SETTINGS = protect({
        SETUP_WEB_SERVER_PORT = 80,
        EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode
    })

WIFI_AP_DEFAULT_SETTINGS = protect({
        DEFAULT_AP_SETUP_SSID = 'Setup Smart WiFi Socket',
        --DEFAULT_AP_SETUP_PWD = 'S/\/\@rT\/\/iFiS0cKeT9911:)',
        DEFAULT_AP_SETUP_PWD = 'Sm@rTWiFiS0cKeT:)',
        DEFAULT_AP_AUTH_MODE = wifi.WPA_WPA2_PSK,
        DEFAULT_AP_CHANNEL = 6,
        DEFAULT_AP_IS_SSID_HIDDEN = false,
        DEFAULT_AP_MAX_CONN = 4,
        DEFAULT_AP_BEACON_INTERVAL = 100,
        DEFAULT_AP_SAVE_CFG_TO_FLASH = true
    })

WIFI_STA_DEFAULT_SETTINGS = protect({
        DEFAULT_STA_AUTO_CONNECT = false,
        DEFAULT_STA_SAVE_CFG_TO_FLASH = true
    })

local CONSTANTS = protect({
        FILENAMES = FILENAMES,
        AP_NETWORK_SETTINGS = AP_NETWORK_SETTINGS,
        GPIO_PINS = GPIO_PINS,
        NETWORK_SERVICE_SETTINGS = NETWORK_SERVICE_SETTINGS,
        WIFI_AP_DEFAULT_SETTINGS = WIFI_AP_DEFAULT_SETTINGS,
        WIFI_STA_DEFAULT_SETTINGS = WIFI_STA_DEFAULT_SETTINGS
    })

return CONSTANTS