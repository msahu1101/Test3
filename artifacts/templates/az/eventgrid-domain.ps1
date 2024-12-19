param (
    $Location = 'westus',
    $Name = 'profiletest-uw-egd-sb',
    $ResourceGroup = 'customer-uw-rg-sb',
    $Identity = 'systemassigned',
    $Schema = 'cloudeventschemav1_0',
    $Sku = 'premium'
)

az eventgrid domain create --location $Location --name $Name --resource-group $ResourceGroup 

                           