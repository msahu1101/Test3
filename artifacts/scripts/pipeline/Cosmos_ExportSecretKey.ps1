# Make outputs from resource group deployment available to subsequent tasks

#Parameter/variables declarations
param (
    $dbName,
    $resourceGroup,
    $AppName,
    $subscription,
    $slotName = "production"
)

#Install-Module -Name az.keyvault -Force -AllowClobber
connect-azaccount -identity
set-azcontext "$subscription"
# List keys for an Azure Cosmos Account
$keys = Invoke-AzResourceAction -Action listKeys `
    -ResourceType "Microsoft.DocumentDb/databaseAccounts" -ApiVersion "2015-04-08" `
    -ResourceGroupName "$resourceGroup" -Name "$dbName" -Force

$primaryMasterKey = $keys.primaryMasterKey
#write-host "Key: $primaryMasterKey"
az functionapp config appsettings set -n $AppName -g $resourceGroup --settings "datastore.cosmos.secret=$primaryMasterKey"
if($slotName -ne "production") 
{
    az functionapp config appsettings set -n $AppName -s $slotName -g $resourceGroup --settings "datastore.cosmos.secret=$primaryMasterKey"
}
#Write-Host "##vso[task.setvariable variable=TARGET_COSMOS_PRIMARY_MASTER_KEY]$primaryMasterKey"