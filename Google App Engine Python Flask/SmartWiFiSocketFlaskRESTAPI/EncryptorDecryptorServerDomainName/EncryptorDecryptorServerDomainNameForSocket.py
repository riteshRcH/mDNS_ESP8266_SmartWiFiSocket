#!/usr/bin/python3
#https://stackoverflow.com/questions/11629785/why-is-aes-decrypt-not-returning-my-original-text
from Crypto.Cipher import AES
import base64
key = 'put password here'
IV = 'IV'

def encrypt(plain_text):
    return base64.b64encode(AES.new(key, AES.MODE_CBC, IV=IV).encrypt(plain_text))

def decrypt(encrypted_base_64_encoded_text):
    return AES.new(key, AES.MODE_CBC, IV=IV).decrypt(base64.b64decode(encrypted_base_64_encoded_text))

server_domain_name_for_socket="http://10.10.10.4:9912".ljust(32)
encrypted_base_64_encoded_string = encrypt(server_domain_name_for_socket)
print("Original String: \t\t\t\t", server_domain_name_for_socket)
print("Encrypted Base64 encoded String: \t\t", encrypted_base_64_encoded_string)
print("Base64 decoded Decrypted String: \t\t", decrypt(encrypted_base_64_encoded_string))
