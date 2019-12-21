require "SmartWiFiSocket_CommonUtils" -- require Common_Utils because of gpio_write_setup_led_pins and also loading Common_Utils into memory as all rest lua files load it again

--unregister_all_wifi_eventmon_callbacks()
    wifi.eventmon.unregister(wifi.eventmon.STA_GOT_IP)
    wifi.eventmon.unregister(wifi.eventmon.STA_DISCONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.STA_CONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.STA_AUTHMODE_CHANGE)
    wifi.eventmon.unregister(wifi.eventmon.STA_DHCP_TIMEOUT)
    wifi.eventmon.unregister(wifi.eventmon.AP_STACONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.AP_STADISCONNECTED)
    wifi.eventmon.unregister(wifi.eventmon.AP_PROBEREQRECVED)
    wifi.eventmon.unregister(wifi.eventmon.WIFI_MODE_CHANGED)

--init_tri_color_led_pins()
    gpio.mode(0, gpio.OUTPUT)
    gpio.mode(5, gpio.OUTPUT)
    gpio.mode(6, gpio.OUTPUT)

    --indicate_relay_off_with_no_timer()
        gpio_write_relay_indicator_led_pins(false, false, false)

    gpio.mode(1, gpio.OUTPUT)
    gpio.mode(2, gpio.OUTPUT)
    gpio.mode(3, gpio.OUTPUT)

    --indicate_initial_setup_not_done()
        gpio_write_setup_led_pins(false, false, false)
        
function scan_save_nearby_wifi_networks_then_call_setup()
    
    file.remove('scanned_nearby_wifi_networks')
    
    while wifi.setmode(wifi.STATION) ~= wifi.STATION do
    end
    wifi.sta.disconnect()
    
    wifi.sta.getap({ ssid = nil, bssid = nil, channel = 0, show_hidden = 1}, 1, function(tbl)
            local fd = file.open('scanned_nearby_wifi_networks', 'w+')
            if fd then
                for bssid, v in pairs(tbl) do
                    local ssid, rssi, _, _ = string.match(v, "([^,]+),([^,]+),([^,]+),([^,]*)")
                    rssi = tonumber(rssi)
                    rssi = rssi < 0 and (rssi * -1) or rssi                 --taking abs(rssi)
                    fd:write(ssid .. (rssi <= 65 and '1' or '0') .. '\n')   --1 strong signal, 0 weak signal
                end
                fd:close()
            end
            fd = nil
            
            tmr.create():alarm(2000, tmr.ALARM_SINGLE, function(t)
                    dofile('SmartWiFiSocket_Setup.lc')
                end)
        end)
end

function start_socket_fixed_mode()
    --performing 1 time activites
    --init_wifi_mode(wifi.STATION)
        wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
        while wifi.setmode(wifi.STATION) ~= wifi.STATION do
        end
    wifi.sta.disconnect()
    
    --remove_action_result_indicator_files_on_boot()
        file.remove("sntp_sync_done_once_on_boot")
        file.remove('scheduled_cron_entries_on_boot')
        file.remove('restore_relay_on')
    
    --starting fixed mode
    dofile('SmartWiFiSocket_FixedMode.lc')
end

function start_socket_portable_mode()
    --performing 1 time activites
    --init_wifi_portable_mode()
        wifi.setphymode(wifi.PHYMODE_B) -- IEEE 802.11b, more range, low Transfer rate, more current draw (from Node MCU docs)
        while wifi.setmode(wifi.SOFTAP) ~= wifi.SOFTAP do
        end
    
    --inidicate_portable_socket_mode()
        gpio_write_setup_led_pins(false, false, true)
    
    --starting portable mode
    dofile('SmartWiFiSocket_PortablMode.lc')
end

local INITIAL_SETUP_DONE = "initial_setup_done"

if file.exists(INITIAL_SETUP_DONE) then
    
    local fd = file.open(INITIAL_SETUP_DONE, "r")
    -- 1 for Fixed Socket/0 for Portable Socket
    local initial_setup_done_contents = fd:read()
    fd:close()
    INITIAL_SETUP_DONE = nil
    fd = nil
    
    collectgarbage()
    
    local _, _, socket_type = string.find(initial_setup_done_contents, '^(.)')
    
    if socket_type == "1" then
        start_socket_fixed_mode()
    elseif socket_type == "0" then
        start_socket_portable_mode()
    else
        scan_save_nearby_wifi_networks_then_call_setup()
    end
else
    scan_save_nearby_wifi_networks_then_call_setup()
end

--init_factory_reset_redo_initial_setup_interrupt()
--FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN = 7
    gpio.mode(8, gpio.INT)
    
    gpio.trig(8, "up", function(level, when)
            tmr.create():alarm(3200, tmr.ALARM_SINGLE, function(t)
            if gpio.read(7) == 1 then
                file.remove("initial_setup_done")
                file.remove("mdns_hostname")
                file.remove("is_internet_mode_configured")
                file.remove("sntp_sync_done_once_on_boot")
                file.remove("scanned_nearby_wifi_networks")
                
                file.remove("OSFT")
                file.remove("FS")
                file.remove("RS")
                file.remove("RT")

                node.restart()
            end
        end)
    end)