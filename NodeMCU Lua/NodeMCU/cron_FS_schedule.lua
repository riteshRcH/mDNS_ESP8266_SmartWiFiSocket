function cron_FS_schedule(cron_mask_config_string)
    local _, _, cron_mask, in_year, desired_state = string.find(cron_mask_config_string, '([^=]+)=([^=]+)=(.)')
    cron_mask = cron_mask:gsub('_', ' ')
    
    do
        in_year_in_use = in_year
        desired_state_in_use = desired_state
        
        function schedule_callback(e)
            current_year = rtctime.epoch2cal(rtctime.get())['year']
            print(tonumber(current_year))
            print(tonumber(in_year_in_use))
            print(desired_state_in_use)
            if tonumber(current_year) == tonumber(in_year_in_use) then
                print('year equal')
                if desired_state_in_use == '1' then
                    print('set')
                else
                    print('reset')
                end
            end
        end
    end

    cron.schedule(cron_mask, schedule_callback)
    return true
end