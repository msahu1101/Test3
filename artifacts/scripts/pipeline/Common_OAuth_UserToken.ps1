param (
    $OAuthDmpServer,
    $Path,
    $Username,
    $Password,
    $Scopes
)

<#
$OAuthServer = 'https://mgmdmp.oktapreview.com'
$Path = 'oauth2/ausph7ezp3Gkkk8WN0h7/v1/token'
$Username = 'sankar@mgmqa.com'
$Password = 'userpassword'
#>    

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Endpoint = $OAuthDmpServer + "/" + $Path

$Splat = @{
    Method          = 'POST'
    Uri             = '{0}' -f $Endpoint
    ContentType     = 'application/x-www-form-urlencoded'
    Body            = 'grant_type=password&username={0}&password={1}&scope={2}' -f $Username, $Password, $Scopes
    Headers         = @{Authorization = ('Basic MG9hZzVxeHl0cU00SHZKSHUwaDc6eWhuLUlqMF82QWpidk9lN3JZN2lwamdtQUJwaGkydnN1b2xCSUJUMQ') }
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
$OAuthUserAccessToken=$Token.access_token
Write-Host "##vso[task.setvariable variable=APIM_OAUTH_USER_TOKEN]$OAuthUserAccessToken"