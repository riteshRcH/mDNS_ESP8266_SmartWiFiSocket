TRI_COLOR_SETUP_LED_RED_OPIN = 1
TRI_COLOR_SETUP_LED_GREEN_OPIN = 2
TRI_COLOR_SETUP_LED_BLUE_OPIN = 3
TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN = 0
-- Pin 4 is used by Active Low onboard Blue LED for UART TX
TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN = 5
TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN = 6
--FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN = 7 -- Used directly in init.lua
RELAY_OPIN = 7

--Used only once in SmartWiFiSocket_FixedMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and FixedMode, moved the code to each of the files instead of Common Utils so that mem is saved for PortablMode
--local toggleTriColorRed = false
--local tmrBlinkTriColorRed = tmr.create()
--tmrBlinkTriColorRed:register(500, tmr.ALARM_AUTO, function(timer)
--        toggleTriColorRed = not toggleTriColorRed
--        gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, toggleTriColorRed and gpio.HIGH or gpio.LOW)
--    end)

--Used only once in SmartWiFiSocket_PortablMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and PortablMode moved the code to each of the files instead of Common Utils so that mem is saved for FixedMode
--local toggleTriColorBlue = false
--local tmrBlinkTriColorBlue = tmr.create()
--tmrBlinkTriColorBlue:register(500, tmr.ALARM_AUTO, function(timer)
--        toggleTriColorBlue = not toggleTriColorBlue
--        gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, toggleTriColorBlue and gpio.HIGH or gpio.LOW)
--    end)

--Used only once in SmartWiFiSocket_FixedMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and FixedMode moved the code to each of the files instead of Common Utils so that mem is saved for PortablMode
--function init_wifi_mode(wifi_mode)
--    -- wifi mode config taken from https://iotbytes.wordpress.com/wifi-configuration-on-nodemcu/
    
--    wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
--    while wifi.setmode(wifi_mode) ~= wifi_mode do
--    end
--end

--function remove_action_result_indicator_files_on_boot ()                  -- moved into FixedMode as it was called only once
----    local SNTP_SYNC_DONE_ONCE_ON_BOOT = "sntp_sync_done_once_on_boot"
--    file.remove("sntp_sync_done_once_on_boot")
--end

--function unregister_all_wifi_eventmon_callbacks ()
--    wifi.eventmon.unregister(wifi.eventmon.STA_GOT_IP)
--    wifi.eventmon.unregister(wifi.eventmon.STA_DISCONNECTED)
--    wifi.eventmon.unregister(wifi.eventmon.STA_CONNECTED)
--    wifi.eventmon.unregister(wifi.eventmon.STA_AUTHMODE_CHANGE)
--    wifi.eventmon.unregister(wifi.eventmon.STA_DHCP_TIMEOUT)
--    wifi.eventmon.unregister(wifi.eventmon.AP_STACONNECTED)
--    wifi.eventmon.unregister(wifi.eventmon.AP_STADISCONNECTED)
--    wifi.eventmon.unregister(wifi.eventmon.AP_PROBEREQRECVED)
--    wifi.eventmon.unregister(wifi.eventmon.WIFI_MODE_CHANGED)
--end

-- moved into init.lua as it was called only once
--function factory_reset_redo_initial_setup()
    -- Action/State Indicator Files
--    local INITIAL_SETUP_DONE = "initial_setup_done"
--    local IS_INTERNET_MODE_CONFIGURED = "is_internet_mode_configured"
    
    -- On Boot Action/State Indicator Files
--    local SNTP_SYNC_DONE_ONCE_ON_BOOT = "sntp_sync_done_once_on_boot"
    
    -- Config Persistence
--    local MDNS_HOSTNAME = "mdns_hostname"
    
--    local ONE_SHOT_FUTURE_TIMERS = "OSFT"
--    local FUTURE_SCHEDULES = "FS"
--    local RECURRING_SCHEDULES = "RS"
--    local RECURRING_TIMERS = "RT"
    
--    file.remove("initial_setup_done")
--    file.remove("mdns_hostname")
--    file.remove("is_internet_mode_configured")
--    file.remove("sntp_sync_done_once_on_boot")
    
--    file.remove("OSFT")
--    file.remove("FS")
--    file.remove("RS")
--    file.remove("RT")

--    node.restart()
--end

--function init_factory_reset_redo_initial_setup_interrupt()
--    gpio.mode(FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN, gpio.INT)
    
--    gpio.trig(FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN, "up", function(level, when)
--            tmr.create():alarm(3200, tmr.ALARM_SINGLE, function(t)
--            if gpio.read(FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN) == 1 then
--                factory_reset_redo_initial_setup()
--            end
--        end)
--    end)
--end

function gpio_write_setup_led_pins(red, green, blue)
    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, red and gpio.HIGH or gpio.LOW)
    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, green and gpio.HIGH or gpio.LOW)
    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, blue and gpio.HIGH or gpio.LOW)
end

function gpio_write_relay_indicator_led_pins(red, green, blue)
    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, red and gpio.HIGH or gpio.LOW)
    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, green and gpio.HIGH or gpio.LOW)
    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, blue and gpio.HIGH or gpio.LOW)
end

--Used only once in this file itself hence moved it into reset_gpio_relay_pin
--function indicate_relay_off_with_no_timer()
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.LOW)
--end

--Used only once in SmartWiFiSocket_Setup.lua hence moved it into that file
--function indicate_initial_setup_not_done()
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
--end

--function init_tri_color_led_pins()
--    gpio.mode(TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.OUTPUT)
--    gpio.mode(TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.OUTPUT)
--    gpio.mode(TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.OUTPUT)
    
--    indicate_relay_off_with_no_timer()
    
