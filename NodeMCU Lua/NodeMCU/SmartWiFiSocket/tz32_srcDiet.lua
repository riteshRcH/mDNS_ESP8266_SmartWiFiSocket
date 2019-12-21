function gettzoffset(t)if tstart and tend and t>=tstart and t<tend then
return tzoffset or 0
else
local fh=file.open("localtime",'r')if fh and struct.unpack("c4",fh:read(4))=='TZif'then
fh:read(16)local ttisgmtcnt,ttisdstcnt,leapcnt,timecnt,typecnt,charcnt=struct.unpack(">LLLLLL",fh:read(24))local offset=1 local transition_time
local got1stGreaterTransitionTime=false
local i=1 for i=1,timecnt do
transition_time=struct.unpack(">l",fh:read(4))if t>transition_time then
offset=i
tstart=transition_time
else
if not got1stGreaterTransitionTime then
got1stGreaterTransitionTime=transition_time
end
end
end
if not got1stGreaterTransitionTime or timecnt==0 then
tend=0x7fffffff
end
local tindex=0 for i=1,timecnt do
B=fh:read(1)if i==offset then
tindex=struct.unpack("B",B)end
end
local ttinfos=fh:read(6*typecnt)local abbreviations=fh:read(charcnt)local leap_seconds=fh:read(8*leapcnt)tzoffset=struct.unpack(">l",ttinfos,tindex*6+1)return tzoffset or 0
else
return tzoffset or 0
end
end
end