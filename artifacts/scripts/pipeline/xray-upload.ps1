param ($xrayClientId,
$xrayClientSecret,
$projectKey,
$testPlanKey,
$testEnvironments,
$resultsFolder)

Write-Output "Uploading Functional Test Results to Xray";

$authorizationBody = @{
    client_id="$xrayClientId"
    client_secret="$xrayClientSecret"
} | ConvertTo-Json;

$xrayAuthUri = "https://xray.cloud.xpand-it.com/api/v1/authenticate";

$XRAY_AUTH_TOKEN = Invoke-RestMethod -Uri "$xrayAuthUri" -Method Post -Body $authorizationBody -ContentType "application/json";

$xrayImportUri = "https://xray.cloud.xpand-it.com/api/v1/import/execution/junit?projectKey=$projectKey&testPlanKey=$testPlanKey&testEnvironments=$testEnvironments";

Get-ChildItem -Path $resultsFolder |
Foreach-Object {
    Write-Output $_.FullName;
    $xrayResponse = Invoke-RestMethod -Uri "$xrayImportUri" -ContentType 'text/xml' -Method Post -Headers @{Authorization="Bearer $XRAY_AUTH_TOKEN"} -InFile $_.FullName;
    $testExecutionKey = $xrayResponse.key;
    Write-Output "Xray import succeeded. Please refer to the below link for test execution results.";
    Write-Output "https://mgmdigitalventures.atlassian.net/browse/$testExecutionKey";
};