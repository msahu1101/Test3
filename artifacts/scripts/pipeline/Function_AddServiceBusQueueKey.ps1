param (
    $ResourceGroup,
    $Namespace,
    $QueueName,    
    $AuthRule,    
    $ResourceGroupForResource,
    $ResourceName,
    $ConfigKey,
    $ExcludeEntityPath="false"
)

$keys = az servicebus queue authorization-rule keys list --resource-group $ResourceGroup --namespace-name $Namespace --queue-name $QueueName --name $AuthRule
$keysObj = $keys | ConvertFrom-Json
$key=$keysObj.primaryConnectionString

if ($ResourceGroupForResource -eq $null) {
    Write-Host "ResourceGroupForResource argument value not found. Adding ResourceGroup value within this........."
    $ResourceGroupForResource=$ResourceGroup
}

if($ExcludeEntityPath -eq "true"){
    $index = $key.LastIndexOf(';')
    $key = $key.substring(0, $index)
}
write-host "Requested: $key"
az functionapp config appsettings set -n $ResourceName -g $ResourceGroupForResource --settings "$ConfigKey=$key"