--    gpio.mode(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.OUTPUT)
--    gpio.mode(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.OUTPUT)
--    gpio.mode(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.OUTPUT)
    
--    indicate_initial_setup_not_done()
--end

--Used only once in SmartWiFiSocket_FixedMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and FixedMode, moved the code to each of the files instead of Common Utils so that mem is saved for PortablMode
--function indicate_trying_connect_to_external_socket_host_wifi_start_timer()
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
    
--    tmrBlinkTriColorRed:start()
--end

--Used only once in SmartWiFiSocket_PortablMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and PortablMode moved the code to each of the files instead of Common Utils so that mem is saved for FixedMode
--function indicate_trying_to_setup_portable_socket_start_timer()
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
    
--    tmrBlinkTriColorBlue:start()
--end

--Used only once in SmartWiFiSocket_FixedMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and FixedMode, moved the code to each of the files instead of Common Utils so that mem is saved for PortablMode
--function indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
--    tmrBlinkTriColorRed:stop()
    
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
--end

--Used only once in SmartWiFiSocket_PortablMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and PortablMode moved the code to each of the files instead of Common Utils so that mem is saved for FixedMode
--function indicate_trying_to_setup_portable_socket_stop_timer()
--    tmrBlinkTriColorBlue:stop()
    
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
--end

--Used only in SmartWiFiSocket_FixedMode.lua and once in SmartWiFiSocket_Setup.lua; since there is reboot in between Setup and FixedMode, moved the code to each of the files instead of Common Utils so that mem is saved for PortablMode
--function indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.HIGH)
--end

--Used only in FixedMode.lua hence moving it there instead of Common Utils
--function indicate_fixed_socket_mode()
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.HIGH)
--end

--Used only once in PortablMode.lua hence moving it there instead of Common Utils
--function inidicate_portable_socket_mode()
--    gpio.write(TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.HIGH)
--end

--Used only once in this file itself hence moved it into set_gpio_relay_pin
--function indicate_relay_on_with_timer()
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.HIGH)
--end

--Used only once in this file itself hence moved it into reset_gpio_relay_pin
--function indicate_relay_off_with_timer()
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.HIGH)
--end

--Used only once in this file itself hence moved it into set_gpio_relay_pin
--function indicate_relay_on_with_no_timer()
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.LOW)
--    gpio.write(TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.HIGH)
--end

--function init_gpio_relay_pin()
--    gpio.mode(RELAY_OPIN, gpio.OUTPUT)
    
--    gpio.write(RELAY_OPIN, gpio.LOW)
--end

-- Spare Colors for Tri Color LED: Yellow (Red + Blue), Purple (Red + Green)
function reset_gpio_relay_pin(is_for_timer_start_event)
    gpio.write(RELAY_OPIN, gpio.LOW)
    
    if is_for_timer_start_event then
        --indicate_relay_off_with_timer()
            gpio_write_relay_indicator_led_pins(true, false, false)
    else
        --indicate_relay_off_with_no_timer()
            gpio_write_relay_indicator_led_pins(false, false, false)
    end
    
    return gpio.read(RELAY_OPIN) == 0
end

function set_gpio_relay_pin(is_for_timer_start_event)
    gpio.write(RELAY_OPIN, gpio.HIGH)
    
    if is_for_timer_start_event then
        --indicate_relay_on_with_timer()
            gpio_write_relay_indicator_led_pins(false, false, true)
    else
        --indicate_relay_on_with_no_timer()
            gpio_write_relay_indicator_led_pins(false, true, false)
    end
    
    return gpio.read(RELAY_OPIN) == 1
end

function get_gpio_relay_pin_status()
    return gpio.read(RELAY_OPIN)
end

-- Moved to FixedMode as it was called only once
--function get_gpio_relay_pin_status_as_boolean()
--    return gpio.read(RELAY_OPIN) == 1
--end

--It is used only in Setup and FixedMode and not in PortablMode and also since there is reboot between Setup and FixedMode; Moved this function into each of the FixedMode and Setup files to save mem for PortablMode
--function urldecode(str)
--    str = string.gsub(str, "+", " ")
--    str = string.gsub(str, "%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end)
--    str = string.gsub(str, "\r\n", "\n")
--    return str
--end

--Made this function as inline function as only 1 loop present; also it is used only in PortablMode and Setup and since there is reboot in between them; moved code into each of those files to save mem for FixedMode
--function set_ap_ip_config()
----    hardcoding variable value to save mem
----    local DEFAULT_AP_SELF_IP = "192.168.10.1"
----    local DEFAULT_AP_NETMASK = "255.255.255.0"
----    local DEFAULT_AP_GATEWAY = "192.168.10.1"
        
--    while not wifi.ap.setip({
--            ip = "192.168.10.1",
--            netmask = "255.255.255.0",
--            gateway = "192.168.10.1"
--        }) do
--    end
--end

--Made this function as inline function as only 1 loop present; also it is used only in PortablMode and Setup and since there is reboot in between them; moved code into each of those files to save mem for FixedMode
--function set_ap_dhcp_config_and_start_dhcp()
--    local set_ap_dhcp_and_start_dhcp_success_indicator = false
----    local DEFAULT_AP_DHCP_START_IP = "192.168.10.2"       -- hardcoding variable value to save mem
    
--    while not set_ap_dhcp_and_start_dhcp_success_indicator do
--        wifi.ap.dhcp.config ({ start = "192.168.10.2" })
--        set_ap_dhcp_and_start_dhcp_success_indicator = wifi.ap.dhcp.start()
--    end
--end

--function stop_ap_dhcp_server()
--    while not wifi.ap.dhcp.stop() do end
--end