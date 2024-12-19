param (
    $Environment = 'na',
    $Endpoint = 'na://na',
    $AccessToken = 'na',
    $ClientId = 'na',
    $ClientSecret = 'na',
    $ApimPrefix = 'na',
    $JmxFilePathWOExtn = 'performance-suite'
)
$RelativePath = '../../../performance/'
$CurrentDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$PerformanceDataDirectory  = Join-Path -Path $CurrentDirectory -ChildPath $RelativePath
Write-Host "Checking if file exists $PerformanceDataDirectory"


$TestFilePath = $PerformanceDataDirectory + $JmxFilePathWOExtn +".jmx"
$ProperfiesFilePath = $PerformanceDataDirectory + $JmxFilePathWOExtn+'-parameters-' + $Environment + '.properties'
$InstrumentationNeeded = 'true'

Write-Host "Checking if file exists $ProperfiesFilePath"
if([System.IO.File]::Exists($ProperfiesFilePath)){
    Write-Host "Using environment specific properties file $ProperfiesFilePath"
}
else{
    $ProperfiesFilePath = $PerformanceDataDirectory + $JmxFilePathWOExtn+'-parameters.properties'
    Write-Host "Checking if file exists $ProperfiesFilePath"
    if([System.IO.File]::Exists($ProperfiesFilePath)){
        Write-Host "Using default properties file $ProperfiesFilePath"
    }else{
        $InstrumentationNeeded = 'false'
        Write-Host "No properties file found. Skipping file instrumentation"
    }
}
$ModifiedTestFile = 'performance-suite-updated.jmx'
if($InstrumentationNeeded -eq 'true'){
    [xml]$JmxContents = Get-Content $TestFilePath	
    $JmxProperties = $JmxContents.jmeterTestPlan.hashTree.hashTree.Arguments.collectionProp.elementProp
    Get-Content -Path $ProperfiesFilePath |
         Where-Object {!$_.StartsWith("#") } | 
         ForEach-Object {
            $Key = $_ -replace '=.*'
            $Value = $_ -replace '.*='
            Write-Host "Replacing property value. Key: $Key | Value: $Value"
            
            forEach ($JmxProperty in $JmxProperties) {   
                if ($JmxProperty.name -eq "application_endpoint") {
                    $JmxProperty.stringProp[1]."#text" = $Endpoint
                }
                if ($JmxProperty.name -eq "application_token") {
                    $JmxProperty.stringProp[1]."#text" = $AccessToken
                }
                if ($JmxProperty.name -eq "apim_prefix") {
                    $JmxProperty.stringProp[1]."#text" = $ApimPrefix
                }
                if ($JmxProperty.name -eq "application_oauth_client_id") {
                    $JmxProperty.stringProp[1]."#text" = $ClientId
                }
                if ($JmxProperty.name -eq "application_oauth_client_secret") {
                    $JmxProperty.stringProp[1]."#text" = $ClientSecret
                }
                if ($JmxProperty.name -eq $Key) {
                    $JmxProperty.stringProp[1]."#text" = $Value
                }
            }   
    }
    echo $JmxContents.OuterXml | set-content -encoding UTF8 $ModifiedTestFile
    Write-Host "File instrumentation complete"

}

Write-Host "##vso[task.setvariable variable=PERFORMANCE_TEST_FILE]$ModifiedTestFile"