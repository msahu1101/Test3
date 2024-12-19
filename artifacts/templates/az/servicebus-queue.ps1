param (
    $ResourceGroup,
    $Namespace,
    $QueueName,
    $PolicyName,
    $EnableSession="false",
    $MaxDeliveryCount="2",
    $MaxSize="1024"
)

az servicebus queue create --resource-group $ResourceGroup --namespace-name $Namespace --name $QueueName --enable-dead-lettering-on-message-expiration true --enable-session $EnableSession --max-delivery-count $MaxDeliveryCount --max-size $MaxSize
az servicebus queue authorization-rule create --resource-group $ResourceGroup --namespace-name $Namespace --queue-name $QueueName --name $PolicyName --rights Listen Send