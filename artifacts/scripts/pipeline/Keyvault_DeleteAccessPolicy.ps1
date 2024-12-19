param (
   $NetworkEnvionment,
   $KVResourceGroupPrefix = "customerKeyVault",
   $KVNamePrefix = "customer",
   $ResourcePrincipalId
)

$keyvaultname = $KVNamePrefix+"pub-uw-kv-"+$NetworkEnvionment
$keyvaultRg = $KVResourceGroupPrefix+'-uw-rg-'+$NetworkEnvionment
az keyvault delete-policy -n $keyvaultname -g $keyvaultRg --object-id $ResourcePrincipalId