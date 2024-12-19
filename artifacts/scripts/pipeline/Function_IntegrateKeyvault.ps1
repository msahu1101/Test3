param (
   $AppResourceGroupName,
   $AppName,
   $Envionment,
   $NetworkEnvionment,
   $KVResourceGroupPrefix = "customerKeyVault",
   $KVNamePrefix = "customer",
   $SlotName = "production",
   $ConfigKey= "keyVault.client.key"
)

$secretName = $Envionment+"-keyvault-appregistration-key"

$keyvaultname = $KVNamePrefix+"pub-uw-kv-"+$NetworkEnvionment
$keyvaultRg = $KVResourceGroupPrefix+'-uw-rg-'+$NetworkEnvionment

$secretId = az keyvault secret show -n $secretName --vault-name $keyvaultname --query "id" -o tsv
$principalId = az functionapp identity show -n $AppName -g $AppResourceGroupName --query principalId -o tsv

az keyvault set-policy -n $keyvaultname -g $keyvaultRg --object-id $principalId --secret-permissions get
az functionapp config appsettings set -n $AppName -g $AppResourceGroupName --settings "keyVault.client.key=@Microsoft.KeyVault(SecretUri=$secretId^^)"
if($SlotName -ne "production")
{
	$slotPrincipalId = az functionapp identity show -n $AppName -s $SlotName -g $AppResourceGroupName --query principalId -o tsv
	Write-Host "##vso[task.setvariable variable=TARGET_SLOT_PRINCIPAL_ID]$slotPrincipalId"
	az keyvault set-policy -n $keyvaultname -g $keyvaultRg --object-id $slotPrincipalId --secret-permissions get
	az functionapp config appsettings set -n $AppName -s $SlotName -g $AppResourceGroupName --settings "$ConfigKey=@Microsoft.KeyVault(SecretUri=$secretId^^)"	
}