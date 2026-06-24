import sys, json, http.client, urllib.parse, os, base64, time, subprocess

TARGET_SIZE = 175 * 1024 * 1024
CHUNK_SIZE = 256 * 1024
FOLDER_ID = "1Noo-517vNbwrkK9HPjXGD1K7Am7bN23t"
CRED_PATH = os.path.expanduser("~/.atropos/gcp_credentials.json")

def b64(d): return base64.urlsafe_b64encode(d).decode('utf-8').replace('=', '')

def get_token():
    with open(CRED_PATH) as f: creds = json.load(f)
    header = b64(json.dumps({"alg":"RS256","typ":"JWT"}).encode())
    claim = b64(json.dumps({
        "iss": creds['client_email'], "scope": "https://www.googleapis.com/auth/drive",
        "aud": "https://oauth2.googleapis.com/token", "exp": int(time.time())+3600, "iat": int(time.time())
    }).encode())
    
    with open("tmp.pem", "w") as f: f.write(creds['private_key'])
    sig = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", "tmp.pem"], input=f"{header}.{claim}".encode())
    os.remove("tmp.pem")
    
    jwt = f"{header}.{claim}.{b64(sig)}"
    conn = http.client.HTTPSConnection("oauth2.googleapis.com")
    conn.request("POST", "/token", urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": jwt}), {"Content-Type": "application/x-www-form-urlencoded"})
    return json.loads(conn.getresponse().read())['access_token']

def stream():
    token = get_token()
    conn = http.client.HTTPSConnection("www.googleapis.com")
    # 1. Start Resumable Session
    meta = json.dumps({"name": "agents_skills_manifest.md", "parents": [FOLDER_ID]})
    conn.request("POST", "/upload/drive/v3/files?uploadType=resumable", meta, {"Authorization": f"Bearer {token}", "Content-Type": "application/json", "X-Upload-Content-Type": "text/markdown", "X-Upload-Content-Length": str(TARGET_SIZE)})
    upload_url = conn.getresponse().getheader("Location")
    conn.close()
    
    # 2. Upload Chunks
    parsed = urllib.parse.urlparse(upload_url)
    written = 0
    while written < TARGET_SIZE:
        conn = http.client.HTTPSConnection(parsed.netloc)
        chunk = ("# ATROPOS MANIFEST\n" * 1000).encode()[:CHUNK_SIZE]
        conn.request("PUT", parsed.path + "?" + parsed.query, chunk, {"Content-Range": f"bytes {written}-{written+len(chunk)-1}/{TARGET_SIZE}"})
        conn.getresponse()
        written += len(chunk)
        sys.stdout.write(f"\rIngesting: {written/1024/1024:.2f} MB / 175 MB")
        sys.stdout.flush()
    print("\n[SUCCESS] Document created in Lakehouse.")
    os.remove(__file__)

if __name__ == "__main__": stream()
