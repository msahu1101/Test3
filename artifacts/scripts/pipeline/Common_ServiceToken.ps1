param (
    $Endpoint,
    $Path,
    $ClientId,
    $ClientSecret,
    $Scopes,
    $VariableName
)

<#
$Endpoint = 'https://azdeapi-dev.mgmresorts.com'
$Path = 'secure/oauth2/token'
$ClientId = '8abd41d6-e618-4e72-8e92-c55f7774501c'
$ClientSecret = 'LblfxoIzVVrs-Mce2U::ZxcAhQbF:622'
$Scopes = 'trip:elevated'
$VariableName = 'test'
#>    

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$fullEndpoint = $Endpoint + "/" + $Path

$body = @{
    "client_id" = "$ClientId"
    "client_secret" = "$ClientSecret"
    "scope" = "$Scopes"
    "grant_type" = "client_credentials"
}
$header = @{
    "ContentType" = "application/x-www-form-urlencoded"
}
$request = @{
    Method          = "POST"
    Uri             = "$fullEndpoint"
    ContentType     = "application/x-www-form-urlencoded"
    Body            = $body
    Headers         = $header
    UseBasicParsing = $true
}

$Token = "na"
$result = Invoke-WebRequest @request
if ($result.StatusCode -eq 200) {
    $Token = $result.Content | ConvertFrom-Json
}else{
    Write-Error -Message ("Unable to get access token. error Code: "+ $result.StatusCode)  -ErrorAction Stop
}
$ServiceToken=$Token.access_token
Write-Host "##vso[task.setvariable variable=$VariableName]$ServiceToken"