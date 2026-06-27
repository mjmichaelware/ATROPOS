import os
import sys
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

SCOPES = ['https://www.googleapis.com/auth/drive.file']
CLIENT_SECRET_FILE = os.path.expanduser('~/ATROPOS/client_secret.json')
TOKEN_FILE = os.path.expanduser('~/ATROPOS/token.json')
FOLDER_ID = '1Noo-517vNbwrkK9HPjXGD1K7Am7bN23t'

def authenticate_gdrive():
    creds = None
    if os.path.exists(TOKEN_FILE):
        creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(CLIENT_SECRET_FILE, SCOPES)
            creds = flow.run_local_server(port=8080, open_browser=False)
        with open(TOKEN_FILE, 'w') as token:
            token.write(creds.to_json())
    return build('drive', 'v3', credentials=creds)

def upload_file(drive_service, filepath):
    filename = os.path.basename(filepath)
    file_metadata = {'name': filename, 'parents': [FOLDER_ID]}
    media = MediaFileUpload(filepath, mimetype='text/plain')
    print(f"Uploading {filename} ...")
    file = drive_service.files().create(
        body=file_metadata, media_body=media, fields='id'
    ).execute()
    print(f"Success! File ID: {file.get('id')}")

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("Usage: python gdrive_upload.py <file_to_upload>")
        sys.exit(1)
    service = authenticate_gdrive()
    upload_file(service, sys.argv[1])
