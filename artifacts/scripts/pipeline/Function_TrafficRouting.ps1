param (
    $resourceGroup,
    $webAppName,
    [string]$secondarySlotName,
    [int]$distributionPercentage
)

$slotName=$secondarySlotName 
az webapp traffic-routing set --distribution $slotName=$distributionPercentage --name $webAppName  --resource-group $resourceGroup

# ******************************* set parameters as environment variables ***********************************
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_SLOT_NAME]$slotName"

# ******************************* Prints variables **********************************************************
Write-Host "Slot Name          :" $slotName