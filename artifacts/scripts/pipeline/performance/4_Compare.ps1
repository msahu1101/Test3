param (
    $Environment = 'na',
    $JmxFilePathWOExtn = 'performance-suite'
)

$RelativePath = '../../../performance/'
$CurrentDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$PerformanceDataDirectory  = Join-Path -Path $CurrentDirectory -ChildPath $RelativePath

$BenchmarkingNeeded = 'true'
$BenchmarkFile = $PerformanceDataDirectory+$JmxFilePathWOExtn+'-benchmark-' + $Environment + '.json'

if([System.IO.File]::Exists($BenchmarkFile)){
    Write-Host "Using environment specific properties file $BenchmarkFile"
}
else{
    $BenchmarkFile = $PerformanceDataDirectory+$JmxFilePathWOExtn+'-benchmark.json'
    if([System.IO.File]::Exists($BenchmarkFile)){
        Write-Host "Using default benchmarking file $BenchmarkFile"
    }else{
        $BenchmarkingNeeded = 'false'
        Write-Host "No benchmarking file found. Skipping file check"
    }
}
if($BenchmarkingNeeded -eq 'false'){
    Write-Host "Benchmarking check skipped as no file found"
}

$BeanchMarkRecords = Get-content $BenchmarkFile | ConvertFrom-Json 
$OutPutRecords = Get-Content 'output.json' | ConvertFrom-Json



$Msgs = @()
$Failure = $False
foreach ($OutPut in $OutPutRecords) {
    foreach ($BenchMark in ($BeanchMarkRecords | Get-Member -MemberType Properties)) {
    
        if ($BenchMark.Name -eq $OutPut.labelName ) {
            if ($OutPut.errorsCount -gt 0) {
                Write-Host "There are failure in performance tests"
            }        
            if ($OutPut.errorsCount -gt 5) {
                Write-Host "Failure: $OutPut"    
                Write-Error -Message ("Test failed reason errors in : " + $OutPut.labelName)  -ErrorAction Stop
            }
    
            $NFR = ($BeanchMarkRecords | Select-Object -ExpandProperty $BenchMark.Name);
            $avgResponseTime = $NFR.avgResponseTime
            if ($avgResponseTime -le $OutPut.avgResponseTime) {
                $Msg = @{ }
                $Msg.Add("AverageResponseTimeError", "Test failed reason : " + $BenchMark.Name + '.Average Response Time [ Expected ' + $avgResponseTime + ', Actual: ' + $OutPut.avgResponseTime)
                $Failure = $True
                $Msgs += $Msg
            }
            else {
                $bName = $BenchMark.Name
                $val = $NFR.avgResponseTime
                $outnfr = $OutPut.avgResponseTime
                $nText = "Average Response Time"
                Write-Host "Performance test result for : $bName.$nText [ Expected $val, Actual: $outnfr]"
            }
            $maxResponseTime = $NFR.maxResponseTime
            if ($maxResponseTime -le $OutPut.maxResponseTime) {
                $Msg = @{ }
                $Msg.Add("MaxResponseTimeError", "Test failed reason : " + $BenchMark.Name + '.Max Response Time [ Expected ' + $maxResponseTime + ', Actual: ' + $OutPut.maxResponseTime )
                $Failure = $True
                $Msgs += $Msg
            }
            else {
                $bName = $BenchMark.Name
                $val = $NFR.maxResponseTime
                $outnfr = $OutPut.maxResponseTime
                $nText = "Max Response Time"
                Write-Host "Performance test result for : $bName.$nText [ Expected $val, Actual: $outnfr]"
            }
            $line99 = $NFR."90line"
            if ($line99 -le $OutPut."90line") {
                $Msg = @{ }
                $Msg.Add("Percentile90thError", "Test failed reason : " + $BenchMark.Name + '.90th Percentile [ Expected ' + $line99 + ', Actual: ' + $OutPut."90line")
                $Failure = $True
                $Msgs += $Msg
            }
            else {
                $bName = $BenchMark.Name
                $val = $NFR."90line"
                $outnfr = $OutPut."90line"
                $nText = "90th Percentile"
                Write-Host "Performance test result for : $bName.$nText [ Expected $val, Actual: $outnfr]"
            }
            $stDev = $NFR.stDev
            if ($stDev -le $OutPut.stDev) {
                $Msg = @{ }
                $Msg.Add("StadardDeviationError", "Test failed reason : " + $BenchMark.Name + '.Stadard Deviation [ Expected ' + $stDev + ', Actual: ' + $OutPut.stDev )
                $Failure = $True
                $Msgs += $Msg
            }
            else {
                $bName = $BenchMark.Name
                $val = $NFR.stDev
                $outnfr = $OutPut.stDev
                $nText = "Stadard Deviation"
                Write-Host "Performance test result for : $bName.$nText [ Expected $val, Actual: $outnfr]"
            }
        }
    }
}

if ($Failure -eq $True) {
    Write-Error -Message ($Msgs | ConvertTo-Json)  -ErrorAction Stop
}
else {
    Write-Host "All the results are good"
}
