#!usr/bin/python
import time, binascii, random
from datetime import datetime
from Crypto.Cipher import AES

import os, urllib, MySQLdb
import sqlalchemy.pool as pool
from flask import Flask, request
from oauth2client import client, crypt

# These environment variables are configured in app.yaml.
CLOUD_SQL_CONNECTION_NAME = os.environ.get('CLOUD_SQL_CONNECTION_NAME')

CLOUD_SQL_APP_USER = os.environ.get('CLOUD_SQL_APP_USER')
CLOUD_SQL_APP_PASSWORD = os.environ.get('CLOUD_SQL_APP_PASSWORD')

CLOUD_SQL_SOCKET_USER = os.environ.get('CLOUD_SQL_SOCKET_USER')
CLOUD_SQL_SOCKET_PASSWORD = os.environ.get('CLOUD_SQL_SOCKET_PASSWORD')

CLOUD_SQL_DATABASE_NAME = os.environ.get('CLOUD_SQL_DATABASE_NAME')

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
    print(e)
    return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

# taken from here https://cloud.google.com/appengine/docs/standard/python/cloud-sql/#setting-up
def get_cloud_sql_connection_app_no_autocommit():
    # When deployed to App Engine, the `SERVER_SOFTWARE` environment variable
    # will be set to 'Google App Engine/version'.
    if os.getenv('SERVER_SOFTWARE', '').startswith('Google App Engine/'):
        # Connect using the unix socket located at
        # /cloudsql/cloudsql-connection-name.
        cloudsql_unix_socket = os.path.join('/cloudsql', CLOUD_SQL_CONNECTION_NAME)
        conn = MySQLdb.connect(unix_socket=cloudsql_unix_socket, user=CLOUD_SQL_APP_USER, passwd=CLOUD_SQL_APP_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=False)

    # If the unix socket is unavailable, then try to connect using TCP. This
    # will work if you're running a local MySQL server or using the Cloud SQL
    # proxy, for example:
    #
    #   $ cloud_sql_proxy -instances=your-connection-name=tcp:3306
    #
    else:
        conn = MySQLdb.connect(host='127.0.0.1', user=CLOUD_SQL_APP_USER, passwd=CLOUD_SQL_APP_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=False)

    return conn

# taken from here https://cloud.google.com/appengine/docs/standard/python/cloud-sql/#setting-up
def get_cloud_sql_connection_app_autocommit():
    # When deployed to App Engine, the `SERVER_SOFTWARE` environment variable
    # will be set to 'Google App Engine/version'.
    if os.getenv('SERVER_SOFTWARE', '').startswith('Google App Engine/'):
        # Connect using the unix socket located at
        # /cloudsql/cloudsql-connection-name.
        cloudsql_unix_socket = os.path.join('/cloudsql', CLOUD_SQL_CONNECTION_NAME)
        conn = MySQLdb.connect(unix_socket=cloudsql_unix_socket, user=CLOUD_SQL_APP_USER, passwd=CLOUD_SQL_APP_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=True)

    # If the unix socket is unavailable, then try to connect using TCP. This
    # will work if you're running a local MySQL server or using the Cloud SQL
    # proxy, for example:
    #
    #   $ cloud_sql_proxy -instances=your-connection-name=tcp:3306
    #
    else:
        conn = MySQLdb.connect(host='127.0.0.1', user=CLOUD_SQL_APP_USER, passwd=CLOUD_SQL_APP_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=True)

    return conn

 # taken from here https://cloud.google.com/appengine/docs/standard/python/cloud-sql/#setting-up
def get_cloud_sql_connection_socket_no_autocommit():
    # When deployed to App Engine, the `SERVER_SOFTWARE` environment variable
    # will be set to 'Google App Engine/version'.
    if os.getenv('SERVER_SOFTWARE', '').startswith('Google App Engine/'):
        # Connect using the unix socket located at
        # /cloudsql/cloudsql-connection-name.
        cloudsql_unix_socket = os.path.join('/cloudsql', CLOUD_SQL_CONNECTION_NAME)
        conn = MySQLdb.connect(unix_socket=cloudsql_unix_socket, user=CLOUD_SQL_SOCKET_USER, passwd=CLOUD_SQL_SOCKET_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=False)

    # If the unix socket is unavailable, then try to connect using TCP. This
    # will work if you're running a local MySQL server or using the Cloud SQL
    # proxy, for example:
    #
    #   $ cloud_sql_proxy -instances=your-connection-name=tcp:3306
    #
    else:
        conn = MySQLdb.connect(host='127.0.0.1', user=CLOUD_SQL_SOCKET_USER, passwd=CLOUD_SQL_SOCKET_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=False)

    return conn

 # taken from here https://cloud.google.com/appengine/docs/standard/python/cloud-sql/#setting-up
