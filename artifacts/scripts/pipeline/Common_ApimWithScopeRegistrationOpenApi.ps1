 
param (
    $ApimRG,
    $ApimName,
    $DefaultFunctionAccessKey,
    $MasterFunctionAccessKey,
    $BaseFunctionEndpoint,
    $FunctionsVersion,
    $OAuthWellKnownEndpoint,
    $Environment,
    $PolicyFileName ="apim-policy-template.xml",
    $ApiPrefix,
    $ApiSuffix = ''
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Find the policy template
Set-AzContext -Subscription "digital engineering shared services"
$RelativePath = '../../templates/policy/'
$CurrentDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$PolicyDirectory  = Join-Path -Path $CurrentDirectory -ChildPath $RelativePath
$PolicyFilePath = $PolicyDirectory + '/'+$PolicyFileName
Write-Host "[DEBUG] Policy template file to be used : $PolicyFilePath"

# Create policy from template
$OpenApiUrl = $BaseFunctionEndpoint+"/api/control/scope?code="+$MasterFunctionAccessKey
$RolesData = New-TemporaryFile
$cHeaders = @{
    'accept' = 'application/json;charset=utf-8,*/*'
}
Invoke-RestMethod -Method GET -uri $OpenApiUrl -OutFile $RolesData -Headers $cHeaders 
Write-Host "[DEBUG] Roles data retrieved from application..."

$pathConditions = ""
$allScopes = ""

$Roles = Get-content $RolesData | ConvertFrom-Json 

Write-Host "[DEBUG] Roles Response...$Roles"
##
$Roles | ForEach-Object {
    $Mapping = $_.PSObject.Properties
    $Mapping | ForEach-Object {
        if($_.Name -eq "mappings"){ 
            $key1 = $_.Name
            $value1 = $_.Value
            
            $value1 | Get-Member -MemberType Properties | ForEach-Object {
                
                $keyObject = $_.Name
                $value = $value1 | Select-Object -ExpandProperty $keyObject
                $roles = '"'+($value.roles -join '","') + '"'
                $methods = '"'+($value.methods -join '","') + '"'
                $route = $value.staticRoute
                
                $pathConditions = $pathConditions+ @"
        
                    <when condition="@(new [] {$methods}.Contains(context.Request.Method) && context.Request.OriginalUrl.Path.Contains("$route/"))">
                        <choose>
                            <when condition="@(!((Jwt)context.Variables["jwt"]).Claims["scp"].Intersect(new [] {$roles}).Any())">
                                <return-response>
                                    <set-status code="401" reason="Unauthorized. Client is not authorized to access requested resource. Invalid Scopes" />
                                </return-response>
                            </when>
                        </choose>
                    </when>
                
"@ 
            }
        }
        
        if($_.Name -eq "roles"){ 
            $roles = $_.Value
            $roles | ForEach-Object {
                $allScopes = -join($allScopes, " `n                                        ", "<value>$_</value>") 
            }
        }
   }
}
##
$RawRolesData = (Get-Content -Path $PolicyFilePath -Raw)
$PolicyFile = $RawRolesData -replace '\$\{\{function_access_default_key\}\}', $DefaultFunctionAccessKey 
$PolicyFile = $PolicyFile -replace '\$\{\{oauth_wellknown_endpoint\}\}', $OAuthWellKnownEndpoint
$PolicyFile = $PolicyFile -replace '\$\{\{path_conditions\}\}', $pathConditions
$PolicyFile = $PolicyFile -replace '\$\{\{all_possible_scopes\}\}', $allScopes

Write-Host "[DEBUG] Policy file generated from template with role/scope..."
$PolicyFilePath = New-TemporaryFile
Set-Content -Path $PolicyFilePath -Value $PolicyFile

Write-Host "----------------------"
Write-Host "$PolicyFile"
Write-Host "----------------------"


$OpenApiUrl = $BaseFunctionEndpoint+"/api/control/openapi?code="+$MasterFunctionAccessKey
$SpecificationFile = New-TemporaryFile
Invoke-RestMethod -Method GET -uri $OpenApiUrl  -Headers $cHeaders -OutFile $SpecificationFile
Write-Host "[DEBUG] Service specification retirieved from application..."


$EffectiveSuffix = ""
$EffectiveId = ""
if ($ApiSuffix -ne '') {
    $EffectiveSuffix = "/"+$ApiSuffix 
    $EffectiveId = "-"+$ApiSuffix 
}
$ApiMajorVersion = $FunctionsVersion.Substring(0, $FunctionsVersion.IndexOf("."))
$ApiPath = $ApiPrefix+"/"+$Environment+"/v"+$ApiMajorVersion+$EffectiveSuffix
if ($Environment -eq 'p') {
    $ApiPath = $ApiPrefix+"/v"+$ApiMajorVersion+$EffectiveSuffix
}
Write-Host "[DEBUG] Apim will be prefixed with '$ApiPath'"

$ApiManagementContext = New-AzApiManagementContext -ResourceGroupName $ApimRG -ServiceName $ApimName
$ApiSetId=$ApiPrefix+"-"+$Environment+"-"+$ApiMajorVersion+$EffectiveId
Import-AzApiManagementApi -Context $ApiManagementContext -SpecificationFormat "OpenApi" -SpecificationPath $SpecificationFile -Path $ApiPath -ApiId $ApiSetId 
Write-Host "[DEBUG] Api set imported in APIM"

$ApiManagementApi = Get-AzApiManagementApi -Context $ApiManagementContext -ApiId $ApiSetId
$ApiManagementApi.SubscriptionRequired=$false
Set-AzApiManagementApi -InputObject $ApiManagementApi -Name $ApiManagementApi.Name -ServiceUrl $ApiManagementApi.ServiceUrl -Protocols $ApiManagementApi.Protocols
Write-Host "[DEBUG] Subscription key disabled"

Set-AzApiManagementPolicy -Context $ApiManagementContext -ApiId $ApiSetId -PolicyFilePath $PolicyFilePath  -Format application/vnd.ms-azure-apim.policy.raw+xml
Write-Host "[DEBUG] API policy updated"

Write-Host "##vso[task.setvariable variable=FUNCTION_APIM_PREFIX]$ApiPath"
Write-Host "[DEBUG] APIM registration complete"
