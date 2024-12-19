# Make outputs from resource group deployment available to subsequent tasks

#Parameter/variables declarations
param (
    $dbName,
    $resourceGroup,
    $keyvaultname,
    $secretName
)

#Install-Module -Name az.keyvault -Force -AllowClobber

# List keys for an Azure Cosmos Account
$keys = Invoke-AzResourceAction -Action listKeys `
    -ResourceType "Microsoft.DocumentDb/databaseAccounts" -ApiVersion "2015-04-08" `
    -ResourceGroupName "$resourceGroup" -Name "$dbName" -Force

$primaryMasterKey = $keys.primaryMasterKey
#write-host "Key: $primaryMasterKey"

$secret = ConvertTo-SecureString -String "$primaryMasterKey" -AsPlainText -Force
Set-AzKeyVaultSecret -VaultName "$keyvaultname" -Name "$secretName" -SecretValue $secret