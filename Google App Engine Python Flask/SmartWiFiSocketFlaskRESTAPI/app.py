#!flask_virtualenv/bin/python
import mysql.connector
import urllib
import time
from datetime import datetime
from flask import Flask, request
from mysql.connector import Error
from oauth2client import client, crypt

maria_db_host = 'localhost'
maria_db_name = 'smart_wifi_socket'
maria_db_user = 'root'
maria_db_password = 'password4MariaDBSmartWiFiSocketYo1259'

socket_status_read_connection = None 

app = Flask(__name__)

@app.route('/')
def index():
    return "NA", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

def validate_google_id_token(token):

    SERVERS_CLIENT_ID = "173355727617-gh25d49d657nvi3nu1s1v0upsh0ug050.apps.googleusercontent.com"

    try:
        idinfo = client.verify_id_token(token, SERVERS_CLIENT_ID)

        # Or, if multiple clients access the backend server:
        #idinfo = client.verify_id_token(token, None)
        #if idinfo['aud'] not in [CLIENT_ID_1, CLIENT_ID_2, CLIENT_ID_3]:
        #    raise crypt.AppIdentityError("Unrecognized client.")

        if idinfo['iss'] not in ['accounts.google.com', 'https://accounts.google.com']:
            raise crypt.AppIdentityError("Wrong issuer.")

        # If auth request is from a G Suite domain:
        #if idinfo['hd'] != GSUITE_DOMAIN_NAME:
        #    raise crypt.AppIdentityError("Wrong hosted domain.")

        return idinfo['email']
    except crypt.AppIdentityError as aie:
        # Invalid token
        print(aie)
        return None

# allow both POST and PUT only via HTTPS as both are called from mobile apps while Internet Mode Config (Registration)
@app.route('/smart_wifi_socket/api/v1.0/users', methods = ['POST', 'PUT'])
def add_update_user():
    try:
        conn = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_user, password=maria_db_password)
        cursor = conn.cursor(prepared = True)

        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = request.values['email']
        google_oauth_id_token = request.values['google_oauth_id_token']

        if email and google_oauth_id_token:
            if validate_google_id_token(google_oauth_id_token) == email:
                if request.method == 'POST':    # create user
                    cursor.execute("insert into users values(%s, %s, CURRENT_TIMESTAMP)", (email, google_oauth_id_token))
                elif request.method == 'PUT':    # revalidate and update users oauth id token
                    cursor.execute("update users set google_oauth_id_token = %s, last_authenticated_timestamp = CURRENT_TIMESTAMP where email = %s", (google_oauth_id_token, email))

                if cursor.rowcount == 1:
                    conn.commit()
                    return "success", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
                else:
                    conn.rollback()
                    return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "oauth_validation_failed", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except mysql.connector.IntegrityError as ie:
        conn.rollback()
        mysql_error_code = ie.args[0]   # unique/primary key violation i.e users email already registered
        return "already_registered" if mysql_error_code == 1062 else "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        conn.rollback()
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        cursor.close()
        conn.close()

# allow POST only via HTTPS as it is called from mobile apps while Internet Mode Config (Registration)
@app.route('/smart_wifi_socket/api/v1.0/sockets', methods = ['POST'])
def add_socket_name():
    try:
        conn = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_user, password=maria_db_password)
        cursor = conn.cursor(prepared = True)
    
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        socket_name = urllib.unquote_plus(request.values['socket_name'])                # url decoding
        external_wifi_ssid = urllib.unquote_plus(request.values['external_wifi_ssid'])  # url decoding
        email = request.values['email']
        google_oauth_id_token = request.values['google_oauth_id_token']

        if email and google_oauth_id_token and socket_name and external_wifi_ssid:
            if validate_google_id_token(google_oauth_id_token) == email:
                cursor.execute('insert into sockets values(%s, %s)', (socket_name, external_wifi_ssid))
                if cursor.rowcount == 1:
                    conn.commit()
                    return "success", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
                else:
                    conn.rollback()
                    return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "oauth_validation_failed", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except mysql.connector.IntegrityError as ie:
        conn.rollback()
        mysql_error_code = ie.args[0]   # unique/primary key violation i.e socket_name already present
        return "already_present" if mysql_error_code == 1062 else "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        conn.rollback()
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        cursor.close()
        conn.close()

