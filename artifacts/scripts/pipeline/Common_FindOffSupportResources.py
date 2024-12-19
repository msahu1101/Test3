import json
import sys
import requests


def lastUpdateDateComparator(e):
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Web/sites/{}/deployments?api-version=2018-02-01".format(in_resourceGroup, e['name'])
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.get(in_resources, headers=headers)
    services = json.loads(rsp.text)
    deployments = []
    print("[DEBUG] Function application", e['name'], "deployment times: ");
    for item in services['value'] :
        print("\t[DEBUG]", item['properties']['last_success_end_time']);
        deployments.append(item['properties']['last_success_end_time']);
    deployments.sort()
    mostRecentDeployment = deployments[len(deployments) - 1]
    print("\t[DEBUG]", mostRecentDeployment, "[RECENT]");
    return mostRecentDeployment


def getFunctionsFromAzure(in_resourceGroup, in_module, in_env, in_token):
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Web/sites?api-version=2018-02-01".format(in_resourceGroup)
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.get(in_resources, headers=headers)
    services = json.loads(rsp.text)
    dictList = []
    for item in services['value'] :
        if(item['type'] == "Microsoft.Web/sites" and item['name'].startswith(in_module) and item['name'].endswith("-uw-fa-" + in_env)):
            dictList.append(item);
    dictList.sort(key=lastUpdateDateComparator)
    return dictList;


in_resourceGroup = sys.argv[1];
in_module = sys.argv[2];
in_env = sys.argv[3];
in_keepVersions = int(sys.argv[4]);
in_token = sys.argv[5]

print("[TRACE] Resource Group:", in_resourceGroup)
print("[TRACE] Module:", in_module)
print("[TRACE] Environment:", in_env)
print("[TRACE] Versions to keep:", in_keepVersions)
print("[TRACE] Token:", in_token)

"""
in_resourceGroup = "customer-uw-rg-d";
in_module = "mgmli";
in_env = "d";
in_keepVersions = 3;
in_token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6IllNRUxIVDBndmIwbXhvU0RvWWZvbWpxZmpZVSIsImtpZCI6IllNRUxIVDBndmIwbXhvU0RvWWZvbWpxZmpZVSJ9.eyJhdWQiOiJodHRwczovL21hbmFnZW1lbnQuY29yZS53aW5kb3dzLm5ldC8iLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC9kZGI4NTkyZi1lZDc0LTQzMjktYmZkMi01NzFiN2MzNDY5OWUvIiwiaWF0IjoxNTg3NDIwOTkzLCJuYmYiOjE1ODc0MjA5OTMsImV4cCI6MTU4NzQyNDg5MywiYWNyIjoiMSIsImFpbyI6IjQyZGdZSEFyLzJGVCszbmlHcHV6OWNLUEdXSXE1cG52cUtnbzJWekh0ajExUnZReTVVNEEiLCJhbXIiOlsicHdkIl0sImFwcGlkIjoiN2Y1OWE3NzMtMmVhZi00MjljLWEwNTktNTBmYzViYjI4YjQ0IiwiYXBwaWRhY3IiOiIyIiwiZmFtaWx5X25hbWUiOiJDaGF0dG9wYWRoeWF5IiwiZ2l2ZW5fbmFtZSI6IlJpdHdpY2siLCJncm91cHMiOlsiODgzODYwMzItM2E1MS00NDE1LWIxN2ItMWExMjRiMDJjYTg4IiwiZTJjMTg4NTEtZWZhMC00MjZmLWI1OTktNzAwNmUwNzZmMDlkIiwiMjk5OWNhNTUtMTc5ZS00NThmLWJlMTktZjA4MzUyYjBmY2I3IiwiYTNhM2U4Y2ItOTc0OC00YTNmLTk0OTItZGMyMGM1MTIyMGZlIiwiYmU1OWYyZTgtNWViMy00M2ZjLWEzNTctOTIyOGVkZGE2N2MwIiwiNDg0MTBhZTUtNjlmNy00M2U3LWFkNmUtMDY3ODFmZGEzNDM3IiwiYWVhMmViZmMtMWIxMy00YjkzLWJiODctNzljOTc4NjcxYTY4Il0sImlwYWRkciI6IjY5LjE2Mi4xLjU2IiwibmFtZSI6IkNoYXR0b3BhZGh5YXksIFJpdHdpY2siLCJvaWQiOiI0ODgzOGE0YS00NzQ1LTQxYTAtYWZmNi02YjZiMGU2NTEzNTIiLCJvbnByZW1fc2lkIjoiUy0xLTUtMjEtMTkzNTY1NTY5Ny0yMTExNjg3NjU1LTcyNTM0NTU0My0xNjI4Nzc3IiwicHVpZCI6IjEwMDMyMDAwNkI3Mzk3Q0QiLCJzY3AiOiJ1c2VyX2ltcGVyc29uYXRpb24iLCJzdWIiOiJuMi1aY3RTeUp0RjQ0VmVpbzlpaTNub0lBRUsxRVRncmNqU0lKLTlsVHpVIiwidGlkIjoiZGRiODU5MmYtZWQ3NC00MzI5LWJmZDItNTcxYjdjMzQ2OTllIiwidW5pcXVlX25hbWUiOiJBWi5SQ0hBVFRPUEFEQG1nbXJlc29ydHMuY29tIiwidXBuIjoiQVouUkNIQVRUT1BBREBtZ21yZXNvcnRzLmNvbSIsInV0aSI6InJERXlNZmkya1VLZzRGYkdjN2NuQUEiLCJ2ZXIiOiIxLjAifQ.BqHuEXyEG37OzzLNoVjZ_b3GtJ2FnxsXvVIxkJVudzKnCtIixTyXds20fVX8SNHs8xi_5O5ki1FQ8K5aM9LswJZ2nrQks4tjqBLuuh4KLkdmkJo4hIOetpkcC9hhEekUuG5U2kV_jAerHHuuG-hmKpZOc31lvGH3-0wi7kJot74ojda6L5XjWMrglssfve1kdNZg_vw5IHr_i5mivE8ARxUBZPSptUA6FAOZGcL8oei8Cy-ei5U4-GBez0Qc6OnK6sVWB2x2_elqdkeguYEtL87-CPl7mMsL-Hjd8I5ZGUn8oHwLMH9jOfO7JEwJ-lBnWPD6bLMO1z6lhUXGJ6u6Tw"
"""
# Get all the functions
instances = getFunctionsFromAzure(in_resourceGroup, in_module, in_env, in_token)

