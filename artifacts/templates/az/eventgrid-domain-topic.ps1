param (
    $TopicName,
    $Name,
    $ResourceGroup
)

az eventgrid domain topic create --domain-name $Name --name $TopicName --resource-group $ResourceGroup                            

                           