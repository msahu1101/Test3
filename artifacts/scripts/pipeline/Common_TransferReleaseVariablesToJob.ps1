# ******************************* environment specific variable arguments ***********************************
param (
    $releaseNumber
)

$releasefileName = 'release-params'+$releaseNumber+'.txt'
Write-Host "Variable file :" $releasefileName
$AppProps = convertfrom-stringdata (get-content $releasefileName -raw)
$T1=$AppProps.'TARGET_APP_SERVICE_RESOURCE_GROUP'
$T2=$AppProps.'TARGET_APP_SERVICE_NAME'
$T3=$AppProps.'TARGET_APP_SERVICE_ENVIRONMENT'
$T4=$AppProps.'TARGET_NETWORK_ENVIRONMENT'
$T5=$AppProps.'TARGET_APP_SERVICE_LOCATION'
$T6=$AppProps.'TARGET_APP_SERVICE_VERSION'
$T7=$AppProps.'TARGET_CODE_VERSION'

Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_RESOURCE_GROUP]$T1"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_NAME]$T2"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_ENVIRONMENT]$T3"
Write-Host "##vso[task.setvariable variable=TARGET_NETWORK_ENVIRONMENT]$T4"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_LOCATION]$T5"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_VERSION]$T6"
Write-Host "##vso[task.setvariable variable=TARGET_CODE_VERSION]$T7"