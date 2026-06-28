$env:JAVA_HOME = 'C:\Program Files\Java\jdk1.8.0_201'
$env:Path = 'C:\Program Files\Java\jdk1.8.0_201\bin;C:\tools\apache-maven-3.9.8\bin;' + $env:Path
Set-Location 'E:\ideaProject\smart-km-report-gen\km-backend'
mvn spring-boot:run 2>&1 | Out-File 'E:\ideaProject\smart-km-report-gen\km-backend\target\app.log' -Encoding UTF8
