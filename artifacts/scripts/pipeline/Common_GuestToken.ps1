param (
    $Endpoint,
    $Path,
    $Username,
    $Password,
    $Scopes,
    $VariableName
)

<#
$Endpoint = 'https://azdeapi-dev.mgmresorts.com'
$Path = 'secure/oauth2/token'
$Username = 'test@mgmresorts.com'
$Password = 'Testpassword'
$Scopes = 'trip:elevated'
$VariableName = 'test'
#>    

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$fullEndpoint = $Endpoint + "/" + $Path

$body = @{
    "username" = "$Username"
    "password" = "$Password"
    "scope" = "$Scopes"
    "grant_type" = "password"
}
$header = @{
    "ContentType" = "application/x-www-form-urlencoded"
    "Authorization" = "Basic MG9hZzVxeHl0cU00SHZKSHUwaDc6eWhuLUlqMF82QWpidk9lN3JZN2lwamdtQUJwaGkydnN1b2xCSUJUMQ=="
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