"""Check/create only workforce indexes; never delete indexes, rules or documents.

Uses the existing gcloud login, without storing or displaying access tokens.
REST contract: https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.collectionGroups.indexes
"""
import argparse
import json
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

COLLECTIONS = {"workforceAttendance", "workforceTimeEvents", "workforceClockRecoveries"}


def signature(index):
    fields = tuple((f["fieldPath"], f.get("order"), f.get("arrayConfig"))
                   for f in index["fields"] if f["fieldPath"] != "__name__")
    return index["queryScope"], fields


def desired_indexes():
    manifest = Path(__file__).resolve().parent.parent / "firestore.indexes.json"
    selected = [i for i in json.loads(manifest.read_text(encoding="utf-8"))["indexes"]
                if i["collectionGroup"] in COLLECTIONS]
    if len(selected) != 4:
        raise RuntimeError("Expected the four reviewed workforce indexes in the manifest.")
    return selected


class Client:
    def __init__(self, project):
        gcloud = shutil.which("gcloud") or shutil.which("gcloud.cmd")
        if not gcloud:
            raise RuntimeError("Install Google Cloud CLI and run gcloud auth login on your PC.")
        result = subprocess.run([gcloud, "auth", "print-access-token", "--quiet"],
                                capture_output=True, text=True, timeout=60)
        if result.returncode or not result.stdout.strip():
            raise RuntimeError("No authenticated gcloud session. Run gcloud auth login.")
        self.token = result.stdout.strip()
        self.project = project
        self.root = f"projects/{project}/databases/(default)/collectionGroups/"

    def request(self, path, body=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request("https://firestore.googleapis.com/v1/" + path,
                                     data=data, headers={"Authorization": "Bearer " + self.token,
                                     "Content-Type": "application/json", "X-Goog-User-Project": self.project})
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)

    def list(self, collection):
        indexes, page = [], ""
        while True:
            # Firestore currently rejects nonzero pageSize. Use its default
            # and preserve any continuation token returned by the service.
            query = urllib.parse.urlencode({"pageToken": page}) if page else ""
            result = self.request(self.root + collection + "/indexes" + ("?" + query if query else ""))
            # The live API can return indexes from other collection groups.
            # A matching field signature there does not satisfy this query.
            prefix = self.root + collection + "/indexes/"
            indexes.extend(i for i in result.get("indexes", [])
                           if i.get("name", "").startswith(prefix))
            page = result.get("nextPageToken", "")
            if not page:
                return indexes

    def create(self, index):
        body = {"queryScope": index["queryScope"], "fields": index["fields"]}
        try:
            self.request(self.root + index["collectionGroup"] + "/indexes", body)
        except urllib.error.HTTPError as error:
            if error.code != 409:  # A concurrent create is checked by the next listing.
                raise


def prepare(client, indexes, apply=False, wait_seconds=600, sleep=time.sleep, clock=time.monotonic):
    deadline = clock() + wait_seconds
    requested = set()
    while True:
        actual = {c: client.list(c) for c in sorted(COLLECTIONS)}
        pending = []
        for index in indexes:
            key = (index["collectionGroup"], signature(index))
            found = next((i for i in actual[key[0]] if signature(i) == key[1]), None)
            state = found.get("state", "UNKNOWN") if found else "MISSING"
            print(f"{key[0]} {','.join(f['fieldPath'] for f in index['fields'])}: {state}", flush=True)
            if state == "NEEDS_REPAIR":
                raise RuntimeError("Index needs repair. Review it in the Firestore console.")
            if state != "READY":
                pending.append(key)
            if found is None and apply and key not in requested:
                client.create(index)
                requested.add(key)
        if not pending:
            print("All four workforce indexes are READY.")
            return 0
        if not apply:
            print("Not ready. Rerun with --apply to create missing indexes and wait.")
            return 2
        remaining = deadline - clock()
        if remaining <= 0:
            raise RuntimeError("Readiness timeout. Index builds continue; rerun this command to check.")
        sleep(min(10, remaining))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True, help="Explicit Firebase project ID; no default project is used.")
    parser.add_argument("--apply", action="store_true", help="Create missing indexes and wait until READY.")
    parser.add_argument("--wait-seconds", type=int, default=600)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", args.project) or args.project.startswith("demo-"):
        parser.error("Use an explicit real Firebase project ID, not an emulator project.")
    if not 1 <= args.wait_seconds <= 3600:
        parser.error("--wait-seconds must be between 1 and 3600.")
    try:
        return prepare(Client(args.project), desired_indexes(), args.apply, args.wait_seconds)
    except urllib.error.HTTPError as error:
        print(f"Firestore HTTP {error.code}. Check project, API access and datastore.indexes permissions.")
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError) as error:
        print(str(error))
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
