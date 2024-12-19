param (
    $MasterFunctionAccessKey,
    $BaseFunctionEndpoint,
    $BaseFrontDoorHostName = ''
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$cHeaders = @{
    'accept' = 'application/yaml;charset=utf-8,*/*'
}

# Download the Open API Spec file

$OpenApiFilePath = New-TemporaryFile
Write-Host "The Open API Spec file is to be downloaded to : $OpenApiFilePath"
$OpenApiUrl = $BaseFunctionEndpoint+"/api/control/openapi?code="+$MasterFunctionAccessKey+"&specType=apigee&output=YAML"
if ($BaseFrontDoorHostName -ne '') {
    $OpenApiUrl = $OpenApiUrl + "&frontDoorHost="+$BaseFrontDoorHostName
}
Invoke-RestMethod -Uri $OpenApiUrl -Headers $cHeaders -Method GET -OutFile $OpenApiFilePath
Write-Host "##vso[task.setvariable variable=OPENAPISPEC_APIGEE_FILEPATH]$OpenApiFilePath"
Write-Host "The Open API Spec file has been successfully downloaded"