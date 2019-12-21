#!flask_virtualenv/bin/python
import time
from datetime import datetime
import binascii
from Crypto.Cipher import AES
import random

import mysql.connector
import urllib
from flask import Flask, request
from oauth2client import client, crypt

maria_db_host = 'localhost'
maria_db_name = 'smart_wifi_socket'
maria_db_socket_user = 'socket'
maria_db_socket_password = 'passwordForSoCkeTDBUserYo1259!'

socket_status_read_connection = None 

app = Flask(__name__)

# taken from here http://flask.pocoo.org/docs/0.12/patterns/errorpages/
@app.errorhandler(400)  # Bad Request
@app.errorhandler(401)  # Unauthorized
@app.errorhandler(403)  # Forbidden
@app.errorhandler(404)  # Not Found
@app.errorhandler(405)  # Method Not Allowed
@app.errorhandler(500)  # Internal Server Error
@app.errorhandler(502)  # Bad Gateway
def error_msg(e):
    return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

# allow POST via HTTPS as it is called from mobile apps while Internet Mode Config (Registration)
# allow PUT only via HTTPS if source=app and google_oauth_id_token is mandatory (do validation) as we are writing the future state of the socket and it must be written by an authenticated/authorized source
# allow PUT via HTTP if source=socket and google_oauth_id_token is optional (no validation required) as we are just updating the state for user to view it (same as read only)
@app.route('/socket/api/v1.0/sockets_per_user', methods = ['PUT'])
def update_sockets_per_user():
    try:
        conn = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_socket_user, password=maria_db_socket_password)
        conn.autocommit = False
        cursor = conn.cursor(prepared = True)

        # request.data is 16 chars IV [A-Z] immediately followed by encrypted text
        input_values = AES.new('PwS0cKEtDaTA!92$', AES.MODE_CBC, IV=request.data[:16]).decrypt(binascii.unhexlify(request.data[16:])).split('~')
        if len(input_values) == 7:
            input_values += [None, None, None]
        # sample with no running timer  ['ritesht93@gmail.com', 'Fan', 'TripMate-94AA', '1', '0', '019800', '1503261408', None, None, None]
        # sample with running timer     ['ritesht93@gmail.com', 'Fan', 'TripMate-94AA', '1', '2', '019800', '1503261408', 'osct', '1=1000=0', '500']

        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        # email = request.values['email']
        # socket_name = request.values['socket_name']
        # external_wifi_ssid = request.values['external_wifi_ssid']  # url decoding
        # current_desired_state = request.values['current_desired_state']
        # last_updated_datetime_from_http_request = request.values['last_updated_datetime']
        # timezone_details = request.values['timezone_details']
        # last_updated_by_device_source = request.values['last_updated_by_device_source']
        # since below 3 params are optional when sent by ESP, we are using dict.get('key', default_value) method over here
        # running_timer_type = request.values.get('running_timer_type', None)
        # running_timer_cron_mask_config_string = request.values.get('running_timer_cron_mask_config_string', None)
        # if running_timer_cron_mask_config_string:
        #    running_timer_cron_mask_config_string = running_timer_cron_mask_config_string
        # running_timer_secs_left = request.values.get('running_timer_secs_left', None)

        #checking here that timestamp from ESP12 must be greater than timestamp present in table and not vice versa as it ensures user's update isnt lost by overwriting old state from ESP12
        #cursor.execute("select if (UNIX_TIMESTAMP(FROM_UNIXTIME(%s)) >= UNIX_TIMESTAMP(last_updated_datetime), 1, 0) as col from sockets_per_user where email = %s and socket_name = %s", (input_values[4], input_values[0], input_values[1]))

        #if 1 in cursor.fetchone():
        if len(input_values) == 10:
            cursor.execute("update sockets_per_user set current_desired_state = %s, timezone_details = %s, running_timer_type = %s, running_timer_cron_mask_config_string = %s, running_timer_secs_left = CAST(%s AS UNSIGNED), last_updated_datetime = FROM_UNIXTIME(%s), last_updated_by_device_source = 'socket', last_updated_systime = CURRENT_TIMESTAMP where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s", (input_values[4], input_values[5], input_values[7], input_values[8], input_values[9], input_values[6], input_values[0], input_values[1], input_values[2], input_values[3]))

            if cursor.rowcount == 1:
                conn.commit()
                return "success", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                conn.rollback()
                return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        #else:
            #return "tried_overwriting_app_update", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        conn.rollback()
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        cursor.close()
        conn.close()


#def encrypt_sockets_per_user_status(plain_text):
#    iv=''.join(random.choice('1234567890'+'abcdefghijklmnopqrstuvwxyz'[3::2]+'ABCDEFGHIJKLMNOPQRSTUVWXYZ'[4::2]) for i in range(16))
#    return iv+binascii.hexlify(AES.new('PwSeRVeRdAta#92*', AES.MODE_CBC, IV=iv).encrypt(plain_text.ljust(64)))

# if source=app then do validation using google_oauth_id_token (HTTPS required)
# if source=socket no validation required on google_oauth_id_token (HTTP ok)
@app.route('/socket/api/v1.0/sockets_per_user_status', methods = ['GET'])
def sockets_per_user_status():
    try:
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = binascii.unhexlify(request.values['email'])
        socket_name = urllib.unquote_plus(request.values['socket_name'])
        external_wifi_ssid = binascii.unhexlify(request.values['external_wifi_ssid'])
        socket_software_version = request.values['socket_software_version']

        socket_status_read_cursor.execute("select current_desired_state from sockets_per_user where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s and last_updated_by_device_source = 'app'", (email, socket_name, external_wifi_ssid, socket_software_version))
        row = socket_status_read_cursor.fetchone()
        
        if row:
            return ''.join(map(lambda x:str(int(time.mktime(x.timetuple()))) if type(x) is datetime else str(x), row)), 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            
        else:
            return "no_update_from_app", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    #return str(len(return_string)) + "\r\n" + encrypt_sockets_per_user_status(return_string), 200, {'Content-Type' : 'text/plain; charset = utf-8'}
if __name__ == '__main__':

    socket_status_read_connection = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_socket_user, password=maria_db_socket_password)
    socket_status_read_connection.autocommit = True
    socket_status_read_cursor = socket_status_read_connection.cursor(prepared = True)

    if socket_status_read_connection.is_connected() and socket_status_read_cursor:
        app.run(debug=True, host='0.0.0.0', port=9912)
