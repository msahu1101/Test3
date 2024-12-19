# ******************************* environment specific variable arguments ***********************************
param (
    $functionsAppEnvironment,
    $networkEnvironment,
	$cloudLocation,
	$functionsNamePrefix,
	$baseFolder,
	$releaseNumber,
	$resourceType = 'fa',
	$resourceGroupPrefix = 'customer',
	$functionApigeeBasepath,
	$functionApigeeProxyName,
	$cloudLocation1 = 'Not-Provided',
	$cloudLocation2 = 'Not-Provided'
)


#------------------------------------------------------------------------------------------------------------

# ******************************* prepare pipeline variables ************************************************
$filePath = $baseFolder+"/pom.xml"
Write-Host $filePath
[xml]$pomXml = Get-Content $filePath
$functionsAppVersionActual=""+$pomXml.project.version
$functionsAppMajorVersion=$functionsAppVersionActual.Substring(0, $functionsAppVersionActual.IndexOf("."))
$functionsAppVersionSuffix=$(If ($functionsAppVersionActual.indexOf("-") -ne -1) {$functionsAppVersionActual.subString($functionsAppVersionActual.indexOf("-"))} Else {""})
$functionsAppVersionRaw=$functionsAppMajorVersion+$functionsAppVersionSuffix -replace '[^a-zA-Z\d\s:]'
$functionsAppVersion=$functionsAppVersionRaw.ToLower()
if ($functionsAppEnvironment -eq $null) {
    Write-Host "No app environment argument found. Reading from pom file........."
    $functionsAppEnvironment=$pomXml.project.properties.targetEnvironment
}
if ($networkEnvironment -eq $null) {
    Write-Host "No network environment argument found. Reading from pom file........."
    $networkEnvironment=$pomXml.project.properties.targetNetworkEnvironment
}
$functionsAppName=$functionsNamePrefix+$functionsAppVersion+"-"+$cloudLocation+"-$resourceType-"+$functionsAppEnvironment
$functionsAppName1=$functionsNamePrefix+$functionsAppVersion+"-"+$cloudLocation1+"-$resourceType-"+$functionsAppEnvironment
$functionsAppName2=$functionsNamePrefix+$functionsAppVersion+"-"+$cloudLocation2+"-$resourceType-"+$functionsAppEnvironment
$functionsResourceGroupName=$resourceGroupPrefix+"-"+$cloudLocation+"-rg-"+$functionsAppEnvironment

$ApiMajorVersion=$functionsAppMajorVersion

$ApiPath = $functionApigeeBasepath+"/v"+$ApiMajorVersion
$ApiProxyName = $functionApigeeProxyName+"-v"+$ApiMajorVersion

#------------------------------------------------------------------------------------------------------------

