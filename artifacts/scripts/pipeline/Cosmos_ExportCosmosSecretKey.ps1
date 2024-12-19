param (
    $dbName,
    $resourceGroup,
    $appName,
    $subscription = "na"
)
az login --identity
az account set --subscription "$subscription"

$keys = "$(az cosmosdb keys list --name $dbName --resource-group $resourceGroup --subscription "$subscription" --type keys --query "primaryMasterKey" --output tsv)"
write-host "Key: $keys"
az functionapp config appsettings set -n $appName -g $resourceGroup --settings "datastore.cosmos.secret=$keys"