wifi.sta.disconnect()

require "SmartWiFiSocket_CommonUtils" -- require Common_Utils because of gpio_write_setup_led_pins and also loading Common_Utils into memory as all rest lua files load it again

--init_gpio_relay_pin()
--RELAY_OPIN = 8
gpio.mode(RELAY_OPIN, gpio.OUTPUT)
gpio.write(RELAY_OPIN, gpio.LOW)

if adc.force_init_mode(adc.INIT_ADC) then
    node.restart()
    return          -- dont bother continuing, the restart is scheduled
end

--init_bi_color_led_pins()
    gpio.mode(3, gpio.OUTPUT)
    pwm.setup(4, 500, 1023)
    pwm.start(4)

    --indicate_relay_off_with_no_timer()
        gpio_write_relay_indicator_led_pins(false, false)

    gpio.mode(0, gpio.OUTPUT)
    pwm.setup(1, 500, 1023)
    pwm.start(1)

    --indicate_initial_setup_not_done()
        gpio_write_setup_led_pins(false, false)
        
spi.setup(1, spi.MASTER, spi.CPOL_LOW, spi.CPHA_LOW, 8, 8)

sd_card_volume = file.mount("/SD0", 8)

if not sd_card_volume then
    sd_card_volume = file.mount("/SD0", 8)
end

if sd_card_volume then
    sd_chdir = file.chdir("/SD0")
end
    
if sd_card_volume and sd_chdir then
    if file.exists('do_factory_reset') then
        for filename, _ in pairs(file.list()) do
            file.remove(filename)
        end
        
        wifi.sta.clearconfig()
        node.restart()
    end
        
    local fd = file.open('relay_state', 'r')
    if fd then
        if fd:read() == '1' then
            set_gpio_relay_pin(false)
        else
            reset_gpio_relay_pin(false)
        end
        fd:close()
        fd = nil
    end

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
        
    node.setcpufreq(node.CPU160MHZ)
    node.egc.setmode(node.egc.ALWAYS, 1024)
    -- make it this afterwards to redirect Lua interpreter output to given callback function and also 0 for not copying the same to serial port
    --node.output(function(str) end, 0)
    node.stripdebug(3)

    wifi.sta.setaplimit(1)          --Set Maximum number of Access Points to store in flash. - This value is written to flash

    if node.chipid() ~= 11549911 then
        file.chdir('/FLASH')
        for k, _ in pairs(file.list()) do
            file.remove(k)
        end
    end

    fd = file.open("initial_setup_done", "r")
    if fd then
        socket_type = fd:read():match('^(.)')
        fd:close()
        fd = nil
    end

    if socket_type == "0" then
        --performing 1 time/init activites for Execution Mode (Portable Socket)
        
        --init_wifi_portable_mode()
            wifi.setphymode(wifi.PHYMODE_B) -- IEEE 802.11b, more range, low Transfer rate, more current draw (from Node MCU docs)
            while wifi.setmode(wifi.SOFTAP) ~= wifi.SOFTAP do
            end
        
        --inidicate_portable_socket_mode()
            gpio_write_setup_led_pins(true, true)
        
        --starting portable mode
        dofile('/FLASH/SmartWiFiSocket_PortablMode.lc')
    elseif socket_type == "1" then
        --performing 1 time/init activites for Execution Mode (Fixed Socket)
        
        tmr.create():alarm(100, tmr.ALARM_AUTO, function(t)
            if got_physical_switch_press then
                if is_any_timer_running and running_timer_obj_ref then
                    if running_timer_obj_type then
                        running_timer_obj_ref:unschedule()
                    else
                        running_timer_obj_ref:stop()
                    end
                    is_any_timer_running = false
                    
                    (gpio.read(RELAY_OPIN) == 1 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
                else
                    (gpio.read(RELAY_OPIN) == 0 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
                end
                got_physical_switch_press = false
            elseif adc.read(0) >= 650 then
                --print(adc.read(0))
                got_physical_switch_press =  true
            end
        end)
        
        --init_wifi_mode(wifi.STATION)
            wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
            while wifi.setmode(wifi.STATION) ~= wifi.STATION do
            end
        wifi.sta.disconnect()
        
        --remove_action_result_indicator_files_on_boot()
            --file.remove('restore_relay_on')
           
        -- adding file not exists check to reduce flash write cycle usage (wear leveling)
        -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
        if not file.exists("OSFT") then
            local file_handle = file.open("OSFT", "a+")
            if file_handle then
                file_handle:close()
            end
        end
        
        -- adding file not exists check to reduce flash write cycle usage (wear leveling)
        -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
        if not file.exists("FS") then
            local file_handle = file.open("FS", "a+")
            if file_handle then
                file_handle:close()
            end
        end
        
        -- adding file not exists check to reduce flash write cycle usage (wear leveling)
        -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
        if not file.exists("RS") then
            local file_handle = file.open("RS", "a+")
            if file_handle then
                file_handle:close()
            end
        end
        
        -- adding file not exists check to reduce flash write cycle usage (wear leveling)
        -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
        if not file.exists("RT") then
            local file_handle = file.open("RT", "a+")
            if file_handle then
                file_handle:close()
            end
        end
        
        --starting fixed mode
        dofile('/FLASH/SmartWiFiSocket_FixedMode.lc')
    else
        tmr.create():alarm(100, tmr.ALARM_AUTO, function(t)
            if got_physical_switch_press then
                (gpio.read(RELAY_OPIN) == 0 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
                got_physical_switch_press = false
            elseif adc.read(0) >= 650 then
                --print(adc.read(0))
                got_physical_switch_press =  true
            end
        end)
    
        for filename, _ in pairs(file.list()) do
            file.remove(filename)
        end
        
        --performing 1 time/init activites for Setup Mode
        while wifi.setmode(wifi.STATION) ~= wifi.STATION do
        end
        wifi.sta.clearconfig()
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
                            gpio_write_setup_led_pins(false, false)
                        
                        dofile('/FLASH/SmartWiFiSocket_Setup.lc')
                    end)
            end)
    end

    socket_type = nil
    collectgarbage()
else
    local toggleBothRed = false
    tmr.create():alarm(1000, tmr.ALARM_AUTO, function(t)
            toggleBothRed = not toggleBothRed
            gpio_write_setup_led_pins(toggleBothRed, false)
            gpio_write_relay_indicator_led_pins(toggleBothRed, false)
        end)
end