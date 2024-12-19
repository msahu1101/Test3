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
   $TenantId
)

$subscriptionId = az account show --query id -o tsv
$r = "/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.EventGrid/domains/$EventGridName/topics/$TopicName"

az eventgrid event-subscription create --name $SubscriberName --source-resource-id $r --endpoint-type webhook --endpoint $EndPoint --azure-active-directory-application-id-or-uri $AppId --azure-active-directory-tenant-id $TenantId