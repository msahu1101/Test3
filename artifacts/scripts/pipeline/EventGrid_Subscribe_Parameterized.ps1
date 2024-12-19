param (
    [Parameter(Mandatory=$true)]
    $SubscriberName,
    [Parameter(Mandatory=$true)]
    $ResourceGroup,
    [Parameter(Mandatory=$true)]
    $EventGridName,
    [Parameter(Mandatory=$true)]
    $TopicName,
    [Parameter(Mandatory=$true)]
    $EndPoint,
    [Parameter(Mandatory=$true)]
    $AppId,
    [Parameter(Mandatory=$true)]
    $TenantId,
    [string]
    $DeadLetterStorageAccount,
    [string]
    $DeadLetterContainer,
    [Int32]$EventTTL,
    [string[]]$IncludedEventTypes,
    [Int32]$MaxDeliveryAttempts,
    [string]$EndPointType = 'webhook'
)


if ($IncludedEventTypes -ne $null) {
    $includedEventTypesCmd = "--included-event-types $IncludedEventTypes"
}

if ($EventTTL -eq $null -or $EventTTL -eq '' -or $EventTTL -eq "0") {
    $EventTTL = 1440
}

if ($MaxDeliveryAttempts -eq $null -or $MaxDeliveryAttempts -eq '' -or $MaxDeliveryAttempts -eq "0") {
    $MaxDeliveryAttempts = 3
}

$subscriptionId = az account show --query id -o tsv
$r = "/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.EventGrid/domains/$EventGridName/topics/$TopicName"

if ($DeadLetterStorageAccount -ne $null -and $DeadLetterStorageAccount -ne '') {
    if($DeadLetterContainer -ne $null -and $DeadLetterContainer -ne '') {
        $d = "/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.Storage/storageAccounts/$DeadLetterStorageAccount/blobServices/default/containers/$DeadLetterContainer"
        $deadLetterEndpointCmd = "--deadletter-endpoint $d"
    }
} else {
    Write-Host -Message ("Dead letter storage not enabled as DeadLetterStorageAccount or DeadLetterContainer parameter values not provided");
}

if ($EndPointType -eq "webhook") {
	$command =  "az eventgrid event-subscription create --name $SubscriberName --source-resource-id $r --endpoint-type $EndPointType --endpoint $EndPoint --azure-active-directory-application-id-or-uri $AppId --azure-active-directory-tenant-id $TenantId $deadLetterEndpointCmd --event-ttl $EventTTL $includedEventTypesCmd --max-delivery-attempts $MaxDeliveryAttempts"
} else {
	$command =  "az eventgrid event-subscription create --name $SubscriberName --source-resource-id $r --endpoint-type $EndPointType --endpoint $EndPoint $deadLetterEndpointCmd --event-ttl $EventTTL $includedEventTypesCmd --max-delivery-attempts $MaxDeliveryAttempts"
}
Write-Host -Message ("AZ command created : " + $command);
Invoke-Expression $command

if ($lastExitCode -eq "0") {
    Write-Host -Message ("Event grid subscription succeeded ")
} else {
    Write-Host -Message ("Event grid subscription failed will retry once")
    Invoke-Expression $command
}

if ($lastExitCode -eq "0") {
    Write-Host -Message ("Exiting - Event grid subscription succeeded with lastExitCode : " + $lastExitCode)
} else {
    Write-Host -Message ("Exiting - Event grid subscription failed with lastExitCode : " + $lastExitCode)
}