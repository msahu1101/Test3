$resourceToken=$(az account get-access-token --resource=https://management.azure.com)
$resourceTokenJson = $resourceToken | ConvertFrom-Json 
$oauthToken = $resourceTokenJson.accessToken

Write-Host "##vso[task.setvariable variable=RESOURCE_OAUTH_TOKEN]$oauthToken"