def get_cloud_sql_connection_socket_autocommit():
    # When deployed to App Engine, the `SERVER_SOFTWARE` environment variable
    # will be set to 'Google App Engine/version'.
    if os.getenv('SERVER_SOFTWARE', '').startswith('Google App Engine/'):
        # Connect using the unix socket located at
        # /cloudsql/cloudsql-connection-name.
        cloudsql_unix_socket = os.path.join('/cloudsql', CLOUD_SQL_CONNECTION_NAME)
        conn = MySQLdb.connect(unix_socket=cloudsql_unix_socket, user=CLOUD_SQL_SOCKET_USER, passwd=CLOUD_SQL_SOCKET_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=True)

    # If the unix socket is unavailable, then try to connect using TCP. This
    # will work if you're running a local MySQL server or using the Cloud SQL
    # proxy, for example:
    #
    #   $ cloud_sql_proxy -instances=your-connection-name=tcp:3306
    #
    else:
        conn = MySQLdb.connect(host='127.0.0.1', user=CLOUD_SQL_SOCKET_USER, passwd=CLOUD_SQL_SOCKET_PASSWORD, db=CLOUD_SQL_DATABASE_NAME, autocommit=True)

    return conn

#http://docs.sqlalchemy.org/en/latest/core/pooling.html#constructing-a-pool
#https://stackoverflow.com/questions/9998805/what-happens-when-a-connection-pool-is-exhausted
#https://bitbucket.org/zzzeek/sqlalchemy/src/master//lib/sqlalchemy/pool.py?fileviewer=file-view-default
socket_autocommit_pool = pool.QueuePool(get_cloud_sql_connection_socket_autocommit, max_overflow=-1, pool_size=32)
socket_no_autocommit_pool = pool.QueuePool(get_cloud_sql_connection_socket_no_autocommit, max_overflow=-1, pool_size=32)
app_autocommit_pool = pool.QueuePool(get_cloud_sql_connection_app_autocommit, max_overflow=-1, pool_size=22)
app_no_autocommit_pool = pool.QueuePool(get_cloud_sql_connection_app_no_autocommit, max_overflow=-1, pool_size=22)

#another way is to use SQLAlchemy ORM (https://cloud.google.com/appengine/docs/flexible/python/using-cloud-sql)
# no need of above another way though since we are using raw connection pooling over MySQLdb anyways and sqlalchemy allows to have a custom conneciton method as shown above which is necessary for Google Cloud SQL and we can customize parameters as well like autocommit, db user and pw by making different connections and pools

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

# allow POST via HTTPS as it is called from mobile apps while Internet Mode Config (Registration)
# allow PUT only via HTTPS if source=app and google_oauth_id_token is mandatory (do validation) as we are writing the future state of the socket and it must be written by an authenticated/authorized source
# allow PUT via HTTP if source=socket and google_oauth_id_token is optional (no validation required) as we are just updating the state for user to view it (same as read only)
@app.route('/app/api/v1.0/sockets_per_user', methods = ['POST', 'PUT'])
def add_update_sockets_per_user_for_app():
    try:
        conn = app_no_autocommit_pool.connect()
        cursor = conn.cursor()

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
                    cursor.execute('insert into sockets_per_user (email, socket_name, external_wifi_ssid, socket_software_version, current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source, last_updated_systime) values (%s, %s, %s, %s, %s, %s, %s, %s, CAST(%s AS UNSIGNED), %s, %s, %s, CURRENT_TIMESTAMP)', (email, socket_name, external_wifi_ssid, socket_software_version, current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source))
                elif request.method == 'PUT':
                    cursor.execute('update sockets_per_user set current_desired_state = %s, timezone_details = %s, running_timer_type = %s, running_timer_cron_mask_config_string = %s, running_timer_secs_left = CAST(%s AS UNSIGNED), last_updated_datetime = %s, last_updated_by = %s, last_updated_by_device_source = %s, last_updated_systime = CURRENT_TIMESTAMP where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s', (current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source, email, socket_name, external_wifi_ssid, socket_software_version))

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

    except MySQLdb.IntegrityError as ie:
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
def sockets_per_user_status_for_app():
    try:
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = urllib.unquote_plus(request.values['email'])
        socket_name = urllib.unquote_plus(request.values['socket_name'])
        external_wifi_ssid = urllib.unquote_plus(request.values['external_wifi_ssid'])
        socket_software_version = int(request.values['socket_software_version'])
        google_oauth_id_token = request.values['google_oauth_id_token']
        status_requester_email = urllib.unquote_plus(request.values['status_requester_email'])

        if google_oauth_id_token and validate_google_id_token(google_oauth_id_token, status_requester_email):
            socket_status_read_connection_for_app = app_autocommit_pool.connect()
            socket_status_read_cursor_for_app = socket_status_read_connection_for_app.cursor()

            socket_status_read_cursor_for_app.execute('select current_desired_state, timezone_details, running_timer_type, running_timer_cron_mask_config_string, running_timer_secs_left, last_updated_datetime, last_updated_by, last_updated_by_device_source from sockets_per_user where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s', (email, socket_name, external_wifi_ssid, socket_software_version))
            row = socket_status_read_cursor_for_app.fetchone()
        
            if row:
                return '~'.join(map(lambda x:str(int(time.mktime(x.timetuple()))) if type(x) is datetime else str(x), row)), 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            else:
                return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "oauth_validation_failed", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print("exception in sockets_per_user_status endpoint: ", e)
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        socket_status_read_cursor_for_app.close()
        socket_status_read_connection_for_app.close()

