
param (
   [Parameter(Mandatory=$true)]
   $ResourceGroup
  )

#Remove Metric Alert
$data = (Get-AzMetricAlertRuleV2 -ResourceGroupName  $ResourceGroup)
foreach ( $i in $data)
{
	Write-Host "Metric Alert ResourceId: $($i.Id)"
	Remove-AzMetricAlertRuleV2 -ResourceId $i.Id 
}

#Remove Activity Alert
$data = (Get-AzActivityLogAlert -ResourceGroupName  $ResourceGroup)
foreach ( $i in $data)
{
	Write-Host "Activity Alert ResourceId: $($i.Id)"
	Remove-AzActivityLogAlert -ResourceId $i.Id 
}

#Remove LogSearch Alert
$data = (Get-AzScheduledQueryRule -ResourceGroupName  $ResourceGroup)
foreach ( $i in $data)
{
	Write-Host "LogSearch Alert ResourceId: $($i.Id)"
	Remove-AzScheduledQueryRule -ResourceId $i.Id
}