# ******************************* prepare arm teplate parameter files ***************************************
Function FilterFile($FilePath){
    $FilePathFiltered=$FilePath+'.tmp'
    (Get-Content $FilePath -Raw) `
    -replace '\$\(TARGET_APP_SERVICE_RESOURCE_GROUP\)', $functionsResourceGroupName `
    -replace '\$\(TARGET_APP_SERVICE_NAME\)', $functionsAppName  `
    -replace '\$\(TARGET_APP_SERVICE_NAME1\)', $functionsAppName1  `
    -replace '\$\(TARGET_APP_SERVICE_NAME2\)', $functionsAppName2  `
    -replace '\$\(TARGET_APP_SERVICE_ENVIRONMENT\)', $functionsAppEnvironment `
    -replace '\$\(TARGET_NETWORK_ENVIRONMENT\)', $networkEnvironment `
    -replace '\$\(TARGET_APP_SERVICE_VERSION\)', $functionsAppVersion `
    -replace '\$\(TARGET_APP_SERVICE_LOCATION\)', $cloudLocation  `
     -replace '\$\(FUNCTION_APIGEE_PREFIX\)', $ApiPath  `
     -replace '\$\(FUNCTION_APIGEE_PROXYNAME\)', $ApiProxyName  `
     -replace '\$\(MAJOR_VERSION\)', $ApiMajorVersion  `
    -replace '\$\(TARGET_CODE_VERSION\)', $functionsAppVersionActual `
     | Set-Content $FilePathFiltered
}

Get-ChildItem ($baseFolder+"/artifacts/templates/params") -rec -Filter *.json | 
Foreach-Object {
    Write-Host "$_"
    FilterFile $_.FullName
}
#------------------------------------------------------------------------------------------------------------

# ******************************* set parameters as environment variables ***********************************
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_RESOURCE_GROUP]$functionsResourceGroupName"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_NAME]$functionsAppName"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_NAME1]$functionsAppName1"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_NAME2]$functionsAppName2"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_ENVIRONMENT]$functionsAppEnvironment"
Write-Host "##vso[task.setvariable variable=TARGET_NETWORK_ENVIRONMENT]$networkEnvironment "
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_LOCATION]$cloudLocation"
Write-Host "##vso[task.setvariable variable=TARGET_APP_SERVICE_VERSION]$functionsAppVersion"
Write-Host "##vso[task.setvariable variable=TARGET_CODE_VERSION]$functionsAppVersionActual"
Write-Host "##vso[task.setvariable variable=SOURCE_FOLDER_ARTIFACT]$baseFolder"
Write-Host "##vso[task.setvariable variable=MAJOR_VERSION]$ApiMajorVersion"
Write-Host "##vso[task.setvariable variable=FUNCTION_APIGEE_PREFIX]$ApiPath"
Write-Host "##vso[task.setvariable variable=FUNCTION_APIGEE_PROXYNAME]$ApiProxyName"

$cosmosInfra='False'
$aspInfra='False'

$filePath = $baseFolder+"/cosmos_infra_changed.txt"
if([System.IO.File]::Exists($filePath)){
    $cosmosInfra = 'True'
    Write-Host "##vso[task.setvariable variable=COSMOS_INFRA_CHANGED]True"
}

$filePath = $baseFolder+"/asp_infra_changed.txt"
if([System.IO.File]::Exists($filePath)){
    $aspInfra = 'True'
    Write-Host "##vso[task.setvariable variable=ASP_INFRA_CHANGED]True"
}

#------------------------------------------------------------------------------------------------------------

# ******************************* Prints variables **********************************************************
Write-Host "Target Resource Group       :" $functionsResourceGroupName
Write-Host "Target App Service Name     :" $functionsAppName
Write-Host "Target App Service Name-1  :" $functionsAppName1
Write-Host "Target App Service Name-2  :" $functionsAppName2
Write-Host "Target App Environment      :" $functionsAppEnvironment
Write-Host "Target Network Environment  :" $networkEnvironment
Write-Host "target Location             :" $cloudLocation
Write-Host "Application Version         :" $functionsAppVersion
Write-Host "Application Version Actuals :" $functionsAppVersionActual
Write-Host "Cosmos Infra Flag           :" $cosmosInfra
Write-Host "ASP Infra Flag          	:" $aspInfra
Write-Host "Major Version               :" $ApiMajorVersion
Write-Host "Apigee Basepath             :" $ApiPath
Write-Host "Apigee ProxyName            :" $ApiProxyName
#------------------------------------------------------------------------------------------------------------
# Dump variables
$releasefileName = 'release-params'+$releaseNumber+'.txt'
Write-Host "Variable file :" $releasefileName
$content = "TARGET_APP_SERVICE_RESOURCE_GROUP="+$functionsResourceGroupName+"`n"+
           "TARGET_APP_SERVICE_NAME="+$functionsAppName+"`n"+
           "TARGET_APP_SERVICE_ENVIRONMENT="+$functionsAppEnvironment+"`n"+
           "TARGET_NETWORK_ENVIRONMENT="+$networkEnvironment+"`n"+
           "TARGET_APP_SERVICE_LOCATION="+$cloudLocation+"`n"+
           "TARGET_APP_SERVICE_VERSION="+$functionsAppVersion+"`n"+
           "TARGET_CODE_VERSION="+$functionsAppVersionActual | Set-Content $releasefileName