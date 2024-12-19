param (
    $Subscription,
    $FunctionsResourceGroup,
    $FunctionName,
    $StorageResourceGroup,
    $StorageName,
    $ConfigurationKey
)

connect-azaccount -identity
set-azcontext "$Subscription"

if ($StorageResourceGroup -eq $null) {
    Write-Host "StorageResourceGroup argument value not found. Adding FunctionsResourceGroup value within this........."
    $StorageResourceGroup=$FunctionsResourceGroup
}

$keys = az storage account show-connection-string --resource-group $StorageResourceGroup --name $StorageName --key primary
$json = $keys| ConvertFrom-Json 
$connectionString = $json.connectionString

az functionapp config appsettings set -n $FunctionName -g $FunctionsResourceGroup --settings "$ConfigurationKey=$connectionString"
