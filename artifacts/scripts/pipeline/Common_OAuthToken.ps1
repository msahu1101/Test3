param (
    $OAuthServer,
    $Path,
    $ClientId,
    $ClientSecret
)

<#
$OAuthServer = 'https://azdeapi-dev.mgmresorts.com'
$Path = 'secure/oauth2/token'
$ClientId = '8abd41d6-e618-4e72-8e92-c55f7774501c'
$ClientSecret = 'LblfxoIzVVrs-Mce2U::ZxcAhQbF:622'
#>    

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Endpoint = $OAuthServer + "/" + $Path
$CredPlain = '{0}:{1}' -f $ClientId, $ClientSecret
$UTF8Encoding = [System.Text.UTF8Encoding]::new()
$CredBytes = $UTF8Encoding.GetBytes($CredPlain)
$Base64auth = [Convert]::ToBase64String($CredBytes)

$Splat = @{
    Method          = 'POST'
    Uri             = '{0}' -f $Endpoint
    ContentType     = 'application/x-www-form-urlencoded'
    Body            = 'grant_type=client_credentials'
    Headers         = @{Authorization = ('Basic {0}' -f $Base64auth) }
    UseBasicParsing = $true
}
Write-Host "$Endpoint"

$Token = "na"
$result = Invoke-WebRequest @Splat
if ($result.StatusCode -eq 200) {
    $Token = $result.Content | ConvertFrom-Json
}else{
    Write-Error -Message ("Unable to get access token. error Code: "+ $result.StatusCode)  -ErrorAction Stop
}
$OAuthAccessToken=$Token.access_token
Write-Host "##vso[task.setvariable variable=APIM_OAUTH_TOKEN]$OAuthAccessToken"