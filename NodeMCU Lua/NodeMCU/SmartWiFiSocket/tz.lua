-- see man tzfile on any Unix Like System

tstart = 0
tend = 0
toffset = 0

function hexprint(bytestr, ul)
    s=""
    for i=1,ul do
        s=s..string.format('%02x', bytestr:byte(i,i))..","
    end
    print('bytestr of len ' .. ul .. ' is =>' .. s)
end
fh=file.open('Calcutta.zone', 'r')
content=fh:read()
fh:close()

version = content:sub(5,5)
if version == '2' or version == '3' then
    offset = content:find('TZif', 6)
    sizeof_transition_times_leap_seconds = 8
else
    offset = 1
    sizeof_transition_times_leap_seconds = 4
end

if content:sub(offset, offset+4) == 'TZif' .. content:sub(5,5) then
    --ignore 15 null bytes
    --fifteen_nulls=content:sub(offset+5, offset+19)
    --hexprint(fifteen_nulls, 15)
    ttisgmtcnt, ttisdstcnt, leapcnt, timecnt, typecnt, charcnt = struct.unpack(">LLLLLL", content:sub(offset+20, offset+43))
    print(ttisgmtcnt)
    print(ttisdstcnt)
    print(leapcnt)
    print(timecnt)
    print(typecnt)
    print(charcnt)
    
    transition_times = nil
    typeindex = nil
    if timecnt and timecnt > 0 then
        transition_times = content:sub(offset+44, offset+43+(sizeof_transition_times_leap_seconds * timecnt))
        hexprint(transition_times, sizeof_transition_times_leap_seconds * timecnt)
        typeindex = content:sub(offset+44+(sizeof_transition_times_leap_seconds * timecnt), offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt)
        hexprint(typeindex, timecnt)
    end
    ttinfos = content:sub(offset+44+(sizeof_transition_times_leap_seconds * timecnt)+timecnt, offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt))
    hexprint(ttinfos, 6 * typecnt)
    --ignore charcnt byte of abbreviations
    --abbreviations=content:sub(offset+44+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt), offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt)
    --hexprint(abbreviations, charcnt)
    leap_seconds = nil
    if leapcnt and leapcnt > 0 then
        leap_seconds = content:sub(offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt, offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt+(12*leapcnt))
        hexprint(leap_seconds, 12 * leapcnt)
    end

    --[[
    --below are used only when timezone file is used in handling POSIX-style timezone env var
    isstd = nil
    if ttisdstcnt and ttisdstcnt > 0 then
        isstd = content:sub(offset+44+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt+(12*leapcnt), offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt+((sizeof_transition_times_leap_seconds+4)*leapcnt)+ttisdstcnt)
        hexprint(isstd, ttisdstcnt)
    end

    isgmt = nil
    if ttisgmt_count and ttisgmt_count > 0 then
        isgmt = content:sub(offset+44+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt+((sizeof_transition_times_leap_seconds+4)*leapcnt)+ttisdstcnt, offset+43+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt+((sizeof_transition_times_leap_seconds+4)*leapcnt)+ttisdstcnt+ttisgmt_count)
        hexprint(isstd, ttisgmt_count)
    end
    --]]
    --[[
    After the second header and data comes a newline-enclosed, POSIX-TZ-environment-variable-style string
    for use in handling instants after the last transition time stored in the file
    (with nothing between the newlines if there is no POSIX representation for such instants).
    ]]

    --POSIX_TZ_env_var_string=content:sub(offset+44+(sizeof_transition_times_leap_seconds * timecnt)+timecnt+(6 * typecnt)+charcnt+((sizeof_transition_times_leap_seconds+4)*leapcnt)+ttisdstcnt+ttisgmt_count)
    --hexprint(POSIX_TZ_env_var_string, POSIX_TZ_env_var_string:len())

    --[[
    For version-3-format time zone files, the POSIX-TZ-style string may
    use two minor extensions to the POSIX TZ format, as described in newtzset (3).
    First, the hours part of its transition times may be signed and range from
    -167 through 167 instead of the POSIX-required unsigned values
    from 0 through 24.  Second, DST is in effect all year if it starts
    January 1 at 00:00 and ends December 31 at 24:00 plus the difference
    between daylight saving and standard time.
    ]]

    ---[[
end
