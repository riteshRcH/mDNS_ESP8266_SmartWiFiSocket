import network

ap_if = network.WLAN(network.AP_IF)

def set_ap_if_essid(new_essid):
    ap_if.config(essid = new_essid)

def get_ap_if_essid():
    return ap_if.config('essid')

def set_ap_if_password(new_pw):
    ap_if.config(password = new_pw)

def get_ap_if_password():
    return ap_if.config('password')

def set_ap_if_channel(new_channel):
    ap_if.config(channel = new_channel)

def get_ap_if_channel():
    return ap_if.config('channel')

if __name__ == "__main__":
    print(get_ap_if_essid())
    print(get_ap_if_password())
    print(get_ap_if_channel())