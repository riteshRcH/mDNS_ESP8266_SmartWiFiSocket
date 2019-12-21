-- see man tzfile on any Unix Like System

--tstart = 0;tend = 0;tzoffset = 0;checkdst=true

--local function hexprint(bytestr, ul)
--    s=""
--    for i=1,ul do
--        s=s..string.format('%02x', bytestr:byte(i,i))..","
--    end
--    print('bytestr of len ' .. ul .. ' is =>' .. s)
--end

function gettzoffset(t)
	if tstart and tend and t >= tstart and t < tend then
		--print(fname, tzoffset, tstart, tend)
        return tzoffset or 0
	else
		local fh=file.open("localtime", 'r')
		if fh and struct.unpack("c4", fh:read(4)) == 'TZif' then
			--ignore version as we need only 32 bit version and also ignore subsequent 15 null bytes
			fh:read(16)
			local ttisgmtcnt, ttisdstcnt, leapcnt, timecnt, typecnt, charcnt = struct.unpack(">LLLLLL", fh:read(24))
			
			local offset = 1
			local transition_time
			local got1stGreaterTransitionTime = false
			local i = 1
			for i = 1, timecnt do
				transition_time = struct.unpack(">l", fh:read(4))
				--print(t, transition_time, t > transition_time)
				if t > transition_time then
					offset = i
					tstart = transition_time
				else
					if not got1stGreaterTransitionTime then
						got1stGreaterTransitionTime = transition_time
					end
				end
			end
			
			if not got1stGreaterTransitionTime or timecnt == 0 then	--timecnt==0 for UTC/UCT
				tend = 0x7fffffff
			end
			
			local tindex = 0										--default index=0 for UTC/UCT
			for i=1, timecnt do
				B=fh:read(1)
				if i == offset then
					tindex = struct.unpack("B", B)
				end
			end
			
			local ttinfos = fh:read(6 * typecnt)
			--hexprint(ttinfos, 6 * typecnt)
			local abbreviations = fh:read(charcnt)
			--hexprint(abbreviations, charcnt)
			local leap_seconds = fh:read(8 * leapcnt)
			--hexprint(leap_seconds, 8 * leapcnt)
			--local isstd = {}
			--for i=1, ttisdstcnt do
			--	isstd[i] = not (fh:read(1) == nil)
				--print("isstd[" .. tostring(i) .. "] : " .. tostring(isstd[i]))
			--end
			--local isgmt = {}
			--for i=1, ttisgmtcnt do
			--	isstd[i] = not (fh:read(1) == nil)
				--print("isgmt[" .. tostring(i) .. "] : " .. tostring(isstd[i]))
			--end
			
			tzoffset = struct.unpack(">l", ttinfos, tindex * 6 + 1)
			--print(fname, tzoffset, tstart, tend, ttisgmtcnt, ttisdstcnt, leapcnt, timecnt, typecnt, charcnt)
            return tzoffset or 0
		else
			return tzoffset or 0
		end
	end
end

--[[local cnter=0
for fname, _ in pairs(file.list()) do
    if fname:match('zone$') then
		cnter = cnter + 1
        --print(fname)
        
        --time pair 1 to check dst changes
        --gettzoffset(fname, 1490918400)  --dst time
        --gettzoffset(fname, 1514678400)  --non dst time
        
        --time pair 2 to check dst changes
        gettzoffset(fname, 1496927324)  --dst time
        gettzoffset(fname, 1512738524)  --non dst time
    end
end
print("Total zone files: ", cnter)]]