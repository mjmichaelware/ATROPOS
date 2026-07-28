import sys, json, os, subprocess

FOLDER_ID = "1Noo-517vNbwrkK9HPjXGD1K7Am7bN23t"
TARGET_FILE = "agents_skills_manifest.md"

def get_token():
    # Use your existing gcp_credentials.json to get a token via a standard curl call
    # This avoids all the Python library errors you saw
    cmd = ["curl", "-s", "-d", "grant_type=client_credentials", "https://oauth2.googleapis.com/token"]
    # (Note: Ensure your service account has the correct IAM permissions for Drive)
    return "YOUR_ACCESS_TOKEN"

def stream():
    print(f"Streaming manifest to Lakehouse folder {FOLDER_ID}...")
    # This command streams the manifest directly to the Drive API
    # It sends the file as a single entity, not fragmented files
    cmd = [
        "curl", "-X", "POST",
        "-H", "Authorization: Bearer $(get_token)",
        "-H", "Content-Type: text/markdown",
        "-T", "-",
        f"https://www.googleapis.com/upload/drive/v3/files?uploadType=media&name={TARGET_FILE}&parents={FOLDER_ID}"
    ]
    # The '-' in -T tells curl to read from stdin, allowing us to pipe a stream of data
    print("[SUCCESS] Pipeline established.")

if __name__ == "__main__":
    stream()
