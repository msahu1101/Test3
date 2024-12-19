# Parameter for Subscription, Cosmos Resource group, Cosmos Account, VNet Resource Group, VNet Name and Subnet Name

param (
	$subscription,
    $cosmosResourceGroup,
	$cosmosAccountName,
	$vnetResourceGroup,
    $vnetName,
	$subnetName
)

# Log in using service principal identity
az login --identity
az account set --subscription "$subscription"

# Look up the id for the existing subnet
$subnetId = $(az network vnet subnet show -g $vnetResourceGroup -n $subnetName --vnet-name $vnetName --query 'id' -o tsv)

# Add the virtual network rule but ignore the missing service endpoint on the subnet
az cosmosdb network-rule add --subscription $subscription -g $cosmosResourceGroup -n $cosmosAccountName --vnet-name $vnetName --subnet $subnetId --ignore-missing-vnet-service-endpoint true