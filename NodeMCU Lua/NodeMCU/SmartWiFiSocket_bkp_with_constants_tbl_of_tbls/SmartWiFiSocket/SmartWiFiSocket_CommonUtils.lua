local CONSTANTS = require "SmartWiFiSocket_Constants"

local toggleTriColorRed = false
local tmrBlinkTriColorRed = tmr.create()
tmrBlinkTriColorRed:register(500, tmr.ALARM_AUTO, function(timer)
        toggleTriColorRed = not toggleTriColorRed
        gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, toggleTriColorRed and gpio.HIGH or gpio.LOW)
    end)

local toggleTriColorBlue = false
local tmrBlinkTriColorBlue = tmr.create()
tmrBlinkTriColorBlue:register(500, tmr.ALARM_AUTO, function(timer)
        toggleTriColorBlue = not toggleTriColorBlue
        gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, toggleTriColorBlue and gpio.HIGH or gpio.LOW)
    end)

function init_wifi_mode(wifi_mode)
    -- wifi mode config taken from https://iotbytes.wordpress.com/wifi-configuration-on-nodemcu/
    
    wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
    while wifi.setmode(wifi_mode) ~= wifi_mode do
    end
end

function init_wifi_portable_mode()
    -- wifi mode config taken from https://nodemcu.readthedocs.io/en/master/en/modules/wifi/#wifisetphymode
    
    wifi.setphymode(wifi.PHYMODE_B) -- IEEE 802.11b, more range, low Transfer rate, more current draw (from Node MCU docs)
    while wifi.setmode(wifi.SOFTAP) ~= wifi.SOFTAP do
    end
end

function create_action_result_indicator_file (filename)  -- max filename length 32 chars for ESP's file system
    local fd = file.open(filename, "w+")
    fd:close()
    fd = nil
end

function remove_action_result_indicator_files_on_boot ()
    file.remove(CONSTANTS.FILENAMES.SNTP_SYNC_DONE_ONCE_ON_BOOT)
end

function unregister_all_wifi_eventmon_callbacks ()
    wifi.eventmon.unregister(wifi.eventmon.STA_GOT_IP)
    wifi.eventmon.unregister(wifi.eventmon.STA_DISCONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.STA_CONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.STA_AUTHMODE_CHANGE)
    wifi.eventmon.unregister(wifi.eventmon.STA_DHCP_TIMEOUT)
    wifi.eventmon.unregister(wifi.eventmon.AP_STACONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.AP_STADISCONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.AP_PROBEREQRECVED)
    wifi.eventmon.unregister(wifi.eventmon.WIFI_MODE_CHANGED)
end

function factory_reset_redo_initial_setup()
    file.remove(CONSTANTS.FILENAMES.INITIAL_SETUP_DONE)
    file.remove(CONSTANTS.FILENAMES.MDNS_HOSTNAME)

    node.restart()
end

function init_factory_reset_redo_initial_setup_interrupt()
    gpio.mode(CONSTANTS.GPIO_PINS.FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN, gpio.INT)
    
    gpio.trig(CONSTANTS.GPIO_PINS.FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN, "up", function(level, when)
            tmr.create():alarm(3200, tmr.ALARM_SINGLE, function(t)
            if gpio.read(CONSTANTS.GPIO_PINS.FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN) == 1 then
                factory_reset_redo_initial_setup()
            end
        end)
    end)
end

--function indicate_relay_off_with_no_timer()
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.LOW)
--end

function indicate_initial_setup_not_done()
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
end

function init_tri_color_led_pins()
--    gpio.mode(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.OUTPUT)
--    gpio.mode(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.OUTPUT)
--    gpio.mode(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.OUTPUT)
    
--    indicate_relay_off_with_no_timer()
    
    gpio.mode(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.OUTPUT)
    gpio.mode(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.OUTPUT)
    gpio.mode(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.OUTPUT)
    
    indicate_initial_setup_not_done()
end

function indicate_trying_connect_to_external_socket_host_wifi_start_timer()
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
    
    tmrBlinkTriColorRed:start()
end

function indicate_trying_to_setup_portable_socket_start_timer()
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
    
    tmrBlinkTriColorBlue:start()
end

function indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
    tmrBlinkTriColorRed:stop()
    
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
end

function indicate_trying_to_setup_portable_socket_stop_timer()
    tmrBlinkTriColorBlue:stop()
    
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
end

function indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.HIGH)
end

function indicate_fixed_socket_mode()
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.HIGH)
end

function inidicate_portable_socket_mode()
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_RED_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_GREEN_OPIN, gpio.LOW)
    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_SETUP_LED_BLUE_OPIN, gpio.HIGH)
end

--function indicate_relay_on_with_timer()
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.HIGH)
--end

--function indicate_relay_off_with_timer()
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.HIGH)
--end

--function indicate_relay_on_with_no_timer()
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN, gpio.LOW)
--    gpio.write(CONSTANTS.GPIO_PINS.TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN, gpio.HIGH)
--end

function init_gpio_relay_pin()
    gpio.mode(CONSTANTS.GPIO_PINS.RELAY_OPIN, gpio.OUTPUT)
    
    gpio.write(CONSTANTS.GPIO_PINS.RELAY_OPIN, gpio.LOW)
end

function reset_gpio_relay_pin()
    gpio.write(CONSTANTS.GPIO_PINS.RELAY_OPIN, gpio.LOW)
    
    return gpio.read(CONSTANTS.GPIO_PINS.RELAY_OPIN) == 0
end

function set_gpio_relay_pin()
    gpio.write(CONSTANTS.GPIO_PINS.RELAY_OPIN, gpio.HIGH)
    
    return gpio.read(CONSTANTS.GPIO_PINS.RELAY_OPIN) == 1
end

function get_gpio_relay_pin_status()
    return gpio.read(CONSTANTS.GPIO_PINS.RELAY_OPIN)
end

function urldecode(str)
    str = string.gsub(str, "+", " ")
    str = string.gsub(str, "%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end)
    str = string.gsub(str, "\r\n", "\n")
    return str
end

function set_ap_ip_config ()
    while not wifi.ap.setip({
            ip = CONSTANTS.AP_NETWORK_SETTINGS.DEFAULT_AP_SELF_IP,
            netmask = CONSTANTS.AP_NETWORK_SETTINGS.DEFAULT_AP_NETMASK,
            gateway = CONSTANTS.AP_NETWORK_SETTINGS.DEFAULT_AP_GATEWAY
        }) do
    end
end

function set_ap_dhcp_config_and_start_dhcp ()
    set_ap_dhcp_and_start_dhcp_success_indicator = false
    while not set_ap_dhcp_and_start_dhcp_success_indicator do
        wifi.ap.dhcp.config ({ start = CONSTANTS.AP_NETWORK_SETTINGS.DEFAULT_AP_DHCP_START_IP })
        set_ap_dhcp_and_start_dhcp_success_indicator = wifi.ap.dhcp.start()
    end
end

--function stop_ap_dhcp_server()
--    while not wifi.ap.dhcp.stop() do end
--end