
param (
	$ApimRG,
    $ApimName,
    $DefaultFunctionAccessKey,
    $MasterFunctionAccessKey,
    $BaseFunctionEndpoint,
    $FunctionsVersion,
    $OAuthTenantId,
    $Environment,
    $PolicyFileName ="apim-policy-template-webhook-aad.xml",
    $ApiPrefix,
    $ApiSuffix = '',
    $OauthServer
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Set-AzContext -Subscription "digital engineering shared services"
$RelativePath = '../../templates/policy/'
$CurrentDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$PolicyDirectory  = Join-Path -Path $CurrentDirectory -ChildPath $RelativePath
$PolicyFilePath = $PolicyDirectory + '/'+$PolicyFileName
Write-Host "PolicyFilePath : $PolicyFilePath"

Set-AzContext -Subscription "digital engineering shared services"
$OpenApiUrl = $BaseFunctionEndpoint+"/api/control/webhook-openapi?code="+$MasterFunctionAccessKey
$SpecificationFile = New-TemporaryFile

Invoke-RestMethod -Method GET -uri $OpenApiUrl -OutFile $SpecificationFile
Write-Host "[1/5] Application specification retrieved"

$EffectiveSuffix = ""
$EffectiveId = ""
if ($ApiSuffix -ne '') {
    $EffectiveSuffix = "/"+$ApiSuffix 
    $EffectiveId = "-"+$ApiSuffix 
}
$ApiPathSuffix = $FunctionsVersion.Substring(0, $FunctionsVersion.IndexOf("."))
$ApiPath = $ApiPrefix+"/"+$Environment+"/v"+$ApiPathSuffix+$EffectiveSuffix+"/webhook"
if ($Environment -eq 'p') {
    $ApiPath = $ApiPrefix+"/v"+$ApiPathSuffix+$EffectiveSuffix+"/webhook"
}
Write-Host "[2/5] API to be prefixed with $ApiPath"

$ApiManagementContext = New-AzApiManagementContext -ResourceGroupName $ApimRG -ServiceName $ApimName
$ApiSetId=$ApiPrefix+"-"+$Environment+"-"+$ApiPathSuffix+$EffectiveId+"-webhook"
Import-AzApiManagementApi -Context $ApiManagementContext -SpecificationFormat "OpenApi" -SpecificationPath $SpecificationFile -Path $ApiPath -ApiId $ApiSetId 
Write-Host "[3/5] API imported to APIM"


$ApiManagementApi = Get-AzApiManagementApi -Context $ApiManagementContext -ApiId $ApiSetId
$ApiManagementApi.SubscriptionRequired=$false
$ApiManagementApi.AuthorizationServerId= $OauthServer
Set-AzApiManagementApi -InputObject $ApiManagementApi -Name $ApiManagementApi.Name -ServiceUrl $ApiManagementApi.ServiceUrl -Protocols $ApiManagementApi.Protocols
Write-Host "[4/5] OAuth server configured"

$RawPolicy = (Get-Content -Path $PolicyFilePath -Raw)
$PolicyFile = $RawPolicy -replace '\{\{function_access_token\}\}', $DefaultFunctionAccessKey -replace '\{\{oauth_server_aad_tenant_id\}\}', $OAuthTenantId
Set-AzApiManagementPolicy -Context $ApiManagementContext -ApiId $ApiSetId -Policy $PolicyFile

Write-Host "[5/5] Oauth Registration Successful"