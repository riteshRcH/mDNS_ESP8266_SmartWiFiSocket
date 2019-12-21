require "SmartWiFiSocket_CommonUtils" -- require Common_Utils because of gpio_write_setup_led_pins and also loading Common_Utils into memory as all rest lua files load it again

--init_gpio_relay_pin()
--RELAY_OPIN = 8
gpio.mode(RELAY_OPIN, gpio.OUTPUT)
gpio.write(RELAY_OPIN, file.exists('restore_relay_on') and gpio.HIGH or gpio.LOW)

wifi.sta.disconnect()

-- 0 = portable mode, 1 = fixed mode and nil is setup
local socket_type = nil

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
    gpio.mode(3, gpio.OUTPUT)
    gpio.mode(5, gpio.OUTPUT)
    gpio.mode(6, gpio.OUTPUT)

    --indicate_relay_off_with_no_timer()
        gpio_write_relay_indicator_led_pins(false, false, false)

    gpio.mode(0, gpio.OUTPUT)
    gpio.mode(1, gpio.OUTPUT)
    gpio.mode(2, gpio.OUTPUT)

    --indicate_initial_setup_not_done()
        gpio_write_setup_led_pins(false, false, false)
        
node.setcpufreq(node.CPU160MHZ)
node.egc.setmode(node.egc.ALWAYS, 256)
-- make it this afterwards to redirect Lua interpreter output to given callback function and also 0 for not copying the same to serial port
--node.output(function(str) end, 0)
node.stripdebug(3)

wifi.sta.setaplimit(1)          --Set Maximum number of Access Points to store in flash. - This value is written to flash

if node.chipid() ~= 11549911 then
    for k, _ in pairs(file.list()) do
        file.remove(k)
    end
end

local fd = file.open("initial_setup_done", "r")
if fd then
    socket_type = fd:read():match('^(.)')
    fd:close()
    fd = nil
end

--FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN = 7
factory_reset_redo_initial_setup_interrupt_handler = function()
    gpio.trig(7, "none")
    
    tmr.create():alarm(3200, tmr.ALARM_SINGLE, function(t)
            if gpio.read(7) == 0 then
--                file.remove("initial_setup_done")
--                file.remove("mdns_hostname")
--                file.remove("internet_mode_configured")
--                file.remove("scanned_nearby_wifi_networks")
--                file.remove('restore_relay_on')
                
--                file.remove("OSFT")
--                file.remove("FS")
--                file.remove("RS")
--                file.remove("RT")
                
--                for past_daily_schedule_timer_filename, _ in pairs(file.list())
--                do
--                    if past_daily_schedule_timer_filename:match('^_') or past_daily_schedule_timer_filename:match('.zone$') then
--                        file.remove(past_daily_schedule_timer_filename)
--                    end
--                end

                -- can remove all files as code files wont be removed anyways due to code changes made in firmware (app/modules/file.c)
                for filename, _ in pairs(file.list()) do
                    file.remove(filename)
                end
                
                wifi.sta.clearconfig()

                node.restart()
            else
                gpio.mode(7, gpio.INT, gpio.PULLUP)
                gpio.trig(7, "down", factory_reset_redo_initial_setup_interrupt_handler)
            end
        end)
end

if socket_type == "0" then
    --performing 1 time/init activites for Execution Mode (Portable Socket)
    
--    if is_physical_switch_present then
--        tmr.create():alarm(100, tmr.ALARM_AUTO, function(t)
--            if got_physical_switch_press then
--                stop_toggle_timer_and_set_same_state()
--                got_physical_switch_press = false
--            elseif adc.read(0) >= 650 then
--                --print(adc.read(0))
--                got_physical_switch_press =  true
--            end
--        end)
--    end
    
    --init_wifi_portable_mode()
        wifi.setphymode(wifi.PHYMODE_B) -- IEEE 802.11b, more range, low Transfer rate, more current draw (from Node MCU docs)
        while wifi.setmode(wifi.SOFTAP) ~= wifi.SOFTAP do
        end
    
    --inidicate_portable_socket_mode()
        if wifi.ap.getconfig(true)['hidden'] then
            gpio_write_setup_led_pins(true, true, false)
        else
            gpio_write_setup_led_pins(false, false, true)
        end
        
    gpio.mode(7, gpio.INT, gpio.PULLUP)
    gpio.trig(7, "down", factory_reset_redo_initial_setup_interrupt_handler)
    
    --starting portable mode
    dofile('SmartWiFiSocket_PortablMode.lc')
elseif socket_type == "1" then
    --performing 1 time/init activites for Execution Mode (Fixed Socket)
    
    --init_wifi_mode(wifi.STATION)
        wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
        while wifi.setmode(wifi.STATION) ~= wifi.STATION do
        end
    wifi.sta.disconnect()
    
    --remove_action_result_indicator_files_on_boot()
        file.remove('restore_relay_on')
       
    -- adding file not exists check to reduce flash write cycle usage (wear leveling)
    -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
    if not file.exists("OSFT") then
        file.open("OSFT", "a+"):close()
    end
    
    -- adding file not exists check to reduce flash write cycle usage (wear leveling)
    -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
    if not file.exists("FS") then
        file.open("FS", "a+"):close()
    end
    
    -- adding file not exists check to reduce flash write cycle usage (wear leveling)
    -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
    if not file.exists("RS") then
        file.open("RS", "a+"):close()
    end
    
    -- adding file not exists check to reduce flash write cycle usage (wear leveling)
    -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
    if not file.exists("RT") then
        file.open("RT", "a+"):close()
    end
    
    gpio.mode(7, gpio.INT, gpio.PULLUP)
    gpio.trig(7, "down", factory_reset_redo_initial_setup_interrupt_handler)
    
    --starting fixed mode
    dofile('SmartWiFiSocket_FixedMode.lc')
else
    factory_reset_redo_initial_setup_interrupt_handler = nil
    
--    if is_physical_switch_present then
--        tmr.create():alarm(100, tmr.ALARM_AUTO, function(t)
--            if got_physical_switch_press then
--                (gpio.read(RELAY_OPIN) == 0 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
--                got_physical_switch_press = false
--            elseif adc.read(0) >= 650 then
--                --print(adc.read(0))
--                got_physical_switch_press =  true
--            end
--        end)
--    end
    
    --performing 1 time/init activites for Setup Mode
    while wifi.setmode(wifi.STATION) ~= wifi.STATION do
    end
    wifi.sta.disconnect()
    
    -- No filters only SSID/BSSID, channel = 0 scan all channels and show_hidden = 1 get hidden SSIDs too
    wifi.sta.getap({ ssid = nil, bssid = nil, channel = 0, show_hidden = 1}, 1, function(tbl)
            local fh = file.open('scanned_nearby_wifi_networks', 'w+')
            if fh then
                for bssid, v in pairs(tbl) do
                    local ssid, rssi, _, _ = v:match("([^,]+),([^,]+),([^,]+),([^,]*)")
                    rssi = math.abs(tonumber(rssi))
                    fh:write(ssid .. (rssi <= 65 and '1' or '0') .. '\n')   --1 strong signal, 0 weak signal
                end
                fh:close()
            end
            
            tmr.create():alarm(2000, tmr.ALARM_SINGLE, function(t)
                    
                    --init_wifi_mode(wifi.SOFTAP)
                        wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
                        while wifi.setmode(wifi.SOFTAP) ~= wifi.SOFTAP do
                        end
                    
                    --indicate_initial_setup_not_done()
                        gpio_write_setup_led_pins(false, false, false)
                    
                    dofile('SmartWiFiSocket_Setup.lc')
                end)
        end)
end

collectgarbage()