removableFunctions = []
removableStorageAccounts = []

# remove active applications
if(len(instances) > in_keepVersions) :
    instances = instances[:len(instances) - in_keepVersions]
else:
    instances = []
# find storage account
print("[DEBUG] Resources to be deleted");
    
for instance in instances:
    removableFunctions.append(instance["name"])
    print("\t[DEBUG]Function:", instance["name"]);
    
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Web/sites/{}/config/appsettings/list?api-version=2018-02-01".format(in_resourceGroup, instance['name'])
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.post(in_resources, headers=headers)
    config = json.loads(rsp.text)
    storage = config['properties']["AzureWebJobsStorage"]
    params = storage.split(";")
    for param in params:
        if(param.startswith("AccountName")):
            storageName = param.split("=")[1]
            removableStorageAccounts.append(storageName);
            print("\t[DEBUG]Storage:", storageName);
            break 
    
# Cleanup resources
print("[DEBUG] ........................");
print("[DEBUG] Initiating cleanup process");
print("[DEBUG] ........................");

for removable in removableFunctions:
    print("\t[DEBUG]Starting cleanup for function", removable);
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Web/sites/{}?api-version=2018-02-01".format(in_resourceGroup, removable)
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.delete(in_resources, headers=headers)
    print("Function: Cleanup process completed with response ", rsp);

for removable in removableStorageAccounts:
    print("\t[DEBUG]Starting cleanup for storage", removable);
    in_resources = "https://management.azure.com/subscriptions/e9d2d224-1163-4cd2-a663-e216b16c797f/resourceGroups/{}/providers/Microsoft.Storage/storageAccounts/{}?api-version=2017-10-01".format(in_resourceGroup, removable)
    headers = { 'Authorization' : 'Bearer ' + in_token}
    rsp = requests.delete(in_resources, headers=headers)
    print("Storage: Cleanup process completed with response ", rsp);
    
print("[DEBUG] Completed cleanup process");