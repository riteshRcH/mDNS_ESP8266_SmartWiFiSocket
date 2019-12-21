-- even if below functions have less lines and used in many places, dont make it inline as we need to keep PIN configurations only here

--FACTORY_RESET_REDO_INITIAL_SETUP_INT_IPIN = 7 -- Used directly in init.lua
RELAY_OPIN = 8

function gpio_write_setup_led_pins(red, green, blue)
--    TRI_COLOR_SETUP_LED_RED_OPIN = 0
--    TRI_COLOR_SETUP_LED_GREEN_OPIN = 1
--    TRI_COLOR_SETUP_LED_BLUE_OPIN = 2
    
    gpio.write(0, red and gpio.HIGH or gpio.LOW)
    gpio.write(1, green and gpio.HIGH or gpio.LOW)
    gpio.write(2, blue and gpio.HIGH or gpio.LOW)
end

function gpio_write_relay_indicator_led_pins(red, green, blue)
--    TRI_COLOR_RELAY_INDICATOR_LED_RED_OPIN = 3
--    -- Pin 4 is used by Active Low onboard Blue LED for UART TX
--    TRI_COLOR_RELAY_INDICATOR_LED_GREEN_OPIN = 5
--    TRI_COLOR_RELAY_INDICATOR_LED_BLUE_OPIN = 6

    gpio.write(3, red and gpio.HIGH or gpio.LOW)
    gpio.write(5, green and gpio.HIGH or gpio.LOW)
    gpio.write(6, blue and gpio.HIGH or gpio.LOW)
end

-- Spare Colors for Tri Color LED: Yellow (Red + Blue), Purple (Red + Green)
function reset_gpio_relay_pin(is_for_timer_start_event)
    gpio.write(RELAY_OPIN, gpio.LOW)
    
    --indicate_relay_off_with_timer() or indicate_relay_off_with_no_timer()
        gpio_write_relay_indicator_led_pins(is_for_timer_start_event, false, false)
    
    return gpio.read(RELAY_OPIN) == 0
end

function set_gpio_relay_pin(is_for_timer_start_event)
    gpio.write(RELAY_OPIN, gpio.HIGH)
    
    --indicate_relay_on_with_timer() or indicate_relay_on_with_no_timer()
        gpio_write_relay_indicator_led_pins(false, not is_for_timer_start_event, is_for_timer_start_event)
    
    return gpio.read(RELAY_OPIN) == 1
end

-- made it inline since RELAY_OPIN is global anyways
--function get_gpio_relay_pin_status()
--    return gpio.read(RELAY_OPIN)
--end

toggleTimerRunning = false
tmrToggleRelayPin = tmr.create()
stop_toggle_timer_and_set_same_state = function()
    if toggleTimerRunning then
        tmrToggleRelayPin:stop()
        toggleTimerRunning = false;
        
        --update relay indicator LED colors
        (gpio.read(RELAY_OPIN) == 1 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
    else
        (gpio.read(RELAY_OPIN) == 0 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
    end
    
    return "success"
end