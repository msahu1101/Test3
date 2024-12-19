
param (
    $Endpoint,
    $ClientId,
    $ClientSecret,
    $Scopes
)

<#
$Endpoint = 'https://azdeapi-dev.mgmresorts.com/int/identity/authorization/v1/mgmsvc/token'
$ClientId = '0oaqtasrbcskQsrmh0h7'
$ClientSecret = 'BuIODy5DdmjQIU3dVhLDLScse9Z_gMYOG6xEj5W7'
$Scopes = 'loyalty:balances:read'
#>    

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Splat = @{
    Method          = 'POST'
    Uri             = '{0}' -f $Endpoint
    ContentType     = 'application/x-www-form-urlencoded'
    Body            = 'grant_type=client_credentials&client_id={0}&client_secret={1}&scope={2}' -f $ClientId, $ClientSecret, $Scopes 
    UseBasicParsing = $true
}

$Token = "na"
$result = Invoke-WebRequest @Splat
if ($result.StatusCode -eq 200) {
    $Token = $result.Content | ConvertFrom-Json
}else{
    Write-Error -Message ("Unable to get access token. error Code:  $result")  -ErrorAction Stop
}
$OAuthAccessToken=$Token.access_token
Write-Host "##vso[task.setvariable variable=APIM_OAUTH_TOKEN]$OAuthAccessToken"