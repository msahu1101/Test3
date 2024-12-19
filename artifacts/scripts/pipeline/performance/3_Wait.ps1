param (
    $TestRunId,
    $BlazeMeterUser,
    $BlazeMeterPassword
    <#
    $BlazeMeterUser = '71cab2f3ed6dfc5e15af437a',
    $BlazeMeterPassword  = '8f9dd3d3137f959fcfba27f45363687b651412f6e85287f672d5b02ddeb80fa4e1ff2a2f'
    #>    
 )
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$BlazeMeterServer = 'https://a.blazemeter.com/api/v4'
$AuthHeader = @{
    "Authorization" = "Basic " + [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($BlazeMeterUser + ":" + $BlazeMeterPassword))
}

$RunUrl = $BlazeMeterServer + '/masters/' + $TestRunId + '/status'
Write-Host "Trying to fetch $RunUrl"
$TestStatus = 'INITIATED' 
$StatusChecker = 0
do {
    Write-Host 'Checking status #'$StatusChecker ' | ' $TestStatus 
    $StatusChecker = $StatusChecker + 1
    $StatusResponse = Invoke-RestMethod -Method GET -Header $AuthHeader -uri $RunUrl -ContentType "application/json" 
    $TestStatus = $StatusResponse.result.status     
    Start-Sleep -Seconds 60
}
until(($TestStatus -eq 'ENDED') -OR ($StatusChecker -gt 100))

if ($TestStatus -ne 'ENDED') {
    Write-Error -Message "Timed out. Test is still running..."  -ErrorAction Stop
}

# Get Statistics
$RunUrl = $BlazeMeterServer + '/masters/' + $TestRunId + '/reports/aggregatereport/data'
$StatsResponse = Invoke-RestMethod -Method GET -Header $AuthHeader -uri $RunUrl -ContentType "application/json"
$StatsResponse.result | ConvertTo-Json | Out-File output.json
Write-Host "Response Received"

