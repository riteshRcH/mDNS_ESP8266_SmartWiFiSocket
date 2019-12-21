# run as root user of mysql

drop database unify_smart_wifi_socket;

create database unify_smart_wifi_socket;

use unify_smart_wifi_socket;

create table sockets_per_user
(email varchar(191),
socket_name varchar(64),
external_wifi_ssid varchar(32),
socket_software_version int,
current_desired_state varchar(64) not null,
timezone_details varchar(256) not null,
running_timer_type varchar(64),
running_timer_cron_mask_config_string varchar(64),
running_timer_secs_left bigint,
last_updated_datetime bigint not null,
last_updated_by varchar(254) not null,
last_updated_by_device_source varchar(64) not null,
last_updated_systime timestamp default CURRENT_TIMESTAMP,
primary key (email, socket_name, external_wifi_ssid, socket_software_version));

# create table file_hashes_per_version
# (version varchar(32),
# filename varchar(32),
# hash varchar(512),
# primary key (version, filename));

# https://stackoverflow.com/questions/598190/mysql-check-if-the-user-exists-and-drop-it
GRANT USAGE ON *.* TO 'app'@'%' IDENTIFIED BY 'passwordForApPDBUserYo1259!';
DROP USER 'app'@'%';

CREATE USER 'app'@'%' IDENTIFIED BY 'passwordForApPDBUserYo1259!';
GRANT SELECT, INSERT, UPDATE ON unify_smart_wifi_socket.sockets_per_user TO 'app'@'%';

# https://stackoverflow.com/questions/598190/mysql-check-if-the-user-exists-and-drop-it
GRANT USAGE ON *.* TO 'socket'@'%' IDENTIFIED BY 'passwordForSoCkeTDBUserYo1259!';
DROP USER 'socket'@'%';

CREATE USER 'socket'@'%' IDENTIFIED BY 'passwordForSoCkeTDBUserYo1259!';
GRANT SELECT, UPDATE ON unify_smart_wifi_socket.sockets_per_user TO 'socket'@'%';
