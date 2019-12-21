-- even if below functions have less lines and used in many places, dont make it inline as we need to keep PIN configurations only here

--FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN = 7 -- Used directly in init.lua
RELAY_OPIN = 2
is_any_timer_running = false
running_timer_obj_ref = nil
running_timer_obj_type = nil  -- false = tmr, true = cron.entry

local fd = file.open('initial_setup_done', 'r')
local remember_power_cuts = nil
if fd then
    remember_power_cuts = fd:read():match('^.(.)') == '1'
    fd:close()
    fd = nil
end

function check_line_present_in_file(line, filename)
    local file_handle = file.open(filename, 'r')
    local line_exists = false
    if file_handle then
        while true do
            local line_from_file = file_handle:readline()
            if line_from_file then
                line_from_file = line_from_file:match("^%s*(.-)%s*$") --match() used to trim
                --print("checking")
                --print(line)
                --print(line_from_file)
                if line_from_file == line then
                    line_exists = true
                    break
                end
            else
                break
            end
        end
        file_handle:close()
    end
    
    return line_exists
end

function gpio_write_setup_led_pins(red, green)
    if red and green then
        pwm.setduty(1, 255)
    elseif red and not green then
        pwm.setduty(1, 1023)
    else
        pwm.setduty(1, 0)
    end
    gpio.write(0, green and gpio.HIGH or gpio.LOW)
end

function gpio_write_relay_indicator_led_pins(red, green)
    if red and green then
        pwm.setduty(4, 255)
    elseif red and not green then
        pwm.setduty(4, 1023)
    else
        pwm.setduty(4, 0)
    end
    gpio.write(3, green and gpio.HIGH or gpio.LOW)
end

function reset_gpio_relay_pin(is_for_timer_start_event)
    gpio.write(RELAY_OPIN, gpio.LOW)
    
    --indicate_relay_off_with_timer() or indicate_relay_off_with_no_timer()
        gpio_write_relay_indicator_led_pins(is_for_timer_start_event, false)
        
    -- remove remember ON state for power failures so that when power comes back, the appliance auto turns ON (only applicable when no timers are used)
    if not is_for_timer_start_event and remember_power_cuts then
        local fd = file.open('relay_state', 'w+')
        fd:write('0')
        fd:close()
        --file.remove('restore_relay_on')
        --print('restore_relay_on removed')
    end
    
    return gpio.read(RELAY_OPIN) == 0
end

function set_gpio_relay_pin(is_for_timer_start_event)
    gpio.write(RELAY_OPIN, gpio.HIGH)
    
    --indicate_relay_on_with_timer() or indicate_relay_on_with_no_timer()
        gpio_write_relay_indicator_led_pins(is_for_timer_start_event, true)
        
    -- remember ON state for power failures so that when power comes back, the appliance auto turns ON (only applicable when no timers are used)
    if not is_for_timer_start_event and remember_power_cuts then
        local fd = file.open('relay_state', 'w+')
        if fd then
            fd:write('1')
            fd:close()
        end
        -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
        --file.open('restore_relay_on', "w+"):close()
        --print('restore_relay_on created')
    end
    
    return gpio.read(RELAY_OPIN) == 1
end

-- made it inline since RELAY_OPIN is global anyways
--function get_gpio_relay_pin_status()
--    return gpio.read(RELAY_OPIN)
--end