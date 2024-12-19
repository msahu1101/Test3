param (
    $ResourceName,
    $ResourceType,
    $ApiVersion,
    $ResourceGroupForResource,
    $ResourceGroup,
    $AppName,
    $Subscription,
    $ConfigKey,
    $Variable,
    $Operation="listKeys"
)

connect-azaccount -identity
set-azcontext "$Subscription"
if ($ResourceGroupForResource -eq $null) {
    Write-Host "ResourceGroupForResource argument value not found. Adding ResourceGroup value within this........."
    $ResourceGroupForResource=$ResourceGroup
}
$keys = Invoke-AzResourceAction -Action $Operation -ResourceType "$ResourceType" -ApiVersion "$ApiVersion" -ResourceGroupName $ResourceGroupForResource -Name "$ResourceName" -Force
$requested = $keys.psobject.properties.Where({$_.name -eq $Variable}).value
$json = $keys | ConvertTo-Json
write-host "Requested: $json"
az functionapp config appsettings set -n $AppName -g $ResourceGroup --settings "$ConfigKey=$requested"
