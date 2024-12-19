param (
    $resourceGroup,
    $appName,
    $subscription = "na",
	$secondarySlotName = "staging",
	$version
)
az login --identity
az account set --subscription "$subscription"

$appServiceExists="true"
$appServiceSlotExists="true"
$slotName=$secondarySlotName
$functionAppName=az functionapp list --resource-group $resourceGroup --query "[?name=='$appName']" -o tsv
if($functionAppName -eq $null) {
	$appServiceExists="false"
	$appServiceSlotExists="false"
} else {
$functionAppSlotName=az functionapp deployment slot list --name $appName --resource-group $resourceGroup --subscription $subscription --query "[?name=='$slotName']" -o tsv
if($functionAppSlotName -eq $null) {
	$appServiceSlotExists="false"
}}

# ******************************* set parameters as environment variables ***********************************
Write-Host "##vso[task.setvariable variable=APP_SERVICE_EXISTS]$appServiceExists"
Write-Host "##vso[task.setvariable variable=APP_SERVICE_SLOT_EXISTS]$appServiceSlotExists"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_SLOT_NAME]$slotName"

# ******************************* Prints variables **********************************************************
Write-Host "App Service Exists :" $appServiceExists
Write-Host "App Service Slot Exists :" $appServiceSlotExists
Write-Host "Slot Name          :" $slotName