import json
import sys
import requests


def timestampComparator(e):
    return e['properties']['timestamp']


def getAllDeployment(in_resourceGroup, in_token):
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Resources/deployments?api-version=2017-05-10".format(in_resourceGroup)
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.get(in_resources, headers=headers)
    services = json.loads(rsp.text)
    dictList = []
    for item in services['value'] :
            dictList.append(item);
    dictList.sort(key=timestampComparator)
    return dictList;


in_resourceGroup = sys.argv[1];
in_keepDeployments = int(sys.argv[4]);
in_token = sys.argv[5]

print("[TRACE] Resource Group:", in_resourceGroup)
print("[TRACE] Module:", in_module)
print("[TRACE] Environment:", in_env)
print("[TRACE] Versions to keep:", in_keepDeployments)
print("[TRACE] Token:", in_token)

"""
in_resourceGroup = "customer-uw-rg-d";
in_keepDeployments = 700;
in_token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6IllNRUxIVDBndmIwbXhvU0RvWWZvbWpxZmpZVSIsImtpZCI6IllNRUxIVDBndmIwbXhvU0RvWWZvbWpxZmpZVSJ9.eyJhdWQiOiJodHRwczovL21hbmFnZW1lbnQuY29yZS53aW5kb3dzLm5ldC8iLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC9kZGI4NTkyZi1lZDc0LTQzMjktYmZkMi01NzFiN2MzNDY5OWUvIiwiaWF0IjoxNTg3NDI5NDAxLCJuYmYiOjE1ODc0Mjk0MDEsImV4cCI6MTU4NzQzMzMwMSwiYWNyIjoiMSIsImFpbyI6IjQyZGdZRmo0TDZqOWNrV3I3Z1g5bEpvSWdRZE1JYS85Wjd2bk9QMWhhUTNjUEhkRnVDRUEiLCJhbXIiOlsicHdkIl0sImFwcGlkIjoiN2Y1OWE3NzMtMmVhZi00MjljLWEwNTktNTBmYzViYjI4YjQ0IiwiYXBwaWRhY3IiOiIyIiwiZmFtaWx5X25hbWUiOiJDaGF0dG9wYWRoeWF5IiwiZ2l2ZW5fbmFtZSI6IlJpdHdpY2siLCJncm91cHMiOlsiODgzODYwMzItM2E1MS00NDE1LWIxN2ItMWExMjRiMDJjYTg4IiwiZTJjMTg4NTEtZWZhMC00MjZmLWI1OTktNzAwNmUwNzZmMDlkIiwiMjk5OWNhNTUtMTc5ZS00NThmLWJlMTktZjA4MzUyYjBmY2I3IiwiYTNhM2U4Y2ItOTc0OC00YTNmLTk0OTItZGMyMGM1MTIyMGZlIiwiYmU1OWYyZTgtNWViMy00M2ZjLWEzNTctOTIyOGVkZGE2N2MwIiwiNDg0MTBhZTUtNjlmNy00M2U3LWFkNmUtMDY3ODFmZGEzNDM3IiwiYWVhMmViZmMtMWIxMy00YjkzLWJiODctNzljOTc4NjcxYTY4Il0sImlwYWRkciI6IjY5LjE2Mi4xLjU2IiwibmFtZSI6IkNoYXR0b3BhZGh5YXksIFJpdHdpY2siLCJvaWQiOiI0ODgzOGE0YS00NzQ1LTQxYTAtYWZmNi02YjZiMGU2NTEzNTIiLCJvbnByZW1fc2lkIjoiUy0xLTUtMjEtMTkzNTY1NTY5Ny0yMTExNjg3NjU1LTcyNTM0NTU0My0xNjI4Nzc3IiwicHVpZCI6IjEwMDMyMDAwNkI3Mzk3Q0QiLCJzY3AiOiJ1c2VyX2ltcGVyc29uYXRpb24iLCJzdWIiOiJuMi1aY3RTeUp0RjQ0VmVpbzlpaTNub0lBRUsxRVRncmNqU0lKLTlsVHpVIiwidGlkIjoiZGRiODU5MmYtZWQ3NC00MzI5LWJmZDItNTcxYjdjMzQ2OTllIiwidW5pcXVlX25hbWUiOiJBWi5SQ0hBVFRPUEFEQG1nbXJlc29ydHMuY29tIiwidXBuIjoiQVouUkNIQVRUT1BBREBtZ21yZXNvcnRzLmNvbSIsInV0aSI6InB4STlvdGppQ0V5R19iVmxaMFVDQUEiLCJ2ZXIiOiIxLjAifQ.OX9xfT77eHYKFw0-0b1yO1wjIvy9qIkWgo20KeXM8OLhMdkUHBSTKNDpPopyGtzYbw95F7WJgcmepITNOLP1afAF9DwSZgxPvgdNpeGN6YEPZATcJP9M0QdZPWQHCidE1nrS-9kUTgtr1vdFqAvvF6_10YvcYu7ssAP18VnqWK77gDvqyAqqWYxlQOvOwFxyrZGM9kn1sw2GPo0MhIYIvlCzKfHFNJau8FKpWIKkB68W1k1QvqSGR8xVWOrPzO5o_jeEm_zcf9JDQQ918hYdUFD8yB7qOJuhxeFv3rrN7Y-is8gCt3TQHxZ45VUUVvQsJ94QqyRRb5lOMCTAEL9m2g"
"""
# Get all the functions
instances = getAllDeployment(in_resourceGroup, in_token)

# remove active applications
if(len(instances) > in_keepDeployments) :
    instances = instances[:len(instances) - in_keepDeployments]
else:
    instances = []

# Cleanup resources
print("[DEBUG] ........................");
print("[DEBUG] Initiating deployment cleanup process");
print("[DEBUG] ........................");

for removable in instances:
    print("\t[DEBUG]Starting cleanup for function", removable);
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Resources/deployments/{}?api-version=2017-05-10".format(in_resourceGroup, removable)
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.delete(in_resources, headers=headers)
    print("Function: Cleanup process completed with response ", rsp);
    
print("[DEBUG] Completed cleanup process");
