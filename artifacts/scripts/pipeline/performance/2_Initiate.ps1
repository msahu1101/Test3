param (
    $Duration = 1,
    $TestModule = 'Profile/Loyalty Performance Module',
    $Environment = 'na',
    $TestParamFile,
    $ProjectId,
    $BlazeMeterUser,
    $BlazeMeterPassword
    
    <#
    $ProjectId ='496593',
    $BlazeMeterUser = '71cab2f3ed6dfc5e15af437a',
    $BlazeMeterPassword  = '8f9dd3d3137f959fcfba27f45363687b651412f6e85287f672d5b02ddeb80fa4e1ff2a2f'
    #>    
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ModifiedTestFile = 'performance-suite-updated.jmx'

$BlazeMeterServer = 'https://a.blazemeter.com/api/v4'
$TestProjectName = $TestModule+' - ' + $Environment

# Authentication
$WebClient = New-Object System.Net.WebClient
$Credentials = new-object System.Net.CredentialCache
$Credential = new-object System.Net.NetworkCredential($BlazeMeterUser, $BlazeMeterPassword)
$WebClient.Credentials = $Credentials
$AuthHeader = @{
    "Authorization" = "Basic " + [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($BlazeMeterUser + ":" + $BlazeMeterPassword))
}

# Create Test
$TestPayload = '{"projectId":"' + $ProjectId + '","name": "' + $TestProjectName + '", "configuration": {"type": "taurus", "filename": "' + $ModifiedTestFile + '","scriptType":"jmeter","duration":"' + $Duration + '"}}'
$TestResponse = Invoke-RestMethod -Method POST -Header $AuthHeader -ContentType "application/json" -uri ($BlazeMeterServer + '/tests/') -Body $TestPayload
$TestId = $TestResponse.result.id
Write-Host "BlazeMeter test created. ID: $TestId"

# Upload

$UploadUrl = $BlazeMeterServer + '/tests/' + $TestId + '/files'
$Credentials.Add($UploadUrl, "Basic", $Credential)
$WebClient.UploadFile($UploadUrl, "POST", (Get-ChildItem $ModifiedTestFile))
Write-Host "JMeter file uploaded to Blazemeter" Write-Host "JMeter file uploaded to server"



#Upload config file if applicable

 if ($TestParamFile) {

 $RelativePath = '../../../performance/'

 $CurrentDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path

 $PerformanceDataDirectory = Join-Path -Path $CurrentDirectory -ChildPath $RelativePath

 Write-Host "Checking if file exists $PerformanceDataDirectory"

 $TestParamPath = $PerformanceDataDirectory + $TestParamFile

 $WebClient.UploadFile($UploadUrl, "POST", (Get-ChildItem $TestParamPath))

 Write-Host "CSV file file uploaded to Blazemeter"

 }
# Run
$RunUrl = $BlazeMeterServer + '/tests/' + $TestId + '/start'
$RunResponse = Invoke-RestMethod -Method POST -Header $AuthHeader -uri $RunUrl -ContentType "application/json"
$TestRunId = $RunResponse.result.id 
Write-Host "BlazeMeter test triggered. Run ID: $TestRunId"

Write-Host "##vso[task.setvariable variable=PERFORMANCE_TEST_RUNID]$TestRunId"