# allow POST via HTTPS as it is called from mobile apps while Internet Mode Config (Registration)
# allow PUT only via HTTPS if source=app and google_oauth_id_token is mandatory (do validation) as we are writing the future state of the socket and it must be written by an authenticated/authorized source
# allow PUT via HTTP if source=socket and google_oauth_id_token is optional (no validation required) as we are just updating the state for user to view it (same as read only)
@app.route('/smart_wifi_socket/api/v1.0/sockets_per_user', methods = ['POST', 'PUT'])
def add_update_sockets_per_user():
    try:
        conn = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_user, password=maria_db_password)
        cursor = conn.cursor(prepared = True)
    
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = request.values['email']
        socket_name = urllib.unquote_plus(request.values['socket_name'])
        external_wifi_ssid = urllib.unquote_plus(request.values['external_wifi_ssid'])  # url decoding
        current_desired_state = request.values['current_desired_state']
        last_updated_datetime_from_http_request = request.values['last_updated_datetime']
        timezone_details = request.values['timezone_details']
        last_updated_by = request.values['last_updated_by']
        last_updated_by_device_source = request.values['last_updated_by_device_source']
        # since below 4 params are optional when sent by ESP, we are using dict.get('key', default_value) method over here
        google_oauth_id_token = request.values.get('google_oauth_id_token', None)       # will be None when ESP12 tries to do PUT to update status
        running_timer_type = request.values.get('running_timer_type', None)
        running_timer_cron_mask_config_string = request.values.get('running_timer_cron_mask_config_string', None)
        running_timer_secs_left = request.values.get('running_timer_secs_left', None)

        cursor.execute("select * from sockets where socket_name = %s and external_wifi_ssid = %s", (socket_name, external_wifi_ssid))
        row = cursor.fetchone()

        if row:
            if last_updated_by_device_source == 'app':  # dont allow this on the HTTP server, only for HTTPS
                google_oauth_id_token_validation_result = (request.method in ('POST', 'PUT') and google_oauth_id_token and validate_google_id_token(google_oauth_id_token) == last_updated_by)
            elif last_updated_by_device_source == 'socket' and request.method == 'PUT':    # for HTTP server
                #checking here that timestamp from ESP12 must be greater than timestamp present in table and not vice versa as it ensures user's update isnt lost by overwriting old state from ESP12
                cursor.execute("select if (UNIX_TIMESTAMP(FROM_UNIXTIME(%s)) >= UNIX_TIMESTAMP(last_updated_datetime), 1, 0) as col from sockets_per_user where email = %s and socket_name = %s", (last_updated_datetime_from_http_request, email, socket_name))
                if 1 in cursor.fetchone():
                    google_oauth_id_token_validation_result = True
                else:
                    return "tried_overwriting_app_update", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

            if google_oauth_id_token_validation_result:
                if request.method == 'POST':
                    cursor.execute('insert into sockets_per_user values (%s, %s, %s, FROM_UNIXTIME(%s), %s, %s, %s, %s, CAST(%s AS UNSIGNED), %s, CURRENT_TIMESTAMP)', (email, socket_name, current_desired_state, last_updated_datetime_from_http_request, timezone_details, last_updated_by, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_by_device_source ))
                elif request.method == 'PUT':
                    cursor.execute('update sockets_per_user set current_desired_state = %s, last_updated_datetime = FROM_UNIXTIME(%s), timezone_details = %s, last_updated_by = %s, running_timer_type = %s, running_timer_cron_mask_config_string = %s, running_timer_secs_left = CAST(%s AS UNSIGNED), last_updated_by_device_source = %s, last_updated_systime = CURRENT_TIMESTAMP where email = %s and socket_name = %s', (current_desired_state, last_updated_datetime_from_http_request , timezone_details, last_updated_by, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_by_device_source, email, socket_name))

                if cursor.rowcount == 1:
                    conn.commit()
                    return "success", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
                else:
                    conn.rollback()
                    return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "oauth_validation_failed", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "socket_name_and_external_wifi_ssid_combo_not_present", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except mysql.connector.IntegrityError as ie:
        conn.rollback()
        mysql_error_code = ie.args[0]   # unique/primary key violation i.e socket_name already present
        return "already_present" if mysql_error_code == 1062 else "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        conn.rollback()
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        cursor.close()
        conn.close()

# if source=app then do validation using google_oauth_id_token (HTTPS required)
# if source=socket no validation required on google_oauth_id_token (HTTP ok)
@app.route('/smart_wifi_socket/api/v1.0/sockets_per_user', methods = ['GET'])
def sockets_per_user_status():
    try:
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = request.values['email']
        socket_name = request.values['socket_name']
        external_wifi_ssid = request.values['external_wifi_ssid']
        source = request.values['source']
        # since below param is optional when sent by ESP, we are using dict.get('key', default_value) method over here
        google_oauth_id_token = request.values.get('google_oauth_id_token', None)

        socket_status_read_cursor.execute("select * from sockets where socket_name = %s and external_wifi_ssid = %s", (socket_name, external_wifi_ssid))
        row = socket_status_read_cursor.fetchone()

        if row:
            if source == 'app':         # HTTPS version
                # not good to send body with HTTP GET
                #if google_oauth_id_token and validate_google_oauth_id_token(google_oauth_id_token):
                    socket_status_read_cursor.execute('select email, socket_name, current_desired_state, last_updated_datetime, timezone_details, last_updated_by, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left from sockets_per_user where email = %s and socket_name = %s', (email, socket_name))
                #else:
                #    return "oauth_validation_failed", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            elif source == 'socket':    # HTTP version
                socket_status_read_cursor.execute("select current_desired_state, last_updated_datetime, timezone_details from sockets_per_user where email = %s and socket_name = %s and last_updated_by_source = 'app'", (email, socket_name))

            row = socket_status_read_cursor.fetchone()
        
            if row:
                return '~'.join(map(lambda x:str(int(time.mktime(x.timetuple()))) if type(x) is datetime else str(x), row)), 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "no_update_from_app", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "socket_name_and_external_wifi_ssid_combo_not_present", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

if __name__ == '__main__':

    socket_status_read_connection = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_user, password=maria_db_password)
    socket_status_read_cursor = socket_status_read_connection.cursor(prepared = True)

    if socket_status_read_connection.is_connected() and socket_status_read_cursor:
        app.run(debug=True)
