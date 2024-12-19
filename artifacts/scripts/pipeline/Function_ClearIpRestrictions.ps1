param (
   $AppResourceGroupName,
   $AppName
)

$ApplicationId =$AppName+"/web"
$r = Get-AzResource -ResourceGroupName $AppResourceGroupName -ResourceType Microsoft.Web/sites/config -ResourceName $ApplicationId -ApiVersion 2016-08-01
$p = $r.Properties
$p.ipSecurityRestrictions = @()
Set-AzResource -ResourceGroupName $AppResourceGroupName -ResourceType Microsoft.Web/sites/config -ResourceName  $ApplicationId -ApiVersion 2016-08-01 -PropertyObject $p -Force