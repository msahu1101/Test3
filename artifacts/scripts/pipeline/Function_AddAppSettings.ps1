param (
   [Parameter(Mandatory=$true)]
   $FunctionResourceGroup,
   [Parameter(Mandatory=$true)]
   $FunctionName,
   [Parameter(Mandatory=$true)]
   $CommaSeparatedKeyValues,
   [Parameter(Mandatory=$true)]
   $SlotName = "production"
)

$KeyValues = $CommaSeparatedKeyValues.Split(",")
foreach ($KeyValue in $KeyValues) {
   az functionapp config appsettings set --name $FunctionName --resource-group $FunctionResourceGroup --settings "$KeyValue"
   if($SlotName -ne "production")
	{
		az functionapp config appsettings set --name $FunctionName --slot $SlotName --resource-group $FunctionResourceGroup --settings "$KeyValue"
	}
}