# allow POST via HTTPS as it is called from mobile apps while Internet Mode Config (Registration)
# allow PUT only via HTTPS if source=app and google_oauth_id_token is mandatory (do validation) as we are writing the future state of the socket and it must be written by an authenticated/authorized source
# allow PUT via HTTP if source=socket and google_oauth_id_token is optional (no validation required) as we are just updating the state for user to view it (same as read only)
@app.route('/socket/api/v1.0/sockets_per_user', methods = ['PUT'])
def update_sockets_per_user_for_socket():
    try:
        conn = socket_no_autocommit_pool.connect()
        cursor = conn.cursor()

        # request.data is 16 chars IV [A-Z] immediately followed by encrypted text
        input_values = AES.new('PwS0cKEtDaTA!92$', AES.MODE_CBC, IV=request.data[:16]).decrypt(binascii.unhexlify(request.data[16:])).split('~')

        input_values = list(map(lambda element:str.strip(element, '\x00'), input_values))
        # time must be in UTC instead of local timezone as Java in Android needs it in UTC for Date() constructor
        input_values[5] = datetime.utcnow().strftime("%s")
        if len(input_values) == 6:
            input_values += [None, None, None]
        # sample with no running timer  ['ritesht93@gmail.com', 'Fan', 'TripMate-94AA', '0', '019800', '1503261408', None, None, None]
        # sample with running timer     ['ritesht93@gmail.com', 'Fan', 'TripMate-94AA', '2', '019800', '1503261408', 'osct', '1=1000=0', '500']

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
        if len(input_values) == 9:
            cursor.execute("update sockets_per_user set current_desired_state = %s, timezone_details = %s, running_timer_type = %s, running_timer_cron_mask_config_string = %s, running_timer_secs_left = CAST(%s AS UNSIGNED), last_updated_datetime = %s, last_updated_by_device_source = 'socket', last_updated_systime = CURRENT_TIMESTAMP where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s", (input_values[3], input_values[4], input_values[6], input_values[7], input_values[8], input_values[5], input_values[0], input_values[1], input_values[2], '1'))

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
def sockets_per_user_status_for_socket():
    try:
        # https://stackoverflow.com/questions/10434599/how-to-get-data-received-in-flask-request
        email = binascii.unhexlify(request.values['email'])
        socket_name = urllib.unquote_plus(request.values['socket_name'])
        external_wifi_ssid = binascii.unhexlify(request.values['external_wifi_ssid'])
        #socket_software_version = request.values['socket_software_version']
        socket_software_version = '1'

        socket_status_read_connection_for_socket = socket_autocommit_pool.connect()
        socket_status_read_cursor_for_socket = socket_status_read_connection_for_socket.cursor()

        socket_status_read_cursor_for_socket.execute("select current_desired_state from sockets_per_user where email = %s and socket_name = %s and external_wifi_ssid = %s and socket_software_version = %s and last_updated_by_device_source = 'app'", (email, socket_name, external_wifi_ssid, socket_software_version))
        row = socket_status_read_cursor_for_socket.fetchone()
        
        if row:
            #https://timezones.appspot.com/
            #https://stackoverflow.com/questions/2331592/why-does-datetime-datetime-utcnow-not-contain-timezone-information
            #https://stackoverflow.com/questions/17976063/how-to-create-tzinfo-when-i-have-utc-offset
            #return row[0]+datetime.utcnow().strftime("%s"), 200, {'Content-Type' : 'text/plain; charset = utf-8'}
            return row[0], 200, {'Content-Type' : 'text/plain; charset = utf-8'}
        else:
            return "no_update_from_app", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    except Exception as e:
        print(e)
        return "error", 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    #return str(len(return_string)) + "\r\n" + encrypt_sockets_per_user_status(return_string), 200, {'Content-Type' : 'text/plain; charset = utf-8'}

    finally:
        socket_status_read_cursor_for_socket.close()
        socket_status_read_connection_for_socket.close()
