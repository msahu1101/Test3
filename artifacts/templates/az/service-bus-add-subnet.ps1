param (
    $ResourceGroup,
    $Namespace,
    $SubnetId
)

az servicebus namespace network-rule add --resource-group $ResourceGroup --namespace-name $Namespace --subnet $SubnetId --ignore-missing-endpoint True