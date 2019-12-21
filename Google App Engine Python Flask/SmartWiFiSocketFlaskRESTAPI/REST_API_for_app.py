#!flask_virtualenv/bin/python
import time
from datetime import datetime

import urllib
import mysql.connector
from flask import Flask, request
from oauth2client import client, crypt

maria_db_host = 'localhost'
maria_db_name = 'smart_wifi_socket'
maria_db_app_user = 'app'
maria_db_app_password = 'passwordForApPDBUserYo1259!'

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

def validate_google_id_token(token, email):

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

        return idinfo['email'] == email
    except crypt.AppIdentityError as aie:
        # Invalid token
        print("exception in validate_google_id_token endpoint: ", aie)
        return False

    except ValueError as ve:
        print("igoring ValueError plain text too large exception in validate_google_id_token endpoint: ", ve)
        return ve[0] == 'Plaintext too large'

    except Exception as e:
        print("exception in validate_google_id_token endpoint: ", e)
        return False

# allow both POST and PUT only via HTTPS as both are called from mobile apps while Internet Mode Config (Registration)
@app.route('/app/api/v1.0/users', methods = ['POST', 'PUT'])
def add_update_user():
    try:
        conn = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_app_user, password=maria_db_app_password)
        conn.autocommit = False
        cursor = conn.cursor(prepared = True)

        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = urllib.unquote_plus(request.values['email'])
        google_oauth_id_token = request.values['google_oauth_id_token']

        if email and google_oauth_id_token:
            if validate_google_id_token(google_oauth_id_token, email):
                if request.method == 'POST':    # create user
                    cursor.execute("insert into users (email, google_oauth_id_token, last_authenticated_timestamp) values(%s, %s, CURRENT_TIMESTAMP)", (email, google_oauth_id_token))
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
        print("exception in users endpoint: ", e)
        conn.rollback()
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        cursor.close()
        conn.close()

# allow POST via HTTPS as it is called from mobile apps while Internet Mode Config (Registration)
# allow PUT only via HTTPS if source=app and google_oauth_id_token is mandatory (do validation) as we are writing the future state of the socket and it must be written by an authenticated/authorized source
# allow PUT via HTTP if source=socket and google_oauth_id_token is optional (no validation required) as we are just updating the state for user to view it (same as read only)
@app.route('/app/api/v1.0/sockets_per_user', methods = ['POST', 'PUT'])
def add_update_sockets_per_user():
    try:
        conn = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_app_user, password=maria_db_app_password)
        conn.autocommit = False
        cursor = conn.cursor(prepared = True)
    
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = urllib.unquote_plus(request.values['email'])
        socket_name = urllib.unquote_plus(request.values['socket_name'])
        external_wifi_ssid = urllib.unquote_plus(request.values['external_wifi_ssid'])  # url decoding
        socket_software_version = request.values['socket_software_version']
        current_desired_state = request.values['current_desired_state']
        timezone_details = request.values['timezone_details']

        # since below 3 params are optional when sent by socket or app, we are using dict.get('key', default_value) method over here
        running_timer_type = request.values.get('running_timer_type', None)
        running_timer_cron_mask_config_string = request.values.get('running_timer_cron_mask_config_string', None)
        if running_timer_cron_mask_config_string:
            running_timer_cron_mask_config_string = urllib.unquote_plus(running_timer_cron_mask_config_string)
        running_timer_secs_left = request.values.get('running_timer_secs_left', None)

        last_updated_datetime = request.values['last_updated_datetime']
        last_updated_by = urllib.unquote_plus(request.values['last_updated_by'])
        last_updated_by_device_source = request.values['last_updated_by_device_source']
        google_oauth_id_token = request.values['google_oauth_id_token']

        if last_updated_by_device_source == 'app':  # dont allow this on the HTTP server, only for HTTPS
            if google_oauth_id_token and validate_google_id_token(google_oauth_id_token, last_updated_by):
                if request.method == 'POST':
                    cursor.execute('insert into sockets_per_user (email, socket_name, external_wifi_ssid, socket_software_version, current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source, last_updated_systime) values (%s, %s, %s, %s, %s, %s, %s, %s, CAST(%s AS UNSIGNED), FROM_UNIXTIME(%s), %s, %s, CURRENT_TIMESTAMP)', (email, socket_name, external_wifi_ssid, socket_software_version, current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source))
                elif request.method == 'PUT':
                    cursor.execute('update sockets_per_user set current_desired_state = %s, timezone_details = %s, running_timer_type = %s, running_timer_cron_mask_config_string = %s, running_timer_secs_left = CAST(%s AS UNSIGNED), last_updated_datetime = FROM_UNIXTIME(%s), last_updated_by = %s, last_updated_by_device_source = %s, last_updated_systime = CURRENT_TIMESTAMP where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s', (current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source, email, socket_name, external_wifi_ssid, socket_software_version))

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
        print("exception in sockets_per_user endpoint: ", e)
        conn.rollback()
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        cursor.close()
        conn.close()

# if source=app then do validation using google_oauth_id_token (HTTPS required)
# if source=socket no validation required on google_oauth_id_token (HTTP ok)
@app.route('/app/api/v1.0/sockets_per_user_status', methods = ['POST'])
def sockets_per_user_status():
    try:
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = urllib.unquote_plus(request.values['email'])
        socket_name = urllib.unquote_plus(request.values['socket_name'])
        external_wifi_ssid = urllib.unquote_plus(request.values['external_wifi_ssid'])
        socket_software_version = int(request.values['socket_software_version'])
        google_oauth_id_token = request.values['google_oauth_id_token']
        status_requestor_email = urllib.unquote_plus(request.values['status_requestor_email'])

        if google_oauth_id_token and validate_google_id_token(google_oauth_id_token, status_requestor_email):
            socket_status_read_cursor.execute('select current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source from sockets_per_user where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s', (email, socket_name, external_wifi_ssid, socket_software_version))
            row = socket_status_read_cursor.fetchone()
        
            if row:
                return '~'.join(map(lambda x:str(int(time.mktime(x.timetuple()))) if type(x) is datetime else str(x), row)), 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "oauth_validation_failed", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print("exception in sockets_per_user_status endpoint: ", e)
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

if __name__ == '__main__':

    socket_status_read_connection = mysql.connector.connect(host=maria_db_host, database=maria_db_name, user=maria_db_app_user, password=maria_db_app_password)
    socket_status_read_connection.autocommit = True
    socket_status_read_cursor = socket_status_read_connection.cursor(prepared = True)

    if socket_status_read_connection.is_connected() and socket_status_read_cursor:
        app.run(debug=True, host='0.0.0.0', port=9911)
