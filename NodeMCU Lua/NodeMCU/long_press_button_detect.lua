--protological.com/debounce-calaculator/
gpio.mode(7, gpio.INT, gpio.PULLUP)
factory_reset_btn_press_interrupt_handler=function(level, when)
    if level == 0 then
        gpio.trig(7, "none")
        print(level, when)
        tmr.create():alarm(3200, tmr.ALARM_SINGLE, function(t)
            if gpio.read(7) == 0 then
                print('factory reset')
            else
                print('reenabled interrupt')
                gpio.trig(7, "low", factory_reset_btn_press_interrupt_handler)
            end
        end)
    end 
end
gpio.trig(7, "low", factory_reset_btn_press_interrupt_handler)
